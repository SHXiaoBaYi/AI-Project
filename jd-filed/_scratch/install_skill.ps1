$ErrorActionPreference = 'Stop'
$s = 'D:\AI-XBY\AI-Project\jd-filed\scripts'
$k = 'C:\Users\Administrator\.qwenworkcn\skills\pivot-analysis'
$noBom = New-Object Text.UTF8Encoding $false
$withBom = New-Object Text.UTF8Encoding $true

foreach ($f in @('build_jd_pivot.ps1', 'build_jd_dashboard.ps1')) {
  $srcPath = Join-Path $s $f
  $dstPath = Join-Path (Join-Path $k 'scripts') $f
  $txt = [IO.File]::ReadAllText($srcPath, $noBom)
  [IO.File]::WriteAllText($dstPath, $txt, $withBom)
  $errs = $null; $toks = $null
  [System.Management.Automation.Language.Parser]::ParseFile($dstPath, [ref]$toks, [ref]$errs) | Out-Null
  $b = [IO.File]::ReadAllBytes($dstPath)
  "{0}  bytes={1} bom={2} syntaxErrors={3}" -f $f, $b.Length, ($b[0..2] -join '.'), $errs.Count
  foreach ($e in $errs) { "   L" + $e.Extent.StartLineNumber + ": " + $e.Message }
}

$mapSrc = Join-Path $s 'dashboard_map_jd.json'
$mapDst = Join-Path (Join-Path $k 'assets') 'dashboard_map_jd.json'
$mt = [IO.File]::ReadAllText($mapSrc, $noBom)
[IO.File]::WriteAllText($mapDst, $mt, $withBom)
"map -> " + ([IO.File]::ReadAllText($mapDst, $withBom) | ConvertFrom-Json).platform
