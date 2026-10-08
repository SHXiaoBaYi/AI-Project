$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
$script = 'C:\Users\Administrator\.qwenworkcn\skills\pivot-analysis\scripts\build_jd_pivot.ps1'
try {
  & $script -Dir 'D:\AI-XBY\AI-Project\jd-filed' -OutDir 'D:\AI-XBY\AI-Project\jd-filed\_scratch' -Platform '京东' -ShopLabel '冒烟' -DateTag '26-09-30' -ErrorAction Stop | Out-Null
  'SMOKE_OK'
} catch {
  'SMOKE_FAIL: ' + $_.Exception.Message
  $_.ScriptStackTrace
}
Get-ChildItem 'D:\AI-XBY\AI-Project\jd-filed\_scratch\京东_冒烟_26-09-30.xlsx' |
  ForEach-Object { 'artifact=' + $_.Name + ' size=' + $_.Length }
