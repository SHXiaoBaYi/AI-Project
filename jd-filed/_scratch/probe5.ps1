$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb = $xl.Workbooks.Add(); $ws = $wb.Worksheets.Item(1)
  function T($n, [scriptblock]$b) { try { & $b; "OK   $n" } catch { "FAIL $n :: " + $_.Exception.Message } }
  T 'Cells.Item(1,1).Value2 = string'   { $ws.Cells.Item(1,1).Value2 = '搜索' }
  T 'Cells.Item(1,2).Value = string'    { $ws.Cells.Item(1,2).Value = '搜索' }
  T 'Range("A3").Value2 = string'       { $ws.Range('A3').Value2 = '搜索' }
  T 'Range(Cells,Cells).Value2 = string[]{...}' {
      $a = New-Object 'object[,]' 2,1; $a[0,0]='首页推荐'; $a[1,0]='购物车'
      $r = $ws.Range($ws.Cells.Item(1,4), $ws.Cells.Item(2,4)); $r.Value2 = $a }
  T 'Cells.Item(1,5).Value2 = int'      { $ws.Cells.Item(1,5).Value2 = 123 }
  T 'Cells.Item(1,6).Value2 = double'   { $ws.Cells.Item(1,6).Value2 = [double]1.5 }
  T 'readback A1' { $v = $ws.Cells.Item(1,1).Value2; if ($v -ne '搜索') { throw "got [$v]" } }
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
