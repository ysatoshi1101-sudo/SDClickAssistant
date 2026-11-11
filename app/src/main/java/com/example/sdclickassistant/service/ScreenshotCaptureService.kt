package com.example.sdclickassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenshotCaptureService"

        private const val CHANNEL_ID =
            "screenshot_capture_channel"

        private const val NOTIFICATION_ID = 1001

        private const val VIRTUAL_DISPLAY_NAME =
            "SDClickAssistantCapture"

        private const val READY_TIMEOUT_MS = 5000L

        /**
         * キャプチャ時に、新しいフレームが届くのを待つ時間(ms)。
         *
         * 画面が動いている時は、新しいフレームがすぐ届くので、これを待たずに済む。
         * 画面が止まっている時は、新しいフレームが届かないため、
         * この時間だけ待ってから、保持している直近のフレームを使う。
         */
        private const val FRESH_FRAME_WAIT_MS = 300L

        /**
         * フレームが1枚も届いていない場合(起動直後など)に、
         * 最初のフレームが届くのを待つ上限時間(ms)。
         */
        private const val FIRST_FRAME_WAIT_MS = 3000L

        /**
         * ImageReaderが同時に保持できるフレームの枚数。
         *
         * 直近のフレームを1枚保持したまま、次のフレームを受け取るため、
         * 余裕をもって3枚にしている。
         */
        private const val IMAGE_READER_MAX_IMAGES = 3

        var instance: ScreenshotCaptureService? = null
            private set

        /**
         * ScreenshotCaptureServiceが起動して、
         * キャプチャセッションの準備が完了するまで待つ。
         *
         * @return 準備完了したService。失敗時はnull。
         */
        suspend fun awaitReady(
            timeoutMillis: Long = READY_TIMEOUT_MS
        ): ScreenshotCaptureService? {

            var result: ScreenshotCaptureService? = null
            var finished = false

            withTimeoutOrNull(timeoutMillis) {

                while (!finished) {

                    val service = instance

                    if (service != null) {

                        if (service.captureReady.await()) {
                            result = service
                        }

                        finished = true
                    } else {
                        kotlinx.coroutines.delay(50L)
                    }
                }
            }

            return result
        }
    }

    // ------------------------------------------------------------
    // MediaProjection
    // ------------------------------------------------------------

    private var mediaProjection: MediaProjection? = null

    /**
     * キャプチャ用ImageReader。
     *
     * シナリオ実行中だけでなく、
     * Serviceが待機している間も維持する。
     */
    private var imageReader: ImageReader? = null

    /**
     * MediaProjectionに接続するVirtualDisplay。
     *
     * 1つのMediaProjectionにつき1つだけ作成する。
     */
    private var virtualDisplay: VirtualDisplay? = null

    /**
     * キャプチャセッションの準備状態。
     *
     * true  = 使用可能
     * false = 初期化失敗
     */
    private val captureReady =
        CompletableDeferred<Boolean>()

    /**
     * 直近に届いた、画面のフレーム。
     *
     * VirtualDisplayは、画面の内容が変化した時にだけ、新しいフレームを作る。
     * そのため、画面が止まっていると、新しいフレームは届かない。
     * 「次に届くフレーム」を待つ方式だと、止まっている画面では待たされ続けてしまう。
     * これを避けるため、直近のフレームを保持しておき、
     * キャプチャの時は、これを使えるようにしている。
     * (画面が変わっていなければ、直近のフレームは、今の画面と同じ。)
     *
     * 読み書きは、メインスレッドで行う。
     * (フレームの受け取りも、キャプチャ画像の読み出しも、メインスレッドで行うため。)
     */
    private var latestImage: Image? = null

    /**
     * フレームが届くたびに増える番号。
     * 「新しいフレームが届くのを待つ」時に、この番号の変化を見る。
     */
    private val frameCounter = MutableStateFlow(0L)

    /**
     * MediaProjectionが停止されたときに呼ばれる。
     */
    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {

                Log.d(
                    TAG,
                    "MediaProjection stopped"
                )

                releaseCaptureResources(
                    stopProjection = false
                )

                if (!captureReady.isCompleted) {
                    captureReady.complete(false)
                }
            }
        }

    // ------------------------------------------------------------
    // Service lifecycle
    // ------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        instance = this

        Log.d(
            TAG,
            "Service created"
        )

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(
            TAG,
            "Service starting"
        )

        /*
         * Foreground Serviceを先に開始する。
         *
         * Android 14以降のMediaProjectionでは、
         * MediaProjection取得前にMediaProjectionタイプの
         * Foreground Serviceとして開始しておく必要がある。
         */
        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )

            } else {

                @Suppress("DEPRECATION")
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification()
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start foreground service",
                e
            )

            if (!captureReady.isCompleted) {
                captureReady.complete(false)
            }

            stopSelf()

            return START_NOT_STICKY
        }

        /*
         * すでにMediaProjectionが存在する場合、
         * 再取得しない。
         */
        if (mediaProjection != null) {

            Log.d(
                TAG,
                "MediaProjection already initialized"
            )

            return START_NOT_STICKY
        }

        initializeMediaProjection()

        /*
         * MediaProjectionは自動復活させない。
         *
         * ServiceがAndroidによって終了された場合、
         * 古いresultDataを使ってMediaProjectionを
         * 再取得しないため。
         */
        return START_NOT_STICKY
    }

    /**
     * MediaProjection、ImageReader、VirtualDisplayを
     * 一度だけ初期化する。
     */
    private fun initializeMediaProjection() {

        /*
         * Tokenは必ずconsume()する。
         *
         * これにより同じresultDataを再利用しない。
         */
        val token =
            MediaProjectionTokenHolder.consume()

        if (token == null) {

            Log.e(
                TAG,
                "No MediaProjection token available"
            )

            if (!captureReady.isCompleted) {
                captureReady.complete(false)
            }

            stopSelf()

            return
        }

        val resultCode = token.first
        val data = token.second

        try {

            val manager =
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            /*
             * resultCode + dataからMediaProjectionを
             * 一度だけ取得する。
             */
            val projection =
                manager.getMediaProjection(
                    resultCode,
                    data
                )

            /*
             * Android 14以降ではVirtualDisplay作成前に
             * Callback登録が必要。
             */
            projection.registerCallback(
                projectionCallback,
                Handler(mainLooper)
            )

            mediaProjection = projection

            /*
             * ディスプレイサイズ取得。
             */
            val metrics =
                DisplayMetrics()

            val displayManager =
                getSystemService(
                    DisplayManager::class.java
                )

            val display =
                displayManager.getDisplay(
                    android.view.Display.DEFAULT_DISPLAY
                )

            if (display == null) {

                Log.e(
                    TAG,
                    "Default display is not available"
                )

                releaseCaptureResources(
                    stopProjection = true
                )

                if (!captureReady.isCompleted) {
                    captureReady.complete(false)
                }

                stopSelf()

                return
            }

            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)

            val width =
                metrics.widthPixels

            val height =
                metrics.heightPixels

            val density =
                metrics.densityDpi

            Log.d(
                TAG,
                "Capture size: ${width}x${height}, density=$density"
            )

            /*
             * ImageReaderを1回だけ作成する。
             *
             * 直近のフレームを1枚保持したまま、次のフレームを受け取るため、
             * 保持できる枚数に余裕を持たせている。
             */
            val reader =
                ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    IMAGE_READER_MAX_IMAGES
                )

            imageReader = reader

            /*
             * ImageReaderから画像が届いたら、
             * 直近のフレームとして保持する。
             */
            reader.setOnImageAvailableListener(
                { availableReader ->

                    handleImageAvailable(
                        availableReader
                    )

                },
                Handler(mainLooper)
            )

            /*
             * VirtualDisplayを1回だけ作成する。
             *
             * 以後、スクリーンショットのたびに
             * createVirtualDisplay()を呼ばない。
             */
            virtualDisplay =
                projection.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    Handler(mainLooper)
                )

            Log.d(
                TAG,
                "VirtualDisplay created successfully"
            )

            if (!captureReady.isCompleted) {
                captureReady.complete(true)
            }

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "SecurityException while initializing MediaProjection",
                e
            )

            releaseCaptureResources(
                stopProjection = true
            )

            if (!captureReady.isCompleted) {
                captureReady.complete(false)
            }

            stopSelf()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to initialize MediaProjection",
                e
            )

            releaseCaptureResources(
                stopProjection = true
            )

            if (!captureReady.isCompleted) {
                captureReady.complete(false)
            }

            stopSelf()
        }
    }

    // ------------------------------------------------------------
    // ImageReader
    // ------------------------------------------------------------

    /**
     * ImageReaderに新しい画像が届いたときの処理。
     *
     * 届いた最新のフレームを、直近のフレームとして保持する。
     * それまで保持していた古いフレームは、解放する。
     * (メインスレッドで呼ばれる。)
     */
    private fun handleImageAvailable(
        reader: ImageReader
    ) {

        // 解放済み(または別の)ImageReaderからの通知は、無視する。
        if (reader !== imageReader) {
            return
        }

        val image =
            try {

                reader.acquireLatestImage()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to acquire latest image",
                    e
                )

                null
            }

        if (image == null) {
            return
        }

        // 古いフレームを解放して、新しいフレームを保持する。
        latestImage?.let { previous ->

            try {
                previous.close()
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to close previous image",
                    e
                )
            }
        }

        latestImage = image

        // 「新しいフレームが届くのを待っている」処理へ知らせる。
        frameCounter.value = frameCounter.value + 1
    }

    // ------------------------------------------------------------
    // Screenshot
    // ------------------------------------------------------------

    /**
     * 現在の画面をキャプチャしてBitmapとして返す(保存はしない)。
     *
     * SCREENSHOTステップでのファイル保存だけでなく、
     * CONDITIONステップの色判定・画像認識など、
     * 画面の状態を読み取りたい場面全般で使う。
     *
     * 動作:
     *  1. 新しいフレームが届くのを、最大 FRESH_FRAME_WAIT_MS だけ待つ。
     *     (画面が動いている時は、すぐ届くので、ほとんど待たない。)
     *  2. 届かなければ(=画面が止まっている)、保持している直近のフレームを使う。
     *     画面が変わっていなければ、直近のフレームは今の画面と同じ。
     *
     * 呼び出し側は使用後に必ずbitmap.recycle()すること。
     */
    suspend fun captureBitmap(): Bitmap? {

        if (!captureReady.isCompleted ||
            !captureReady.await()
        ) {

            Log.e(
                TAG,
                "Capture session is not ready"
            )

            return null
        }

        if (mediaProjection == null ||
            imageReader == null ||
            virtualDisplay == null
        ) {

            Log.e(
                TAG,
                "Capture resources are not available"
            )

            return null
        }

        // 新しいフレームを、少しだけ待つ。
        val startCount = frameCounter.value

        val gotFreshFrame =
            withTimeoutOrNull(FRESH_FRAME_WAIT_MS) {
                frameCounter.first { it > startCount }
            } != null

        if (!gotFreshFrame) {

            Log.d(
                TAG,
                "No new frame within ${FRESH_FRAME_WAIT_MS}ms: using the latest frame"
            )
        }

        // フレームが1枚も届いていない場合(起動直後など)は、最初の1枚を待つ。
        if (latestImage == null) {

            Log.d(
                TAG,
                "Waiting for the first frame"
            )

            val gotFirstFrame =
                withTimeoutOrNull(FIRST_FRAME_WAIT_MS) {
                    frameCounter.first { it > startCount }
                } != null

            if (!gotFirstFrame) {

                Log.e(
                    TAG,
                    "No frame has arrived"
                )

                return null
            }
        }

        // フレームの受け取りと同じメインスレッドで、保持しているフレームを読み出す。
        // (読み出している最中に、フレームが解放されるのを防ぐ。)
        return withContext(Dispatchers.Main.immediate) {

            val image = latestImage

            if (image == null) {

                Log.e(
                    TAG,
                    "Latest image is not available"
                )

                null

            } else {

                try {

                    // 保持しているフレームは、次のキャプチャでも使うため、closeしない。
                    imageToBitmap(image)

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed to convert Image to Bitmap",
                        e
                    )

                    null
                }
            }
        }
    }

    /**
     * 現在の画面をキャプチャしてPNGとして保存する。
     *
     * VirtualDisplayは作成済みのものを使い回す。
     */
    suspend fun captureAndSave(
        scenarioName: String,
        stepNumber: Int
    ): Boolean {

        val bitmap =
            captureBitmap()
                ?: return false

        return try {

            saveBitmap(
                bitmap = bitmap,
                scenarioName = scenarioName,
                stepNumber = stepNumber
            )

        } finally {

            bitmap.recycle()
        }
    }

    /**
     * ImageReaderのImageをBitmapへ変換する。
     */
    private fun imageToBitmap(
        image: Image
    ): Bitmap {

        val plane =
            image.planes[0]

        val buffer =
            plane.buffer

        val pixelStride =
            plane.pixelStride

        val rowStride =
            plane.rowStride

        val width =
            image.width

        val height =
            image.height

        val rowPadding =
            rowStride -
                    pixelStride * width

        val bitmapWidth =
            width +
                    rowPadding / pixelStride

        val rawBitmap =
            Bitmap.createBitmap(
                bitmapWidth,
                height,
                Bitmap.Config.ARGB_8888
            )

        buffer.rewind()

        rawBitmap.copyPixelsFromBuffer(
            buffer
        )

        return if (bitmapWidth != width) {

            Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                width,
                height
            ).also {
                rawBitmap.recycle()
            }

        } else {

            rawBitmap
        }
    }

    // ------------------------------------------------------------
    // Resource management
    // ------------------------------------------------------------

    /**
     * キャプチャリソースを解放する。
     *
     * @param stopProjection
     * true ならMediaProjectionも停止する。
     * falseならMediaProjection自身の停止通知時などに使用。
     */
    private fun releaseCaptureResources(
        stopProjection: Boolean
    ) {

        /*
         * 保持している直近のフレームを解放する。
         * (ImageReaderを閉じる前に行う。)
         */
        latestImage?.let { image ->

            try {

                image.close()

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to close latest image",
                    e
                )
            }
        }

        latestImage = null

        /*
         * VirtualDisplayを解放。
         */
        virtualDisplay?.let { display ->

            try {

                display.release()

                Log.d(
                    TAG,
                    "VirtualDisplay released"
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to release VirtualDisplay",
                    e
                )
            }
        }

        virtualDisplay = null

        /*
         * ImageReaderを閉じる。
         */
        imageReader?.let { reader ->

            try {

                reader.close()

                Log.d(
                    TAG,
                    "ImageReader closed"
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to close ImageReader",
                    e
                )
            }
        }

        imageReader = null

        /*
         * MediaProjectionを停止する。
         */
        mediaProjection?.let { projection ->

            try {

                projection.unregisterCallback(
                    projectionCallback
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to unregister MediaProjection callback",
                    e
                )
            }

            if (stopProjection) {

                try {

                    projection.stop()

                    Log.d(
                        TAG,
                        "MediaProjection stopped"
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "Failed to stop MediaProjection",
                        e
                    )
                }
            }
        }

        mediaProjection = null
    }

    /**
     * シナリオ実行終了時に呼ぶこともできるが、
     * 現在の設計ではServiceを待機状態にするため、
     * 通常のシナリオ完了では呼ばない。
     *
     * STOPやService終了時も同じMediaProjectionを
     * 次回実行に利用できるように維持する。
     */
    fun stopCaptureSession() {

        Log.d(
            TAG,
            "Stopping capture session"
        )

        releaseCaptureResources(
            stopProjection = true
        )

        stopSelf()
    }

    // ------------------------------------------------------------
    // Service destroy
    // ------------------------------------------------------------

    override fun onDestroy() {

        Log.d(
            TAG,
            "Service destroying"
        )

        releaseCaptureResources(
            stopProjection = true
        )

        if (!captureReady.isCompleted) {
            captureReady.complete(false)
        }

        instance = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    // ------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "スクリーンショット実行中",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "SD Click Assistantのスクリーンショット機能"

                setShowBadge(false)
            }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    private fun buildNotification(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "SD Click Assistant"
            )
            .setContentText(
                "スクリーンショット機能が待機中です"
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_camera
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    // ------------------------------------------------------------
    // Save
    // ------------------------------------------------------------

    /**
     * BitmapをMediaStoreへPNGとして保存する。
     */
    private fun saveBitmap(
        bitmap: Bitmap,
        scenarioName: String,
        stepNumber: Int
    ): Boolean {

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.JAPAN
            ).format(Date())

        /*
         * ファイル名に使用できない文字を除去。
         */
        val safeScenarioName =
            scenarioName
                .replace(
                    Regex("[\\\\/:*?\"<>|]"),
                    "_"
                )
                .ifBlank {
                    "Scenario"
                }

        val fileName =
            "${timestamp}_${safeScenarioName}_${stepNumber}.png"

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/png"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/SD Click Assistant/$safeScenarioName"
                    )

                    put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                    )
                }
            }

        val resolver =
            contentResolver

        val uri =
            try {

                resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to create MediaStore entry",
                    e
                )

                null
            }

        if (uri == null) {

            Log.e(
                TAG,
                "MediaStore.insert() returned null"
            )

            return false
        }

        return try {

            val success =
                resolver
                    .openOutputStream(uri)
                    ?.use { outputStream ->

                        bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            outputStream
                        )

                    }
                    ?: false

            if (!success) {

                Log.e(
                    TAG,
                    "Bitmap.compress() failed"
                )

                resolver.delete(
                    uri,
                    null,
                    null
                )

                false

            } else {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    val completedValues =
                        ContentValues().apply {

                            put(
                                MediaStore.Images.Media.IS_PENDING,
                                0
                            )
                        }

                    resolver.update(
                        uri,
                        completedValues,
                        null,
                        null
                    )
                }

                Log.d(
                    TAG,
                    "Screenshot saved: $uri"
                )

                true
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to save screenshot",
                e
            )

            try {

                resolver.delete(
                    uri,
                    null,
                    null
                )

            } catch (deleteException: Exception) {

                Log.w(
                    TAG,
                    "Failed to delete incomplete screenshot",
                    deleteException
                )
            }

            false
        }
    }
}