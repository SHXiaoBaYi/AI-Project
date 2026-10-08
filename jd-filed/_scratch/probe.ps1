$ErrorActionPreference = 'Continue'
function Probe([string]$label, [scriptblock]$body) {
  try { & $body; "OK   | $label" } catch { "FAIL | $label :: " + $_.Exception.GetType().Name + ' / 0x' + ('{0:X8}' -f $_.Exception.HResult) + ' / ' + $_.Exception.Message }
}
$xl = New-Object -ComObject Excel.Application
$xl.Visible = $false; $xl.DisplayAlerts = $false
try {
  $wb = $xl.Workbooks.Add()
  $ws = $wb.Worksheets.Item(1); $ws.Name = '指标总览'
  $hdr = @('平台','报表类型','原始列名','列角色')
  $data = @(
    @('京东','交易概况','成交金额','本期值'),
    @('京东','交易概况','成交金额-对比日','对比日'),
    @('京东','商品明细','SPU','维度'),
    @('京东','商品明细','成交金额','本期值')
  )
  $arr = New-Object 'object[,]' 5, 4
  for ($j = 0; $j -lt 4; $j++) { $arr[0, $j] = $hdr[$j] }
  for ($i = 0; $i -lt 4; $i++) { for ($j = 0; $j -lt 4; $j++) { $arr[($i + 1), $j] = $data[$i][$j] } }

  Probe 'Value2 = object[,]' { $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(5,4)).Value2 = $arr }
  Probe 'sheet cell A7 formula via .Formula' { $ws.Range('A7').Formula = '=COUNTIFS(''$指标总览''!$B:$B,"交易概况",''$指标总览''!$D:$D,"本期值")' }
  Probe 'read back A7 value' { $v = $ws.Range('A7').Value2; "   A7 => $v" }

  $fArr = New-Object 'object[,]' 2, 1
  $fArr[0,0] = 'cnt'
  $fArr[1,0] = '=COUNTIFS(''指标总览''!$B:$B,"交易概况",指标总览!$D:$D,"本期值")'
  Probe 'Value2 = object[,] containing formula' { $ws.Range('A9').Value2 = $fArr }

  $ws2 = $wb.Worksheets.Add([Type]::Missing, $ws); $ws2.Name = '透视表'
  $probePivot = {
    $src = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(5,4))
    $cache = $wb.PivotCaches().Create(1, $src)
    '   cache created'
    $pt = $cache.CreatePivotTable($ws2.Range('A3'), '指标透析')
    '   pivot table created'
    $pt.PivotFields('报表类型').Orientation = 1
    $pt.PivotFields('列角色').Orientation = 2
    $null = $pt.AddDataField($pt.PivotFields('原始列名'), '列数', -4112)
    '   fields set'
  }
  Probe 'native PivotTable via Range source' $probePivot

  $probePivotAddr = {
    $src = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(5,4))
    $addr = $src.Address($true, $true, 1, $true)
    "   addr => $addr"
    $cache = $wb.PivotCaches().Create(1, $addr)
    $pt = $cache.CreatePivotTable($ws2.Range('K3'), '指标透析2')
    '   ok with address string'
  }
  Probe 'native PivotTable via address string' $probePivotAddr

  Probe 'AutoFilter on header' { $null = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(1,4)).AutoFilter(4) }
  Probe 'Columns.AutoFit' { $null = $ws.Columns.AutoFit() }
  Probe 'AddComment + Shape size' { $cm = $ws.Cells.Item(1,6).AddComment('测试备注'); $cm.Comment.TextSize = 9; $cm.Shape.Height = 120; $cm.Shape.Width = 340 }
  Probe 'FreezePanes' { $ws.Activate(); $xl.ActiveWindow.SplitRow = 1; $xl.ActiveWindow.FreezePanes = $true }
  Probe 'SaveAs xlsx format 51' { $tmp = 'D:\AI-XBY\AI-Project\jd-filed\_scratch\probe_out.xlsx'; $wb.SaveAs($tmp, 51); "   saved $tmp"; $wb.Close($false) }
} finally {
  try { $wb.Close($false) } catch { }
  $xl.Quit()
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl)
}
