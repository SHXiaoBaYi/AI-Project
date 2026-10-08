$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
$script = 'D:\AI-XBY\AI-Project\jd-filed\scripts\build_jd_dashboard.ps1'
$book   = 'D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30.xlsx'
$map    = 'D:\AI-XBY\AI-Project\jd-filed\scripts\dashboard_map_jd.json'
$mode   = $args[0]
try {
  if ($mode -eq 'demo') {
    & $script -InFile $book -MapFile $map -Demo -ErrorAction Stop | Out-Null
    'DEMO_OK'
  } else {
    & $script -InFile $book -MapFile $map -ErrorAction Stop | Out-Null
    'REAL_OK'
  }
} catch {
  'FAIL: ' + $_.Exception.Message
  $_.ScriptStackTrace
}
