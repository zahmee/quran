<#
.SYNOPSIS
ينشئ أرشيفاً قابلاً للنقل لمشروع قرآن القارئ من دون تعديل ملفات المصدر.

.DESCRIPTION
يحفظ السكربت كل الملفات المطلوبة لمتابعة التطوير، بما فيها مجلد .git
والتعديلات غير المرسلة وملفات التوقيع الموجودة داخل المشروع.
يستبعد فقط نواتج البناء والكاش وملفات الجهاز المحلي والنسخ القديمة غير اللازمة
لنسخة العمل الافتراضية. لا يحذف أو ينقل أي ملف من المشروع الأصلي.
#>
[CmdletBinding()]
param(
    [string]$SourcePath,

    [string]$Destination,

    [ValidateSet('7z', 'zip')]
    [string]$Format = '7z',

    [ValidateRange(0, 9)]
    [int]$CompressionLevel = 7,

    [switch]$Preview,

    [switch]$IncludeLegacy,

    [switch]$IncludeReleaseArtifacts,

    [switch]$ExcludeSecrets,

    [switch]$SkipChecksum
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-AbsoluteDirectoryPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $item = Get-Item -LiteralPath $Path -Force
    if (-not $item.PSIsContainer) {
        throw "المسار المحدد ليس مجلداً: $Path"
    }

    $fullPath = $item.FullName
    if ($fullPath.Length -gt 3) {
        $fullPath = $fullPath.TrimEnd([char]'\', [char]'/' )
    }
    return $fullPath
}

function Test-PathIsInside {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CandidatePath,

        [Parameter(Mandatory = $true)]
        [string]$ParentPath
    )

    if ($CandidatePath.Equals($ParentPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }

    $parentWithSeparator = $ParentPath.TrimEnd([char]'\', [char]'/' ) + [System.IO.Path]::DirectorySeparatorChar
    return $CandidatePath.StartsWith($parentWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)
}

function Get-SevenZipPath {
    $commandNames = @('7z.exe', '7zz.exe', '7za.exe')
    foreach ($commandName in $commandNames) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($null -ne $command -and -not [string]::IsNullOrWhiteSpace($command.Source)) {
            return $command.Source
        }
    }

    $knownPaths = @(
        'C:\Program Files\7-Zip\7z.exe',
        'C:\Program Files\NanaZip\7z.exe',
        'C:\Program Files\NanaZip\7zz.exe',
        'C:\Program Files\NanaZip Preview\7z.exe',
        'C:\Program Files\NanaZip Preview\7zz.exe'
    )

    foreach ($knownPath in $knownPaths) {
        if (Test-Path -LiteralPath $knownPath -PathType Leaf) {
            return $knownPath
        }
    }

    throw 'لم يتم العثور على NanaZip أو 7-Zip. ثبّت أحدهما ثم شغّل السكربت مرة أخرى.'
}

function Get-ExclusionReason {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $normalizedPath = $RelativePath.Replace('/', '\')

    # بيانات Git مطلوبة لنقل كامل التاريخ والفروع وحالة العمل، ولا تطبق عليها الاستثناءات العامة.
    if ($normalizedPath -match '^\.git(\\|$)') {
        return $null
    }

    if (-not $IncludeLegacy -and $normalizedPath -match '^old(\\|$)') {
        return 'المجلد القديم old'
    }

    if ($normalizedPath -match '(^|\\)\.codex(\\|$)') {
        return 'ملفات العمل المؤقتة لـ Codex'
    }

    if ($normalizedPath -match '(^|\\)(\.gradle|\.kotlin|\.idea|\.vscode|\.cxx|build|out|dist|node_modules|__pycache__|captures|tmp|temp)(\\|$)') {
        return 'نواتج البناء أو الكاش أو مجلدات الأدوات المحلية'
    }

    $fileName = [System.IO.Path]::GetFileName($normalizedPath)
    if ($fileName -in @('local.properties', '.DS_Store', 'Thumbs.db', 'Desktop.ini')) {
        return 'إعداد أو ملف خاص بالجهاز المحلي'
    }

    if ($fileName -match '\.hprof$') {
        return 'تفريغ ذاكرة قابل لإعادة الإنشاء'
    }

    $extension = [System.IO.Path]::GetExtension($fileName).ToLowerInvariant()
    if (-not $IncludeReleaseArtifacts -and $extension -in @('.apk', '.aab')) {
        return 'ملف إصدار قابل لإعادة البناء'
    }

    if ($ExcludeSecrets) {
        if ($fileName -eq '.env' -or $fileName -like '.env.*') {
            return 'ملف سرّي استُبعد بطلب المستخدم'
        }

        if ($fileName -in @('keystore.properties', 'google-services.json', 'credentials.json')) {
            return 'ملف سرّي استُبعد بطلب المستخدم'
        }

        if ($extension -in @('.jks', '.keystore', '.p12', '.pfx', '.pem', '.key')) {
            return 'مفتاح أو شهادة استُبعدت بطلب المستخدم'
        }
    }

    return $null
}

function Get-GitInfo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryPath
    )

    $result = [ordered]@{
        included = $true
        repository_detected = $false
        head = $null
        branch = $null
        worktree_changes = $null
    }

    $gitCommand = Get-Command git.exe -ErrorAction SilentlyContinue
    if ($null -eq $gitCommand) {
        $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    }

    if ($null -eq $gitCommand) {
        return [pscustomobject]$result
    }

    $probe = @(& $gitCommand.Source -C $RepositoryPath rev-parse --is-inside-work-tree 2>$null)
    if ($LASTEXITCODE -ne 0 -or $probe.Count -eq 0 -or $probe[0].Trim() -ne 'true') {
        return [pscustomobject]$result
    }

    $result.repository_detected = $true

    $head = @(& $gitCommand.Source -C $RepositoryPath rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -eq 0 -and $head.Count -gt 0) {
        $result.head = $head[0].Trim()
    }

    $branch = @(& $gitCommand.Source -C $RepositoryPath branch --show-current 2>$null)
    if ($LASTEXITCODE -eq 0 -and $branch.Count -gt 0) {
        $result.branch = $branch[0].Trim()
    }

    $status = @(& $gitCommand.Source -C $RepositoryPath status --porcelain 2>$null)
    if ($LASTEXITCODE -eq 0) {
        $result.worktree_changes = $status.Count
    }

    return [pscustomobject]$result
}

if ([string]::IsNullOrWhiteSpace($SourcePath)) {
    $SourcePath = $PSScriptRoot
}

$sourcePath = Get-AbsoluteDirectoryPath -Path $SourcePath
$sourceParent = Split-Path -Path $sourcePath -Parent
$projectName = Split-Path -Path $sourcePath -Leaf

if ([string]::IsNullOrWhiteSpace($Destination)) {
    $destinationPath = Join-Path -Path $sourceParent -ChildPath '_backups'
}
else {
    $destinationPath = [System.IO.Path]::GetFullPath($Destination)
}

if ($destinationPath.Length -gt 3) {
    $destinationPath = $destinationPath.TrimEnd([char]'\', [char]'/' )
}

if (Test-PathIsInside -CandidatePath $destinationPath -ParentPath $sourcePath) {
    throw 'يجب أن يكون مجلد النسخ خارج المشروع حتى لا يدخل الأرشيف في نفسه.'
}

$archiveStamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$archiveStem = '{0}_{1}' -f $projectName, $archiveStamp
$finalArchivePath = Join-Path -Path $destinationPath -ChildPath "$archiveStem.$Format"
$partialArchivePath = "$finalArchivePath.partial"
$fileListPath = Join-Path -Path $destinationPath -ChildPath ".$archiveStem.files.txt"
$hashPath = "$finalArchivePath.sha256"
$manifestPath = "$finalArchivePath.manifest.json"

$includedFiles = New-Object 'System.Collections.Generic.List[System.IO.FileInfo]'
$archiveInputPaths = New-Object 'System.Collections.Generic.List[string]'
$excludedByReason = @{}
$includedBytes = [int64]0
$excludedBytes = [int64]0

Get-ChildItem -LiteralPath $sourcePath -Force -File -Recurse | ForEach-Object {
    $relativePath = $_.FullName.Substring($sourcePath.Length).TrimStart([char]'\', [char]'/' )
    $reason = Get-ExclusionReason -RelativePath $relativePath

    if ($null -eq $reason) {
        $includedFiles.Add($_)
        $archiveInputPaths.Add("$projectName\$relativePath")
        $includedBytes += $_.Length
        return
    }

    $excludedBytes += $_.Length
    if (-not $excludedByReason.ContainsKey($reason)) {
        $excludedByReason[$reason] = [ordered]@{
            files = 0
            bytes = [int64]0
        }
    }
    $excludedByReason[$reason].files++
    $excludedByReason[$reason].bytes += $_.Length
}

if ($includedFiles.Count -eq 0) {
    throw 'لم توجد ملفات لإضافتها إلى الأرشيف.'
}

$gitInfo = Get-GitInfo -RepositoryPath $sourcePath

$requiredRelativePaths = New-Object 'System.Collections.Generic.List[string]'
$requiredCandidates = @(
    '.git\HEAD',
    'mushaf_app\gradlew.bat',
    'mushaf_app\settings.gradle.kts',
    'mushaf_app\app\src\main\AndroidManifest.xml'
)

if (-not $ExcludeSecrets) {
    $requiredCandidates += @(
        'mushaf_app\release.jks',
        'mushaf_app\keystore.properties'
    )
}

foreach ($relativeCandidate in $requiredCandidates) {
    $candidatePath = Join-Path -Path $sourcePath -ChildPath $relativeCandidate
    if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
        $reason = Get-ExclusionReason -RelativePath $relativeCandidate
        if ($null -eq $reason) {
            $requiredRelativePaths.Add($relativeCandidate)
        }
    }
}

Write-Host ''
Write-Host 'ملخص النسخة المقترحة'
Write-Host ("المشروع: {0}" -f $sourcePath)
Write-Host ("وجهة الأرشيف: {0}" -f $finalArchivePath)
Write-Host ("الملفات المضمنة: {0:N0} ملفاً، بحجم {1:N2} ميجابايت قبل الضغط" -f $includedFiles.Count, ($includedBytes / 1MB))
Write-Host ("الملفات المستبعدة: {0:N2} ميجابايت" -f ($excludedBytes / 1MB))
Write-Host ("بيانات Git مضمنة: نعم")
Write-Host ("تغييرات Git غير المرسلة المكتشفة: {0}" -f $(if ($null -eq $gitInfo.worktree_changes) { 'غير متاح' } else { $gitInfo.worktree_changes }))
Write-Host ("ملفات التوقيع والإعدادات السرية مضمنة: {0}" -f $(if ($ExcludeSecrets) { 'لا' } else { 'نعم' }))

if ($excludedByReason.Count -gt 0) {
    Write-Host ''
    Write-Host 'تفصيل الاستثناءات'
    foreach ($reason in ($excludedByReason.Keys | Sort-Object)) {
        $entry = $excludedByReason[$reason]
        Write-Host ("- {0}: {1:N0} ملفاً، {2:N2} ميجابايت" -f $reason, $entry.files, ($entry.bytes / 1MB))
    }
}

if ($Preview) {
    Write-Host ''
    Write-Host 'انتهت المعاينة. لم يُنشأ أي أرشيف ولم تُعدَّل ملفات المشروع.'
    return
}

if (Test-Path -LiteralPath $finalArchivePath) {
    throw "الأرشيف موجود بالفعل: $finalArchivePath"
}

if (Test-Path -LiteralPath $partialArchivePath) {
    throw "توجد نسخة مؤقتة سابقة تحتاج إلى مراجعة: $partialArchivePath"
}

[System.IO.Directory]::CreateDirectory($destinationPath) | Out-Null

$sevenZipPath = Get-SevenZipPath
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($fileListPath, [string[]]$archiveInputPaths.ToArray(), $utf8WithoutBom)

$locationWasPushed = $false
try {
    Push-Location -LiteralPath $sourceParent
    $locationWasPushed = $true

    Write-Host ''
    Write-Host 'يجري إنشاء الأرشيف...'
    $createArguments = @(
        'a',
        "-t$Format",
        "-mx=$CompressionLevel",
        '-mmt=on',
        '-sccUTF-8',
        '-scsUTF-8',
        $partialArchivePath,
        "@$fileListPath"
    )

    & $sevenZipPath @createArguments
    if ($LASTEXITCODE -ne 0) {
        throw "فشل إنشاء الأرشيف. رمز الخروج: $LASTEXITCODE"
    }

    Write-Host 'يجري اختبار سلامة الأرشيف...'
    $testArguments = @('t', "-t$Format", '-sccUTF-8', $partialArchivePath)
    & $sevenZipPath @testArguments
    if ($LASTEXITCODE -ne 0) {
        throw "فشل اختبار الأرشيف. رمز الخروج: $LASTEXITCODE"
    }

    Write-Host 'يجري التحقق من الملفات المضمنة والاستثناءات...'
    $listingArguments = @('l', '-slt', "-t$Format", '-sccUTF-8', $partialArchivePath)
    $listing = @(& $sevenZipPath @listingArguments)
    if ($LASTEXITCODE -ne 0) {
        throw "تعذر قراءة محتويات الأرشيف. رمز الخروج: $LASTEXITCODE"
    }

    $archivePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($line in $listing) {
        if ($line.StartsWith('Path = ', [System.StringComparison]::Ordinal)) {
            $listedPath = $line.Substring(7).Trim().Replace('/', '\')
            if ($listedPath.StartsWith("$projectName\", [System.StringComparison]::OrdinalIgnoreCase)) {
                [void]$archivePaths.Add($listedPath)
            }
        }
    }

    $missingPaths = New-Object 'System.Collections.Generic.List[string]'
    foreach ($inputPath in $archiveInputPaths) {
        if (-not $archivePaths.Contains($inputPath)) {
            $missingPaths.Add($inputPath)
        }
    }

    if ($missingPaths.Count -gt 0) {
        $examples = ($missingPaths | Select-Object -First 5) -join ', '
        throw "الأرشيف لا يحتوي كل ملفات العمل المتوقعة. أمثلة: $examples"
    }

    $unexpectedExcludedPaths = New-Object 'System.Collections.Generic.List[string]'
    foreach ($archivePath in $archivePaths) {
        $relativePath = $archivePath.Substring($projectName.Length + 1)
        if ($null -ne (Get-ExclusionReason -RelativePath $relativePath)) {
            $unexpectedExcludedPaths.Add($archivePath)
        }
    }

    if ($unexpectedExcludedPaths.Count -gt 0) {
        $examples = ($unexpectedExcludedPaths | Select-Object -First 5) -join ', '
        throw "وجدت ملفات كان يجب استبعادها. أمثلة: $examples"
    }

    foreach ($requiredRelativePath in $requiredRelativePaths) {
        $requiredArchivePath = "$projectName\$requiredRelativePath"
        if (-not $archivePaths.Contains($requiredArchivePath)) {
            throw "تعذر التحقق من ملف مطلوب داخل الأرشيف: $requiredRelativePath"
        }
    }

    Move-Item -LiteralPath $partialArchivePath -Destination $finalArchivePath -ErrorAction Stop
}
finally {
    if ($locationWasPushed) {
        Pop-Location
    }

    if (Test-Path -LiteralPath $fileListPath -PathType Leaf) {
        Remove-Item -LiteralPath $fileListPath -Force
    }
}

$archiveItem = Get-Item -LiteralPath $finalArchivePath -ErrorAction Stop
$archiveHash = $null
if (-not $SkipChecksum) {
    $archiveHash = (Get-FileHash -LiteralPath $finalArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    "{0} *{1}" -f $archiveHash, $archiveItem.Name | Set-Content -LiteralPath $hashPath -Encoding utf8
}

$exclusionSummary = [ordered]@{}
foreach ($reason in ($excludedByReason.Keys | Sort-Object)) {
    $entry = $excludedByReason[$reason]
    $exclusionSummary[$reason] = [ordered]@{
        files = $entry.files
        bytes = $entry.bytes
    }
}

$manifest = [ordered]@{
    format_version = 1
    created_at = (Get-Date).ToString('o')
    archive = $archiveItem.Name
    archive_bytes = $archiveItem.Length
    sha256 = $archiveHash
    source_project_folder = $projectName
    included_files = $includedFiles.Count
    included_bytes = $includedBytes
    excluded_files_bytes = $excludedBytes
    excluded = $exclusionSummary
    git = $gitInfo
    secrets_included = (-not $ExcludeSecrets)
    release_artifacts_included = [bool]$IncludeReleaseArtifacts
    legacy_folder_included = [bool]$IncludeLegacy
}

$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8

Write-Host ''
Write-Host 'اكتملت النسخة المضغوطة بنجاح.'
Write-Host ("الأرشيف: {0}" -f $finalArchivePath)
Write-Host ("الحجم: {0:N2} ميجابايت" -f ($archiveItem.Length / 1MB))
if ($null -ne $archiveHash) {
    Write-Host ("بصمة SHA-256: {0}" -f $archiveHash)
    Write-Host ("ملف البصمة: {0}" -f $hashPath)
}
Write-Host ("ملف التقرير: {0}" -f $manifestPath)
Write-Host 'لم تُحذف أو تُعدَّل أي ملفات في المشروع الأصلي.'
