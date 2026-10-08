$ErrorActionPreference = 'Continue'
$p = 'D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx'
$xl = New-Object -ComObject Excel.Application
$xl.Visible = $false; $xl.DisplayAlerts = $false
$out = New-Object Collections.ArrayList
try {
  $wb = $xl.Workbooks.Open($p, 0, $true)
  $wsD = $wb.Worksheets.Item('明细数据')
  $n = [int]$wsD.UsedRange.Rows.Count
  $out.Add("明细数据 行数(含表头) = $n")
  $a = $wsD.Range($wsD.Cells.Item(2, 1), $wsD.Cells.Item($n, 13)).Value2

  # PS 侧独立聚合：渠道 x 引入成交金额；并按月累计（板块六/四 的口径是"当前考核月"）
  $psCh = @{}
  $mCost = @{}; $mSale = @{}
  for ($i = 1; $i -le ($n - 1); $i++) {
    $rep = [string]$a.GetValue($i, 3)
    $met = [string]$a.GetValue($i, 10)
    $role = [string]$a.GetValue($i, 11)
    if ($role -ne '本期值') { continue }
    $val = [double]$a.GetValue($i, 12)
    $dvr = $a.GetValue($i, 5)
    $mo = ''
    if ($dvr -is [double]) { try { $mo = [DateTime]::FromOADate([double]$dvr).ToString('yyyy-MM') } catch { } }
    elseif ($dvr) { $mo = ([string]$dvr).Substring(0, [Math]::Min(7, ([string]$dvr).Length)) }
    if ($rep -like '流量来源*' -and $met -eq '引入成交金额') {
      $ch = [string]$a.GetValue($i, 6)
      if ($psCh.ContainsKey($ch)) { $psCh[$ch] += $val } else { $psCh[$ch] = $val }
    }
    if ($mo) {
      if ($rep -like '全站营销*' -and $met -eq '花费') { if ($mCost.ContainsKey($mo)) { $mCost[$mo] += $val } else { $mCost[$mo] = $val } }
      if ($rep -eq '交易概况' -and $met -eq '成交金额') { if ($mSale.ContainsKey($mo)) { $mSale[$mo] += $val } else { $mSale[$mo] = $val } }
    }
  }
  $curMon = ($mCost.Keys | Sort-Object | Select-Object -Last 1)
  $psCost = 0.0; $psSale = 0.0; $psAdG = 0.0
  if ($curMon) { $psCost = $mCost[$curMon]; $psSale = $mSale[$curMon] }
  $out.Add("PS 基线口径：当前考核月 = $curMon")

  $wsX = $wb.Worksheets.Item('图表数据')
  $out.Add('--- 板块一/二 渠道销售额：Excel公式 vs PS独立聚合 ---')
  $maxRow = [int]$wsX.UsedRange.Rows.Count
  $mismatch = 0; $checked = 0
  for ($r = 4; $r -le 40; $r++) {
    $ch = [string]$wsX.Cells.Item($r, 1).Value2
    if (-not $ch -or $ch -eq '合计') { continue }
    $xlV = [double]$wsX.Cells.Item($r, 2).Value2
    $psV = 0.0; if ($psCh.ContainsKey($ch)) { $psV = $psCh[$ch] }
    $checked++
    $ok = [math]::Abs($xlV - $psV) -lt 0.5
    if (-not $ok) { $mismatch++ }
    $pct = [string]$wsX.Cells.Item($r, 3).Text
    $rnk = [string]$wsX.Cells.Item($r, 4).Text
    $out.Add(('  {0,-8} excel={1,12:N2} ps={2,12:N2} 占比={3,7} 排名={4,3} {5}' -f $ch, $xlV, $psV, $pct, $rnk, $(if ($ok) { 'OK' } else { 'MISMATCH' })))
  }
  $out.Add("渠道比对: checked=$checked mismatch=$mismatch")

  $out.Add('--- 板块三 日趋势（抽样前3行 Excel公式值）---')
  for ($r = 4; $r -le 6; $r++) {
    $lab = [string]$wsX.Cells.Item($r, 6).Value2
    $cur = [double]$wsX.Cells.Item($r, 7).Value2
    $prev = [double]$wsX.Cells.Item($r, 8).Value2
    $hb = [string]$wsX.Cells.Item($r, 10).Text
    $out.Add(('  {0} 本期={1,12:N2} 上期={2,12:N2} 环比={3}' -f $lab, $cur, $prev, $hb))
  }

  $out.Add('--- 板块四 目标达成率 ---')
  for ($r = 5; $r -le 6; $r++) {
    $out.Add(('  {0} 目标={1,12:N0} 实际={2,12:N0} 达成率={3}' -f [string]$wsX.Cells.Item($r, 20).Value2,
              [double]$wsX.Cells.Item($r, 21).Value2, [double]$wsX.Cells.Item($r, 22).Value2,
              [string]$wsX.Cells.Item($r, 23).Text))
  }

  $out.Add('--- 板块五 ROI/费比 ---')
  $out.Add(('  ROI目标={0:N2} 实际ROI={1:N2} 费比={2}' -f [double]$wsX.Cells.Item(11, 21).Value2,
            [double]$wsX.Cells.Item(11, 22).Value2, [string]$wsX.Cells.Item(11, 23).Text))

  $wsT = $wb.Worksheets.Item('目标预算')
  $out.Add('--- 板块六 预算使用率（Excel vs PS独立聚合）---')
  $xlCost = [double]$wsT.Cells.Item(12, 2).Value2
  $xlSale = [double]$wsT.Cells.Item(14, 2).Value2
  $out.Add(('  当月广告花费 excel={0,12:N2} ps={1,12:N2} {2}' -f $xlCost, $psCost, $(if ([math]::Abs($xlCost - $psCost) -lt 1) { 'OK' } else { 'MISMATCH' })))
  $out.Add(('  当月销售额     excel={0,12:N2} ps={1,12:N2} {2}' -f $xlSale, $psSale, $(if ([math]::Abs($xlSale - $psSale) -lt 1) { 'OK' } else { 'MISMATCH' })))
  for ($r = 15; $r -le 24; $r++) {
    $out.Add(('  {0,-14} = {1}' -f [string]$wsT.Cells.Item($r, 1).Value2, [string]$wsT.Cells.Item($r, 2).Text))
  }

  $wsP = $wb.Worksheets.Item('商品渠道')
  $out.Add('--- 板块七 单品渠道（当前下拉 = ' + [string]$wsP.Range('D2').Value2 + '）---')
  $out.Add('  注意：商品渠道明细文件尚未导入，此区应为 0（预期）')
  $out.Add(('  B5 = {0}' -f [string]$wsP.Cells.Item(5, 2).Text))

  foreach ($sn in @('看板', '商品渠道')) {
    $ws = $wb.Worksheets.Item($sn)
    $cnt = [int]$ws.ChartObjects().Count
    $out.Add("sheet $sn 图表数 = $cnt")
    for ($i = 1; $i -le $cnt; $i++) {
      $co = $ws.ChartObjects().Item($i)
      $ct = $co.Chart.ChartType
      $sc = [int]$co.Chart.SeriesCollection().Count
      $out.Add(('   [{0}] {1} ChartType={2} 系列数={3}' -f $i, $co.Name, $ct, $sc))
      for ($s = 1; $s -le $sc; $s++) {
        $ser = $co.Chart.SeriesCollection($s)
        $fillVis = ''
        try { $fillVis = [string]$ser.Format.Fill.Visible } catch { }
        $out.Add(('      s{0} name={1} type={2} axis={3} fillVisible={4} points={5}' -f $s, [string]$ser.Name, [string]$ser.ChartType, [string]$ser.AxisGroup, $fillVis, @($ser.Values).Count))
      }
    }
  }
  $wb.Close($false)
} finally {
  $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl)
}
[IO.File]::WriteAllLines('D:\AI-XBY\AI-Project\jd-filed\_scratch\verify_dash.txt', $out, (New-Object Text.UTF8Encoding $true))
'lines=' + $out.Count
