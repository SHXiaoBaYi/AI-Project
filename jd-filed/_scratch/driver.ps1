$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
$script = 'D:\AI-XBY\AI-Project\jd-filed\scripts\build_jd_pivot.ps1'
try {
  & $script -Dir 'D:\AI-XBY\AI-Project\jd-filed' -Platform '京东' -ShopLabel '奔养养' -ErrorAction Stop
} catch {
  "=== DRIVER CAUGHT ==="
  "TYPE    : " + $_.Exception.GetType().FullName
  "HRESULT : 0x" + ('{0:X8}' -f $_.Exception.HResult)
  "MESSAGE : " + $_.Exception.Message
  "LINE    : " + $_.InvocationInfo.ScriptLineNumber + "  " + $_.InvocationInfo.Line.Trim()
  "STACK   :"
  $_.ScriptStackTrace
  if ($_.Exception.InnerException) { "INNER: " + $_.Exception.InnerException.Message }
}
