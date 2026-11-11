package com.example.sdclickassistant.presentation.scenariolist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sdclickassistant.domain.model.Folder
import com.example.sdclickassistant.domain.model.MacroScenario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioListScreen(
    onScenarioClick: (MacroScenario) -> Unit = {},
    viewModel: ScenarioListViewModel = hiltViewModel()
) {
    val scenarios by viewModel.scenarios.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    var showCreateScenarioDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var scenarioToDelete by remember { mutableStateOf<MacroScenario?>(null) }
    var scenarioToRename by remember { mutableStateOf<MacroScenario?>(null) }
    var scenarioToMove by remember { mutableStateOf<MacroScenario?>(null) }
    var folderToManage by remember { mutableStateOf<Folder?>(null) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("シナリオ一覧") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateScenarioDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新規シナリオ作成")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            FolderFilterRow(
                folders = folders,
                selectedFilter = selectedFilter,
                onSelect = viewModel::selectFilter,
                onLongPressFolder = { folderToManage = it },
                onAddFolderClick = { showCreateFolderDialog = true }
            )

            if (scenarios.isEmpty()) {
                EmptyScenarioList(modifier = Modifier.weight(1f))
            } else {
                ScenarioList(
                    scenarios = scenarios,
                    // フォルダで絞り込んでいる間は並び替えを無効化する
                    // (orderがシナリオ全体で1本の通し番号のため)
                    reorderEnabled = selectedFilter is FolderFilter.All,
                    onReorder = viewModel::reorderScenarios,
                    onScenarioClick = onScenarioClick,
                    onDeleteClick = { scenarioToDelete = it },
                    onRenameClick = { scenarioToRename = it },
                    onCopyClick = viewModel::copyScenario,
                    onFavoriteClick = viewModel::toggleFavorite,
                    onMoveClick = { scenarioToMove = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ------------------------------------------------------------
    // シナリオ関連ダイアログ
    // ------------------------------------------------------------

    if (showCreateScenarioDialog) {
        NameInputDialog(
            title = "新規シナリオ作成",
            label = "シナリオ名",
            initialValue = "",
            confirmLabel = "作成",
            onConfirm = { name ->
                viewModel.createScenario(name)
                showCreateScenarioDialog = false
            },
            onDismiss = { showCreateScenarioDialog = false }
        )
    }

    scenarioToDelete?.let { scenario ->
        AlertDialog(
            onDismissRequest = { scenarioToDelete = null },
            title = { Text("シナリオを削除") },
            text = { Text("「${scenario.name}」を削除します。この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScenario(scenario)
                    scenarioToDelete = null
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { scenarioToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    scenarioToRename?.let { scenario ->
        NameInputDialog(
            title = "名前を変更",
            label = "シナリオ名",
            initialValue = scenario.name,
            confirmLabel = "保存",
            onConfirm = { newName ->
                viewModel.renameScenario(scenario.id, newName)
                scenarioToRename = null
            },
            onDismiss = { scenarioToRename = null }
        )
    }

    scenarioToMove?.let { scenario ->
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = scenario.folderId,
            onSelect = { folderId ->
                viewModel.moveToFolder(scenario.id, folderId)
                scenarioToMove = null
            },
            onDismiss = { scenarioToMove = null }
        )
    }

    // ------------------------------------------------------------
    // フォルダ関連ダイアログ
    // ------------------------------------------------------------

    if (showCreateFolderDialog) {
        NameInputDialog(
            title = "新規フォルダ作成",
            label = "フォルダ名",
            initialValue = "",
            confirmLabel = "作成",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    folderToManage?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToManage = null },
            title = { Text(folder.name) },
            text = { Text("このフォルダをどうしますか？") },
            confirmButton = {
                TextButton(onClick = {
                    folderToRename = folder
                    folderToManage = null
                }) {
                    Text("名前を変更")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder)
                    folderToManage = null
                }) {
                    Text("削除")
                }
            }
        )
    }

    folderToRename?.let { folder ->
        NameInputDialog(
            title = "フォルダ名を変更",
            label = "フォルダ名",
            initialValue = folder.name,
            confirmLabel = "保存",
            onConfirm = { newName ->
                viewModel.renameFolder(folder.id, newName)
                folderToRename = null
            },
            onDismiss = { folderToRename = null }
        )
    }
}

/**
 * フォルダ絞り込み用のチップ行。
 * 「すべて」「未分類」＋各フォルダ＋フォルダ追加ボタン。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderFilterRow(
    folders: List<Folder>,
    selectedFilter: FolderFilter,
    onSelect: (FolderFilter) -> Unit,
    onLongPressFolder: (Folder) -> Unit,
    onAddFolderClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChipItem(
                label = "すべて",
                selected = selectedFilter is FolderFilter.All,
                onClick = { onSelect(FolderFilter.All) }
            )
        }

        item {
            FilterChipItem(
                label = "未分類",
                selected = selectedFilter is FolderFilter.Unassigned,
                onClick = { onSelect(FolderFilter.Unassigned) }
            )
        }

        items(folders, key = { it.id }) { folder ->
            FilterChipItem(
                label = folder.name,
                selected = selectedFilter == FolderFilter.Selected(folder.id),
                onClick = { onSelect(FolderFilter.Selected(folder.id)) },
                onLongClick = { onLongPressFolder(folder) }
            )
        }

        item {
            FilterChipItem(
                label = "＋ フォルダ",
                selected = false,
                onClick = onAddFolderClick
            )
        }
    }
}

/**
 * タップで選択、長押しでフォルダ管理メニューを開くチップ。
 * Material3標準のFilterChipは長押し検知がしづらいため自前実装する。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * シナリオ名・フォルダ名など、テキスト1つを入力するだけの
 * 汎用ダイアログ。
 */
@Composable
private fun NameInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * シナリオの移動先フォルダを選ぶダイアログ。
 * 「未分類」も選択肢に含む。
 */
@Composable
private fun MoveToFolderDialog(
    folders: List<Folder>,
    currentFolderId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォルダへ移動") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentFolderId == null,
                        onClick = { onSelect(null) }
                    )
                    Text("未分類", modifier = Modifier.padding(start = 4.dp))
                }

                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFolderId == folder.id,
                            onClick = { onSelect(folder.id) }
                        )
                        Text(folder.name, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun EmptyScenarioList(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "まだシナリオがありません",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "右下の + ボタンから最初のシナリオを作成できます",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ScenarioList(
    scenarios: List<MacroScenario>,
    reorderEnabled: Boolean,
    onReorder: (List<MacroScenario>) -> Unit,
    onScenarioClick: (MacroScenario) -> Unit,
    onDeleteClick: (MacroScenario) -> Unit,
    onRenameClick: (MacroScenario) -> Unit,
    onCopyClick: (MacroScenario) -> Unit,
    onFavoriteClick: (MacroScenario) -> Unit,
    onMoveClick: (MacroScenario) -> Unit,
    modifier: Modifier = Modifier
) {
    // ドラッグ中に画面上で一時的に並べ替えるためのローカルリスト。
    // ドラッグ中でない時だけ、DBから来た最新の並び順(scenarios)に同期する。
    var items by remember { mutableStateOf(scenarios) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    LaunchedEffect(scenarios) {
        if (draggingIndex == null) {
            items = scenarios
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, scenario ->

            val isDragging = draggingIndex == index

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        // カードの高さを一度だけ計測し、
                        // ドラッグ量から「何個分移動したか」を計算するのに使う。
                        if (itemHeightPx == 0f) {
                            itemHeightPx = coordinates.size.height.toFloat()
                        }
                    }
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .alpha(if (isDragging) 0.9f else 1f)
            ) {
                ScenarioCard(
                    scenario = scenario,
                    reorderEnabled = reorderEnabled,
                    onClick = { onScenarioClick(scenario) },
                    onDeleteClick = { onDeleteClick(scenario) },
                    onRenameClick = { onRenameClick(scenario) },
                    onCopyClick = { onCopyClick(scenario) },
                    onFavoriteClick = { onFavoriteClick(scenario) },
                    onMoveClick = { onMoveClick(scenario) },
                    dragHandleModifier = Modifier.pointerInput(scenario.id, reorderEnabled) {
                        if (!reorderEnabled) return@pointerInput

                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffsetY = 0f
                                onReorder(items)
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y

                                val currentIndex = draggingIndex
                                if (currentIndex == null || itemHeightPx <= 0f) {
                                    return@detectDragGesturesAfterLongPress
                                }

                                val moveBy = (dragOffsetY / itemHeightPx).toInt()

                                if (moveBy != 0) {
                                    val targetIndex =
                                        (currentIndex + moveBy)
                                            .coerceIn(items.indices)

                                    if (targetIndex != currentIndex) {
                                        items = items.toMutableList().apply {
                                            add(targetIndex, removeAt(currentIndex))
                                        }
                                        draggingIndex = targetIndex
                                        dragOffsetY -= moveBy * itemHeightPx
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: MacroScenario,
    reorderEnabled: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onCopyClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoveClick: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (reorderEnabled) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "≡",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(text = scenario.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${scenario.steps.size} ステップ",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // お気に入り切り替え（★/☆はアイコンライブラリに依存しないテキスト表示）
            IconButton(onClick = onFavoriteClick) {
                Text(
                    text = if (scenario.isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Text(
                        text = "⋮",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("名前を変更") },
                        onClick = {
                            showMenu = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("コピー") },
                        onClick = {
                            showMenu = false
                            onCopyClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("フォルダへ移動") },
                        onClick = {
                            showMenu = false
                            onMoveClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}