# tools/edit-helpers.ps1
# 使い方(プロジェクトのルートで):
#   iex (Get-Content tools\edit-helpers.ps1 -Raw -Encoding UTF8)

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# ファイルを丸ごと書き出す(なければ作る)。改行はCRLFにそろえる。
function Write-SourceFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Content
    )
    $full = Join-Path (Get-Location).Path $Path
    $dir = Split-Path $full -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $text = $Content -replace "`r?`n", "`r`n"
    [System.IO.File]::WriteAllText($full, $text, $Utf8NoBom)
    Write-Host "書き込みました: $Path"
}

# 文字列を1か所だけ置換する。1か所でなければ、何も変更しない。
function Edit-SourceFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Old,
        [Parameter(Mandatory)][string]$New
    )
    $full = Join-Path (Get-Location).Path $Path
    if (-not (Test-Path $full)) { Write-Error "ファイルがありません: $Path"; return }

    $text = [System.IO.File]::ReadAllText($full)
    $eol = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $oldN = $Old -replace "`r?`n", $eol
    $newN = $New -replace "`r?`n", $eol

    $count = ([regex]::Matches($text, [regex]::Escape($oldN))).Count
    if ($count -ne 1) {
        Write-Error "置換前の文字列が $count 箇所ありました(1箇所である必要があります)。変更しません: $Path"
        return
    }

    [System.IO.File]::WriteAllText($full, $text.Replace($oldN, $newN), $Utf8NoBom)
    Write-Host "置換しました: $Path"
}