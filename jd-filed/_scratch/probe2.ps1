$ErrorActionPreference = 'Continue'
$xl = New-Object -ComObject Excel.Application
$xl.Visible = $false; $xl.DisplayAlerts = $false
try {
  $wb = $xl.Workbooks.Add()
  $ws = $wb.Worksheets.Item(1); $ws.Name = 'T'
  # 模拟 明细数据：A=报表类型 B=基础指标名 C=列角色 D=统计日期 E=数值(用 Value2 写字符串)
  $hdr = New-Object 'object[,]' 1, 5
  for ($j = 0; $j -lt 5; $j++) { $hdr[0, $j] = @('报表类型','基础指标名','列角色','统计日期','数值')[$j] }
  $r1 = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(1,5)); $r1.Value2 = $hdr
  $rows = @(
    @('商品渠道明细','成交金额','本期值','2026-09-28','12345.60'),
    @('商品渠道明细','成交金额','本期值','2026-09-28','7654.40'),
    @('商品渠道明细','成交金额','本期值','2026-09-27','1000'),
    @('商品渠道明细','成交转化率','本期值','2026-09-28','12.3%')
  )
  $arr = New-Object 'object[,]' 4, 5
  for ($i = 0; $i -lt 4; $i++) { for ($j = 0; $j -lt 5; $j++) { $arr[$i, $j] = $rows[$i][$j] } }
  $tgtX = $ws.Range($ws.Cells.Item(2,1), $ws.Cells.Item(5,5)).Value2 = $arr

  for ($r = 2; $r -le 5; $r++) {
    $dv = $ws.Cells.Item($r, 4).Value2
    $nv = $ws.Cells.Item($r, 5).Value2
    "R$r  日期Type=" + $(if ($null -eq $dv) { 'null' } else { $dv.GetType().Name }) + " val=$dv" +
    "  | 数值Type=" + $(if ($null -eq $nv) { 'null' } else { $nv.GetType().Name }) + " val=$nv"
  }
  # SUMIFS：数值列若为文本则得 0
  $ws.Range('G2').Formula = '=SUMIFS($E:$E,$A:$A,"商品渠道明细",$B:$B,"成交金额")'
  # 文本判据
  $ws.Range('G3').Formula = '=SUMIFS($E:$E,$A:$A,"商品渠道明细",$D:$D,"2026-09-28")'
  # 日期序列判据
  $ws.Range('G4').Formula = '=SUMIFS($E:$E,$A:$A,"商品渠道明细",$D:$D,">="&DATE(2026,9,28),$D:$D,"<="&DATE(2026,9,28))'
  # COUNTIFS 按日期单元格引用
  $ws.Range('G5').Formula = '=COUNTIFS($D:$D,$H$2)'
  $ws.Range('H2').Value2 = '2026-09-28'
  foreach ($c in 'G2','G3','G4','G5','H2') {
    $v = $ws.Range($c).Value2
    "$c => $v  (" + $(if ($null -eq $v) { 'null' } else { $v.GetType().Name }) + ')'
  }
  $ws.Range('J2').Formula = '=COUNT($E$2:$E$5)'
  "COUNT(E2:E5) => " + $ws.Range('J2').Value2 + '  (文本数字不被 COUNT)'
  $wb.Close($false)
} finally {
  $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl)
}
