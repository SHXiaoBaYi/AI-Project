$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
$k = 'C:\Users\Administrator\.qwenworkcn\skills\pivot-analysis\scripts'
$dir = 'D:\AI-XBY\AI-Project\jd-filed'
try {
  # 用「技能安装副本」跑完整两步，验证发布物本身可复用
  & (Join-Path $k 'build_jd_pivot.ps1') -Dir $dir -Platform '京东' -ShopLabel '奔养养' -ErrorAction Stop | Out-Null
  'STEP1_PIVOT_OK'
  $book = Join-Path $dir '京东_奔养养_26-09-30.xlsx'
  & (Join-Path $k 'build_jd_dashboard.ps1') -InFile $book -ErrorAction Stop | Out-Null
  'STEP2_DASHBOARD_OK'
  'book=' + $book + ' exists=' + (Test-Path -LiteralPath $book) + ' size=' + (Get-Item -LiteralPath $book).Length
} catch {
  'FAIL: ' + $_.Exception.Message
  $_.ScriptStackTrace
}
