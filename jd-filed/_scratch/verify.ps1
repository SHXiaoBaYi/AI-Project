$ErrorActionPreference = 'Continue'
$p = 'D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30.xlsx'
$xl = New-Object -ComObject Excel.Application
$xl.Visible = $false; $xl.DisplayAlerts = $false
$out = New-Object Collections.ArrayList
try {
  $wb = $xl.Workbooks.Open($p, 0, $true)
  $ws = $wb.Worksheets.Item('指标总览')
  $n = [int]$ws.UsedRange.Rows.Count
  $vals = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item($n, 13)).Value2
  $cRep = 0; $cRole = 0; $cShop = 0
  for ($j = 1; $j -le 13; $j++) {
    $h = [string]$vals.GetValue(1, $j)
    if ($h -eq '报表类型') { $cRep = $j }
    if ($h -eq '列角色')   { $cRole = $j }
    if ($h -eq '店铺/品牌') { $cShop = $j }
  }
  $expected = @{}; $shops = @{}; $repSet = @{}; $grand = 0
  for ($i = 2; $i -le $n; $i++) {
    $rep = [string]$vals.GetValue($i, $cRep)
    $role = [string]$vals.GetValue($i, $cRole)
    $shops[[string]$vals.GetValue($i, $cShop)] = $true
    $k = "$rep|$role"
    if ($expected.ContainsKey($k)) { $expected[$k]++ } else { $expected[$k] = 1 }
    $repSet[$rep] = $true
    $grand++
  }
  $out.Add("独立复核（直接扫描 指标总览）：数据行=$grand  分组数=$($expected.Count)")
  $out.Add("店铺/品牌 去重 = " + (($shops.Keys | Sort-Object) -join '、'))
  $out.Add('')
  $out.Add('=== 透视表 sheet 整块回读 ===')
  $wsp = $wb.Worksheets.Item('透视表')
  $pr = [int]$wsp.UsedRange.Rows.Count; $pc = [int]$wsp.UsedRange.Columns.Count
  $pv = $wsp.Range($wsp.Cells.Item(1, 1), $wsp.Cells.Item($pr, $pc)).Value2
  for ($i = 1; $i -le $pr; $i++) {
    $line = @()
    for ($j = 1; $j -le $pc; $j++) {
      $v = $pv.GetValue($i, $j)
      if ($null -eq $v) { $v = '' }
      $line += [string]$v
    }
    $out.Add(('R{0:d2}| {1}' -f $i, ($line -join ' | ')))
  }
  # 逐格比对：以行标签(A列) x 列角色 匹配
  $mismatch = 0; $checked = 0
  for ($i = 1; $i -le $pr; $i++) {
    $rep = [string]$pv.GetValue($i, 1)
    if (-not $rep -or -not $repSet.ContainsKey($rep)) { continue }
    for ($j = 2; $j -le $pc; $j++) {
      $role = [string]$pv.GetValue(4, $j)
      if (-not $role) { $role = [string]$pv.GetValue(3, $j) }
      $v = $pv.GetValue($i, $j)
      if ($null -eq $v -or $v -is [string]) { continue }
      $k = "$rep|$role"
      if (-not $expected.ContainsKey($k)) { continue }
      $checked++
      if ([double]$v -ne [double]$expected[$k]) { $mismatch++; $out.Add("  MISMATCH $k pivot=$v expected=$($expected[$k])") }
    }
  }
  $out.Add('')
  $out.Add("透视 vs 明细 逐格比对: checked=$checked mismatch=$mismatch")
  $out.Add('期望交叉表：')
  foreach ($k in ($expected.Keys | Sort-Object)) { $out.Add("  $k = $($expected[$k])") }
  $wb.Close($false)
} finally {
  $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl)
}
[IO.File]::WriteAllLines('D:\AI-XBY\AI-Project\jd-filed\_scratch\verify.txt', $out, (New-Object Text.UTF8Encoding $true))
'lines=' + $out.Count
