<#
.SYNOPSIS
  在透析工作簿上叠加「经营看板」：7 大固定板块 + Excel 原生活图表（无 Python 依赖）。

.DESCRIPTION
  读取 build_jd_pivot.ps1 产出的工作簿（依赖其中的「明细数据」长表 + 「指标总览」），追加/重建：
    目标预算  —— 人工可编辑的目标/预算输入表（有目标文件时自动预填），板块六的表格也在此
    图表数据  —— 所有图表的数据源，全部用 SUMIFS/比率公式挂在「明细数据」与「目标预算」上
    看板      —— 板块一~七的原生图表 + KPI
    商品渠道  —— 下拉选 SPU，联动该单品的渠道贡献表与饼图
  板块规格（写死，见 SKILL.md）：
    一 全渠道GMV(柱形, 横轴渠道/纵轴金额)
    二 渠道贡献(饼图 + 销售额/占比/排名表)
    三 日/周/月/年 同环比(本期vs上期双柱 + 同比折线挂次轴)
    四 月度/年度目标达成率(目标空心 + 实际实心 + 完成率标注)
    五 广告ROI/费比(目标空心 + 实际实心 + 费比数值)
    六 广告预算使用率 + 全月使用进度(表格)
    七 商品-渠道 销售额/件数/下单人数(表 + 饼图；下拉切换单品看渠道贡献)

.PARAMETER Demo
  另存 *_演示.xlsx 并灌入随机演示数据，用于肉眼校验图表形态；演示来源标注「演示数据(非真实)」。

.NOTES
  依赖本机 Excel + PowerShell COM。图表 API 按 Excel 2007(12.0) 可用水准编写，装饰性调用全部包 try/catch。
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $InFile,
  [string] $MapFile = '',
  [string] $OutFile = '',
  [switch] $Demo
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $InFile)) { throw "找不到透析工作簿：$InFile" }
if (-not $MapFile) {
  $MapFile = Join-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..') 'assets\dashboard_map_jd.json'
}
if (-not (Test-Path -LiteralPath $MapFile)) { throw "找不到映射文件：$MapFile" }
$map = [IO.File]::ReadAllText($MapFile, [Text.UTF8Encoding]::new($true)) | ConvertFrom-Json

if (-not $OutFile) {
  if ($Demo) {
    $OutFile = Join-Path (Split-Path -Parent $InFile) ([IO.Path]::GetFileNameWithoutExtension($InFile) + '_演示.xlsx')
  } else { $OutFile = $InFile }
}
$scratch = Join-Path (Split-Path -Parent $InFile) '_scratch'
if (-not (Test-Path -LiteralPath $scratch)) { $null = New-Item -ItemType Directory -Path $scratch }

$log = New-Object Collections.ArrayList
$chartStatus = @{}

# 明细数据列位（与 build_jd_pivot.ps1 表头严格对应）
# A平台 B店铺/品牌 C报表类型 D时间粒度 E统计日期 F渠道 G SPU H SPU名称 I维度组合 J基础指标名 K列角色 L数值 M来源文件
$P  = '明细数据!'
$cRep = "$P`$C:`$C"; $cDate = "$P`$E:`$E"; $cChan = "$P`$F:`$F"
$cSpuN = "$P`$H:`$H"; $cMet = "$P`$J:`$J"; $cRole = "$P`$K:`$K"; $cVal = "$P`$L:`$L"

function CQ([string]$s) { '"' + $s.Replace('"', '""') + '"' }
function SUMIFS_D([string[]]$pairs) { '=SUMIFS(' + $cVal + ',' + ($pairs -join ',') + ')' }
function RGB([int]$r, [int]$g, [int]$b) { $r + ($g * 256) + ($b * 65536) }

$CLR_ACTUAL = RGB 41 105 176
$CLR_TARGET = RGB 150 150 150
$CLR_PREV   = RGB 237 125 49
$CLR_LINE   = RGB 112 173 71
$CLR_HDR    = RGB 217 217 217
$CLR_INPUT  = RGB 255 242 204
$C_PIE = @( (RGB 41 105 176), (RGB 237 125 49), (RGB 112 173 71), (RGB 158 72 190),
            (RGB 255 192 0), (RGB 191 191 191), (RGB 89 89 89), (RGB 255 128 128) )

$xl = New-Object -ComObject Excel.Application
$xl.Visible = $false
$xl.DisplayAlerts = $false
$wb = $null
try {
  if ($OutFile -ne $InFile) { Copy-Item -LiteralPath $InFile -Destination $OutFile -Force; $null = $log.Add("复制 -> $OutFile") }
  $wb = $xl.Workbooks.Open($OutFile)
  $xl.ScreenUpdating = $false

  # ------------------------------------------------------ 读取上下文 --
  $wsD = $wb.Worksheets.Item('明细数据')
  $nDet = [int]$wsD.UsedRange.Rows.Count
  $repSeen = @{}; $chanSeen = @{}; $spuSeen = @{}; $dateSeen = @{}
  $rowsRead = 0
  if ($nDet -gt 1) {
    $a = $wsD.Range($wsD.Cells.Item(2, 1), $wsD.Cells.Item($nDet, 13)).Value2
    $rowsRead = $nDet - 1
    for ($i = 1; $i -le $rowsRead; $i++) {
      $rep = [string]$a.GetValue($i, 3)
      if ($rep) { $repSeen[$rep] = $true }
      $ch = [string]$a.GetValue($i, 6); if ($ch) { $chanSeen[$ch] = $true }
      $sn = [string]$a.GetValue($i, 8); if ($sn) { $spuSeen[$sn] = $true }
      $dv = $a.GetValue($i, 5)
      $ds = ''
      if ($dv -is [double]) { try { $ds = [DateTime]::FromOADate([double]$dv).ToString('yyyy-MM-dd') } catch { } }
      elseif ($dv) { $ds = ([string]$dv); if ($ds.Length -ge 10) { $ds = $ds.Substring(0, 10) } }
      if ($ds) { $dateSeen[$ds] = $true }
    }
  }
  # 报表全集：明细为空时退回 指标总览 的报表类型列
  $wsM = $wb.Worksheets.Item('指标总览')
  $nm = [int]$wsM.UsedRange.Rows.Count
  if ($nm -gt 1) {
    $ma = $wsM.Range($wsM.Cells.Item(2, 3), $wsM.Cells.Item($nm, 3)).Value2
    for ($i = 1; $i -le ($nm - 1); $i++) { $v = [string]$ma.GetValue($i, 1); if ($v) { $repSeen[$v] = $true } }
  }
  $allReps = @($repSeen.Keys | Sort-Object)
  function Resolve-Rep([string]$pattern) {
    if (-not $pattern) { return '' }
    foreach ($x in $allReps) { if ($x -like $pattern) { return $x } }
    return ''
  }
  $R = @{}
  foreach ($k in $map.report_patterns.PSObject.Properties.Name) { $R[$k] = Resolve-Rep $map.report_patterns.$k }
  $M = @{}
  foreach ($k in $map.metrics.PSObject.Properties.Name) {
    $M[$k] = [pscustomobject]@{ rep = $R[$map.metrics.$k.report]; met = [string]$map.metrics.$k.metric }
  }
  $missing = @($R.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object { $_.Key })
  $null = $log.Add('报表解析：' + (($R.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '))
  if ($missing.Count) { $null = $log.Add("未解析到的报表类型（对应板块会出空图，属预期）：$($missing -join ', ')") }

  $channels = @($chanSeen.Keys | Sort-Object)
  $spus = @($spuSeen.Keys | Sort-Object)
  $dateList = @($dateSeen.Keys | Sort-Object)

  # ------------------------------------------------------ 演示数据 --
  $demoSeed = $null
  if ($Demo) {
    $rnd = New-Object Random(7)
    $channels = @('搜索', '首页推荐', '购物车', '我的京东', '活动会场', '站外投放')
    $spus = @('羊毛衫A', '羊绒衫B', '围巾C', '手套D', '马甲E', '开衫F', '大衣G', '毛衣H')
    $chanSeen = @{}; foreach ($c in $channels) { $chanSeen[$c] = $true }
    $spuSeen = @{}; foreach ($s in $spus) { $spuSeen[$s] = $true }
    $dateSeen = @{}
    $dl2 = New-Object Collections.ArrayList
    $base = (Get-Date).Date.AddMonths(-2)
    for ($i = 0; $i -lt 60; $i++) { $k = $base.AddDays($i).ToString('yyyy-MM-dd'); $null = $dl2.Add($k); $dateSeen[$k] = $true }
    $dateList = @($dl2)
    # 演示报表名：显式赋值 + 空值兜底（$(if) 内联写法在此处产出过空串，勿回退）
    $chRep = [string]$M.gmv_channel.rep; if (-not $chRep) { $chRep = '流量来源_演示' }
    $pcRep = [string]$M.pc_gmv.rep;      if (-not $pcRep) { $pcRep = '商品渠道明细_演示' }
    $trRep = [string]$M.gmv.rep;         if (-not $trRep) { $trRep = '交易概况_演示' }
    $adRep = [string]$M.ad_cost.rep;     if (-not $adRep) { $adRep = '全站营销_演示' }
    $M.gmv_channel.rep = $chRep; $M.pc_gmv.rep = $pcRep; $M.gmv.rep = $trRep; $M.ad_cost.rep = $adRep
    $rows = New-Object Collections.ArrayList
    foreach ($ds in $dateList) {
      $wave = [math]::Sin(([double]([DateTime]::Parse($ds).DayOfWeek)) * 0.9) * 0.25 + 1
      foreach ($c in $channels) {
        $g = [math]::Round((6000 + $rnd.NextDouble() * 9000) * $wave, 2)
        $null = $rows.Add(@($chRep, $ds, $c, '', $M.gmv_channel.met, $g))
        $null = $rows.Add(@($chRep, $ds, $c, '', '引入成交客户数', [math]::Round($g / 320)))
        $null = $rows.Add(@($chRep, $ds, $c, '', '引入成交商品件数', [math]::Round($g / 160)))
      }
      foreach ($s in $spus) {
        foreach ($c in $channels) {
          $g2 = [math]::Round((150 + $rnd.NextDouble() * 1600) * $wave, 2)
          $null = $rows.Add(@($pcRep, $ds, $c, $s, $M.pc_gmv.met, $g2))
          $null = $rows.Add(@($pcRep, $ds, $c, $s, $M.pc_qty.met, [math]::Round($g2 / 190)))
          $null = $rows.Add(@($pcRep, $ds, $c, $s, $M.pc_buyers.met, [math]::Round($g2 / 300)))
        }
      }
      $tg = [math]::Round((40000 + $rnd.NextDouble() * 40000) * $wave, 2)
      $null = $rows.Add(@($trRep, $ds, '', '', $M.gmv.met, $tg))
      $cost = [math]::Round((1200 + $rnd.NextDouble() * 4200) * $wave, 2)
      $null = $rows.Add(@($adRep, $ds, '', '', $M.ad_cost.met, $cost))
      $null = $rows.Add(@($adRep, $ds, '', '', $M.ad_gmv.met, [math]::Round($cost * (2.4 + $rnd.NextDouble() * 2.2), 2)))
    }
    $demoSeed = $rows
    $null = $log.Add("演示模式：生成 $($rows.Count) 行随机数据（来源标注 演示数据(非真实)）")
  }

  # ------------------------------------------------------ 重建工作表 --
  foreach ($sn in @('目标预算', '图表数据', '看板', '商品渠道')) {
    try { $wb.Worksheets.Item($sn).Delete() } catch { }
  }
  $wsT = $wb.Worksheets.Add(); $wsT.Name = '目标预算'
  $wsX = $wb.Worksheets.Add(); $wsX.Name = '图表数据'
  $wsB = $wb.Worksheets.Add(); $wsB.Name = '看板'
  $wsP = $wb.Worksheets.Add(); $wsP.Name = '商品渠道'

  function Head($ws, [int]$row, [int]$col, [string[]]$labels) {
    for ($j = 0; $j -lt $labels.Count; $j++) { $ws.Cells.Item($row, $col + $j) = $labels[$j] }
    $h = $ws.Range($ws.Cells.Item($row, $col), $ws.Cells.Item($row, $col + $labels.Count - 1))
    $h.Font.Bold = $true; $h.Interior.Pattern = 1; $h.Interior.Color = $CLR_HDR
    return $h
  }

  # ======================= 目标预算（输入表 + 板块六） =======================
  $wsT.Cells.Item(1, 1) = '目标与预算输入表（黄色单元格人工填写；有目标文件时脚本自动预填）'
  $wsT.Cells.Item(1, 1).Font.Bold = $true; $wsT.Cells.Item(1, 1).Font.Size = 13

  $monthKeys = New-Object Collections.ArrayList
  foreach ($ds in $dateList) { $mk = $ds.Substring(0, 7); if (-not ($monthKeys -contains $mk)) { $null = $monthKeys.Add($mk) } }
  if ($monthKeys.Count -eq 0) {
    $st = (Get-Date).Date.AddMonths(-14)
    for ($i = 0; $i -lt 15; $i++) { $null = $monthKeys.Add($st.AddMonths($i).ToString('yyyy-MM')) }
  }
  $nMon = $monthKeys.Count
  $mLast = [string]$monthKeys[$nMon - 1]
  $tgtRepName = $R.target
  # 有目标文件则按 月份 预填
  $tgtByMonth = @{}
  if ($tgtRepName -and $rowsRead -gt 0) {
    $a2 = $wsD.Range($wsD.Cells.Item(2, 1), $wsD.Cells.Item($nDet, 13)).Value2
    for ($i = 1; $i -le $rowsRead; $i++) {
      if ([string]$a2.GetValue($i, 3) -ne $tgtRepName) { continue }
      $dv = $a2.GetValue($i, 5); $mk = ''
      if ($dv -is [double]) { try { $mk = [DateTime]::FromOADate([double]$dv).ToString('yyyy-MM') } catch { } }
      elseif ($dv) { $mk = ([string]$dv).Substring(0, [Math]::Min(7, ([string]$dv).Length)) }
      if (-not $mk) { continue }
      $met = [string]$a2.GetValue($i, 10)
      if (-not $tgtByMonth.ContainsKey($mk)) { $tgtByMonth[$mk] = @{} }
      $tgtByMonth[$mk][$met] = [double]$a2.GetValue($i, 12)
    }
  }
  $null = Head $wsT 3 1 @('月份', '月度目标', '广告预算', 'ROI目标', '费比上限')
  for ($i = 0; $i -lt $nMon; $i++) {
    $r = 4 + $i
    $wsT.Cells.Item($r, 1) = ([DateTime]::ParseExact([string]$monthKeys[$i] + '-01', 'yyyy-MM-dd', $null)).ToOADate()
    $wsT.Cells.Item($r, 1).NumberFormat = 'yyyy-mm'
    $maps = @(@($map.target_columns.monthly, 2, '#,##0'), @($map.target_columns.budget, 3, '#,##0'),
              @($map.target_columns.roi, 4, '0.00'), @($map.target_columns.cost_cap, 5, '0.0%'))
    foreach ($mp in $maps) {
      $col = $mp[1]
      $wsT.Cells.Item($r, $col).Interior.Pattern = 1
      $wsT.Cells.Item($r, $col).Interior.Color = $CLR_INPUT
      $wsT.Cells.Item($r, $col).NumberFormat = $mp[2]
      $mk = [string]$monthKeys[$i]
      if ($tgtByMonth.ContainsKey($mk) -and $tgtByMonth[$mk].ContainsKey([string]$mp[0])) {
        $wsT.Cells.Item($r, $col) = $tgtByMonth[$mk][[string]$mp[0]]
      } elseif ($Demo) {
        try {
          $demoVal = 0.0
          switch ([int]$col) {
            2 { $demoVal = [double](900000 + $i * 45000) }
            3 { $demoVal = [double](60000 + $i * 2600) }
            4 { $demoVal = [double][math]::Round(3.1 + $i * 0.05, 2) }
            5 { $demoVal = 0.085 }
          }
          $wsT.Cells.Item($r, [int]$col) = $demoVal
        } catch {
          $null = $log.Add("目标预算预填失败 r=$r col=$col : " + $_.Exception.Message)
        }
      }
    }
  }
  $mRowEnd = 3 + $nMon
  $vl = '$A$4:$E$' + $mRowEnd
  $wsT.Cells.Item(2, 7) = '当前考核月份(可改)'; $wsT.Cells.Item(2, 7).Font.Bold = $true
  $wsT.Cells.Item(2, 8) = ([DateTime]::ParseExact($mLast + '-01', 'yyyy-MM-dd', $null)).ToOADate()
  $wsT.Cells.Item(2, 8).NumberFormat = 'yyyy-mm'
  $wsT.Range('H2').Interior.Pattern = 1; $wsT.Range('H2').Interior.Color = $CLR_INPUT
  $lab = @('月度目标', '年度累计目标', '当月广告预算', 'ROI目标', '费比上限')
  $frm = @(
    ('=IFERROR(VLOOKUP($H$2,' + $vl + ',2,FALSE),NA())'),
    ('=IFERROR(SUMIFS($B$4:$B$' + $mRowEnd + ',$A$4:$A$' + $mRowEnd + ',"<="&$H$2),NA())'),
    ('=IFERROR(VLOOKUP($H$2,' + $vl + ',3,FALSE),NA())'),
    ('=IFERROR(VLOOKUP($H$2,' + $vl + ',4,FALSE),NA())'),
    ('=IFERROR(VLOOKUP($H$2,' + $vl + ',5,FALSE),NA())')
  )
  for ($i = 0; $i -lt 5; $i++) {
    $r = 3 + $i
    $wsT.Cells.Item($r, 7) = $lab[$i]; $wsT.Cells.Item($r, 7).Font.Bold = $true
    $wsT.Cells.Item($r, 8).Formula = $frm[$i]
  }
  $wsT.Range('H3:H5').NumberFormat = '#,##0'; $wsT.Range('H6').NumberFormat = '0.00'; $wsT.Range('H7').NumberFormat = '0.0%'

  # 板块六：表格
  $wsT.Cells.Item(10, 1) = '六、广告预算使用率 与 全月使用进度（表格）'
  $wsT.Cells.Item(10, 1).Font.Bold = $true; $wsT.Cells.Item(10, 1).Font.Size = 12
  $null = Head $wsT 11 1 @('指标', '数值', '口径说明')
  $MS = '目标预算!$H$2'
  $ME = '目标预算!$H$2+32-DAY(目标预算!$H$2)'
  $adR = CQ $M.ad_cost.rep; $trR = CQ $M.gmv.rep
  $fCost = SUMIFS_D @($cRep, $adR, $cMet, (CQ $M.ad_cost.met), $cRole, '"本期值"', $cDate, ('">="&' + $MS), $cDate, ('"<="&' + $ME))
  $fAdG  = SUMIFS_D @($cRep, $adR, $cMet, (CQ $M.ad_gmv.met), $cRole, '"本期值"', $cDate, ('">="&' + $MS), $cDate, ('"<="&' + $ME))
  $fSale = SUMIFS_D @($cRep, $trR, $cMet, (CQ $M.gmv.met), $cRole, '"本期值"', $cDate, ('">="&' + $MS), $cDate, ('"<="&' + $ME))
  $fTimeProg = '=IFERROR(DAY(' + $ME + ')/DAY(EOMONTH(' + $MS + ',0)),NA())'
  $b6 = @(
    @('当月广告花费', $fCost, '#,##0.00', '推广报表花费合计'),
    @('当月广告成交额', $fAdG, '#,##0.00', '全站交易额合计'),
    @('当月销售额', $fSale, '#,##0.00', '成交金额合计（费比分母）'),
    @('月度广告预算', '=$H$5', '#,##0.00', '取自本表 H5'),
    @('当前使用率', '=IFERROR($B$12/$B$15,NA())', '0.0%', '已花费 / 预算'),
    @('时间进度', $fTimeProg, '0.0%', '已过天数 / 当月总天数'),
    @('全月使用进度', '=IFERROR($B$16-$B$17,NA())', '0.0%', '使用率 - 时间进度；正数=花钱快于走日历'),
    @('实际ROI', '=IFERROR($B$13/$B$12,NA())', '0.00', '广告成交额 / 花费'),
    @('ROI目标', '=$H$6', '0.00', '取自本表 H6'),
    @('ROI达成', '=IFERROR($B$19/$B$20,NA())', '0.0%', '实际ROI / ROI目标'),
    @('花费比例(费比)', '=IFERROR($B$12/$B$14,NA())', '0.0%', '花费 / 销售额'),
    @('费比上限', '=$H$7', '0.0%', '取自本表 H7'),
    @('预算预警', '=IF($B$16>$B$17+0.05,"超前花钱",IF($B$16<$B$17-0.15,"投放不足","进度正常"))', '@', '使用率对比时间进度的阈值判断')
  )
  for ($i = 0; $i -lt $b6.Count; $i++) {
    $r = 12 + $i
    $wsT.Cells.Item($r, 1) = $b6[$i][0]
    try { $wsT.Cells.Item($r, 2).Formula = $b6[$i][1] }
    catch { $null = $log.Add("公式写入失败 r=$r label=$($b6[$i][0]) :: $($_.Exception.Message) :: [$($b6[$i][1])]") }
    $wsT.Cells.Item($r, 2).NumberFormat = $b6[$i][2]
    $wsT.Cells.Item($r, 3) = $b6[$i][3]
    $wsT.Cells.Item($r, 3).Font.Color = (RGB 120 120 120)
  }
  $wsT.Columns.Item(1).ColumnWidth = 22; $wsT.Columns.Item(2).ColumnWidth = 18
  $wsT.Columns.Item(3).ColumnWidth = 44; $wsT.Columns.Item(7).ColumnWidth = 20
  $wsT.Columns.Item(8).ColumnWidth = 16

  # ============================ 图表数据 ============================
  $wsX.Cells.Item(1, 1) = '本表全为公式，挂在「明细数据」+「目标预算」上；数据更新后图表自动重算，无需重跑脚本'
  $wsX.Cells.Item(1, 1).Font.Italic = $true; $wsX.Cells.Item(1, 1).Font.Color = RGB 120 120 120

  # ---- 板块一/二：渠道贡献表 A3:D
  $chCap = 10
  if ($channels.Count -gt 0) { $chCap = [Math]::Max($channels.Count, [int]$map.limits.channels) }
  if ($chCap -gt 40) { $chCap = 40 }
  $null = Head $wsX 3 1 @('渠道', '销售额', '占比', '排名')
  $chFirst = 4; $chEnd = 3 + $chCap
  $gcR = CQ $M.gmv_channel.rep
  for ($i = 0; $i -lt $chCap; $i++) {
    $r = $chFirst + $i
    if ($i -lt $channels.Count) {
      try { $wsX.Cells.Item($r, 1) = $channels[$i] } catch {
        $cv = $channels[$i]
        $null = $log.Add("渠道标签写入失败 r=$r i=$i type=" + $(if ($null -eq $cv) { 'null' } else { $cv.GetType().FullName }) + " rank=" + @($cv).Rank + " val=[$cv] :: " + $_.Exception.Message)
      }
    }
    $wsX.Cells.Item($r, 2).Formula = SUMIFS_D @($cRep, $gcR, $cMet, (CQ $M.gmv_channel.met), $cRole, '"本期值"', $cChan, ('$A' + $r))
    $wsX.Cells.Item($r, 3).Formula = '=IFERROR($B' + $r + '/SUM($B$' + $chFirst + ':$B$' + $chEnd + '),NA())'
    $wsX.Cells.Item($r, 4).Formula = '=IF($B' + $r + '<=0,"-",RANK($B' + $r + ',$B$' + $chFirst + ':$B$' + $chEnd + '))'
  }
  $wsX.Cells.Item($chEnd + 1, 1) = '合计'; $wsX.Cells.Item($chEnd + 1, 1).Font.Bold = $true
  $wsX.Cells.Item($chEnd + 1, 2).Formula = '=SUM($B$' + $chFirst + ':$B$' + $chEnd + ')'
  $wsX.Range($wsX.Cells.Item($chFirst, 2), $wsX.Cells.Item($chEnd + 1, 2)).NumberFormat = '#,##0'
  $wsX.Range($wsX.Cells.Item($chFirst, 3), $wsX.Cells.Item($chEnd, 3)).NumberFormat = '0.0%'
  $null = $wsX.Columns.Item(1).AutoFit(); $null = $wsX.Columns.Item(2).AutoFit()

  # ---- 板块三：日/周/月/年 双柱+折线（F 起，辅助日期列 M:R）
  $trRq = CQ $M.gmv.rep
  function Build-Period([int]$hdrRow, [string]$label, $buckets) {
    # 列序刻意让 本期/上期/同比% 连续：F=期间 G=本期 H=上期 I=同比% J=环比% K=去年同期
    # （Excel 2007 的 SeriesCollection.NewSeries 不可靠，靠连续区间一次绑 3 条系列）
    $null = Head $wsX $hdrRow 6 @($label, '本期销售额', '上期销售额', '同比%', '环比%', '去年同期')
    $n = @($buckets).Count
    if ($n -eq 0) { $script:BLK[$label] = @{ Hdr = $hdrRow; First = $hdrRow + 1; Last = $hdrRow }; return }
    for ($i = 0; $i -lt $n; $i++) {
      $r = $hdrRow + 1 + $i
      $b = $buckets[$i]
      $wsX.Cells.Item($r, 6) = $b.Name
      $gS = '">="&$M' + $r; $gE = '"<="&$N' + $r
      $hS = '">="&$O' + $r; $hE = '"<="&$P' + $r
      $iS = '">="&$Q' + $r; $iE = '"<="&$R' + $r
      $gCore = @($cRep, $trRq, $cMet, (CQ $M.gmv.met), $cRole, '"本期值"')
      $wsX.Cells.Item($r, 7).Formula = SUMIFS_D ($gCore + @($cDate, $gS, $cDate, $gE))
      $wsX.Cells.Item($r, 8).Formula = SUMIFS_D ($gCore + @($cDate, $hS, $cDate, $hE))
      $wsX.Cells.Item($r, 11).Formula = SUMIFS_D ($gCore + @($cDate, $iS, $cDate, $iE))
      $wsX.Cells.Item($r, 9).Formula = '=IFERROR(($G' + $r + '-$K' + $r + ')/$K' + $r + ',NA())'
      $wsX.Cells.Item($r, 10).Formula = '=IFERROR(($G' + $r + '-$H' + $r + ')/$H' + $r + ',NA())'
      $wsX.Cells.Item($r, 13) = $b.S;   $wsX.Cells.Item($r, 14) = $b.E
      $wsX.Cells.Item($r, 15) = $b.PS;  $wsX.Cells.Item($r, 16) = $b.PE
      $wsX.Cells.Item($r, 17) = $b.TS;  $wsX.Cells.Item($r, 18) = $b.TE
    }
    $last = $hdrRow + $n
    $wsX.Range($wsX.Cells.Item($hdrRow + 1, 7), $wsX.Cells.Item($last, 8)).NumberFormat = '#,##0'
    $wsX.Range($wsX.Cells.Item($hdrRow + 1, 9), $wsX.Cells.Item($last, 10)).NumberFormat = '0.0%'
    $wsX.Range($wsX.Cells.Item($hdrRow + 1, 11), $wsX.Cells.Item($last, 11)).NumberFormat = '#,##0'
    $wsX.Range($wsX.Cells.Item($hdrRow + 1, 13), $wsX.Cells.Item($last, 18)).NumberFormat = 'yyyy-mm-dd'
    $script:BLK[$label] = @{ Hdr = $hdrRow; First = $hdrRow + 1; Last = $last }
    $null = $log.Add("period[$label] 数据区 R$($hdrRow):R$last 列 G:K")
  }
  function Bkt($name, $s, $e, $ps, $pe, $ts, $te) {
    @{ Name = $name; S = $s.ToOADate(); E = $e.ToOADate(); PS = $ps.ToOADate(); PE = $pe.ToOADate(); TS = $ts.ToOADate(); TE = $te.ToOADate() }
  }
  $dayB = New-Object Collections.ArrayList
  $src = $dateList; if ($src.Count -gt [int]$map.limits.trend_days) { $src = @($src | Select-Object -Last ([int]$map.limits.trend_days)) }
  foreach ($ds in $src) {
    try { $c1 = [DateTime]::ParseExact($ds, 'yyyy-MM-dd', $null) } catch { continue }
    $null = $dayB.Add((Bkt $ds $c1 $c1 $c1.AddDays(-1) $c1.AddDays(-1) $c1.AddYears(-1) $c1.AddYears(-1)))
  }
  if ($dayB.Count -lt 2) {
    $dayB = New-Object Collections.ArrayList
    $s0 = (Get-Date).Date.AddDays(-13)
    for ($i = 0; $i -lt 14; $i++) { $c2 = $s0.AddDays($i); $null = $dayB.Add((Bkt $c2.ToString('yyyy-MM-dd') $c2 $c2 $c2.AddDays(-1) $c2.AddDays(-1) $c2.AddYears(-1) $c2.AddYears(-1))) }
  }
  $wkB = New-Object Collections.ArrayList
  $wkStart = @{}
  foreach ($ds in $dateList) {
    try { $c3 = [DateTime]::ParseExact($ds, 'yyyy-MM-dd', $null) } catch { continue }
    $wsat = $c3; if ($c3.DayOfWeek -ne 'Sunday') { $wsat = $c3.AddDays(-1 * [int]$c3.DayOfWeek) }
    $k = $wsat.ToString('yyyy-MM-dd'); if (-not $wkStart.ContainsKey($k)) { $wkStart[$k] = $wsat }
  }
  foreach ($k in ($wkStart.Keys | Sort-Object)) {
    $a1 = $wkStart[$k]; $b1 = $a1.AddDays(6)
    $null = $wkB.Add((Bkt ($a1.ToString('MM-dd') + ' 周') $a1 $b1 $a1.AddDays(-7) $b1.AddDays(-7) $a1.AddDays(-364) $b1.AddDays(-364)))
  }
  $monB = New-Object Collections.ArrayList
  $mk2 = New-Object Collections.ArrayList
  foreach ($ds in $dateList) { $k = $ds.Substring(0, 7); if (-not ($mk2 -contains $k)) { $null = $mk2.Add($k) } }
  if ($mk2.Count -eq 0) { $s4 = (Get-Date).Date.AddMonths(-11); for ($i = 0; $i -lt 12; $i++) { $null = $mk2.Add($s4.AddMonths($i).ToString('yyyy-MM')) } }
  foreach ($k in $mk2) {
    try { $m1 = [DateTime]::ParseExact($k + '-01', 'yyyy-MM-dd', $null) } catch { continue }
    $mEnd2 = $m1.AddMonths(1).AddDays(-1); $pS = $m1.AddMonths(-1); $pE = $m1.AddDays(-1)
    $yS = $m1.AddYears(-1); $yE = $m1.AddYears(-1).AddMonths(1).AddDays(-1)
    $null = $monB.Add((Bkt $k $m1 $mEnd2 $pS $pE $yS $yE))
  }
  $yrB = New-Object Collections.ArrayList
  $yk2 = @{}
  foreach ($ds in $dateList) { $yk2[$ds.Substring(0, 4)] = $true }
  if ($yk2.Count -eq 0) { $yk2[(Get-Date).ToString('yyyy')] = $true; $yk2[(Get-Date).AddYears(-1).ToString('yyyy')] = $true }
  foreach ($k in ($yk2.Keys | Sort-Object)) {
    try { $y1 = [DateTime]::ParseExact($k + '-01-01', 'yyyy-MM-dd', $null) } catch { continue }
    $yE = $y1.AddYears(1).AddDays(-1)
    $null = $yrB.Add((Bkt ($k + ' 年') $y1 $yE $y1.AddYears(-1) $y1.AddYears(-1).AddYears(1).AddDays(-1) $y1.AddYears(-2) $y1.AddYears(-2).AddYears(1).AddDays(-1)))
  }
  $BLK = @{}
  $null = Build-Period 3 "日" $dayB;  $pDay = $BLK["日"]
  $null = Build-Period ($pDay.Last + 3) "周" $wkB; $pWeek = $BLK["周"]
  $null = Build-Period ($pWeek.Last + 3) "月" $monB; $pMon = $BLK["月"]
  $null = Build-Period ($pMon.Last + 3) "年" $yrB;  $pYear = $BLK["年"]
  try { $wsX.Activate(); $wsX.Columns.Item('M:R').Hidden = $true } catch { $null = $log.Add("隐藏辅助日期列失败（不影响计算）：$($_.Exception.Message)") }
  try { $null = $wsX.Columns.Item(6).AutoFit() } catch { }

  # ---- 板块四：目标达成率（T 列起）
  $wsX.Cells.Item(3, 20) = '四、目标达成率'; $wsX.Cells.Item(3, 20).Font.Bold = $true
  $null = Head $wsX 4 20 @('期间', '目标', '实际', '达成率')
  $mStartF = '">="&目标预算!$H$2'
  $mEndF = '"<="&目标预算!$H$2+32-DAY(目标预算!$H$2)'
  $yStartF = '">="&DATE(YEAR(目标预算!$H$2),1,1)'
  $wsX.Cells.Item(5, 20) = '本月'; $wsX.Cells.Item(5, 21).Formula = '=目标预算!$H$3'
  $wsX.Cells.Item(5, 22).Formula = SUMIFS_D @($cRep, $trRq, $cMet, (CQ $M.gmv.met), $cRole, '"本期值"', $cDate, $mStartF, $cDate, $mEndF)
  $wsX.Cells.Item(6, 20) = '本年'; $wsX.Cells.Item(6, 21).Formula = '=目标预算!$H$4'
  $wsX.Cells.Item(6, 22).Formula = SUMIFS_D @($cRep, $trRq, $cMet, (CQ $M.gmv.met), $cRole, '"本期值"', $cDate, $yStartF, $cDate, $mEndF)
  $wsX.Range('U5:V6').NumberFormat = '#,##0'
  for ($r = 5; $r -le 6; $r++) { $wsX.Cells.Item($r, 23).Formula = '=IFERROR($V' + $r + '/$U' + $r + ',NA())'; $wsX.Cells.Item($r, 23).NumberFormat = '0.0%' }

  # ---- 板块五：广告 ROI / 费比
  $wsX.Cells.Item(9, 20) = '五、广告ROI与费比'; $wsX.Cells.Item(9, 20).Font.Bold = $true
  $null = Head $wsX 10 20 @('期间', 'ROI目标', '实际ROI', '费比')
  $wsX.Cells.Item(11, 20) = '本月'
  $wsX.Cells.Item(11, 21).Formula = '=目标预算!$H$6'
  $wsX.Cells.Item(11, 22).Formula = '=IFERROR(目标预算!$B$13/目标预算!$B$12,NA())'
  $wsX.Cells.Item(11, 23).Formula = '=IFERROR(目标预算!$B$12/目标预算!$B$14,NA())'
  $wsX.Cells.Item(11, 21).NumberFormat = '0.00'
  $wsX.Cells.Item(11, 22).NumberFormat = '0.00'
  $wsX.Cells.Item(11, 23).NumberFormat = '0.0%'
  $wsX.Cells.Item(12, 20) = 'ROI达成'; $wsX.Cells.Item(12, 21).Formula = '=IFERROR($V$11/$U$11,NA())'; $wsX.Cells.Item(12, 21).NumberFormat = '0.0%'
  $wsX.Cells.Item(12, 22) = '费比上限'; $wsX.Cells.Item(12, 23).Formula = '=目标预算!$H$7'; $wsX.Cells.Item(12, 23).NumberFormat = '0.0%'

  # ---- 板块七：商品销售表（T15 起）
  $spuCap = 12
  if ($spus.Count -gt 0) { $spuCap = [Math]::Max($spus.Count, 8); if ($spuCap -gt [int]$map.limits.products) { $spuCap = [int]$map.limits.products } }
  $wsX.Cells.Item(15, 20) = '七、商品-渠道 销售额/件数/下单人数'; $wsX.Cells.Item(15, 20).Font.Bold = $true
  $null = Head $wsX 16 20 @('SPU名称', '销售额', '销售件数', '下单人数', '占比')
  $spuFirst = 17; $spuEnd = 16 + $spuCap
  $pcR = CQ $M.pc_gmv.rep
  for ($i = 0; $i -lt $spuCap; $i++) {
    $r = $spuFirst + $i
    if ($i -lt $spus.Count) { $wsX.Cells.Item($r, 20) = $spus[$i] }
    $pairs0 = @($cRep, $pcR, $cRole, '"本期值"', $cSpuN, ('$T' + $r))
    $wsX.Cells.Item($r, 21).Formula = ('=SUMIFS(' + $cVal + ',' + (($pairs0 + @($cMet, (CQ $M.pc_gmv.met))) -join ',') + ')')
    $wsX.Cells.Item($r, 22).Formula = ('=SUMIFS(' + $cVal + ',' + (($pairs0 + @($cMet, (CQ $M.pc_qty.met))) -join ',') + ')')
    $wsX.Cells.Item($r, 23).Formula = ('=SUMIFS(' + $cVal + ',' + (($pairs0 + @($cMet, (CQ $M.pc_buyers.met))) -join ',') + ')')
    $wsX.Cells.Item($r, 24).Formula = '=IFERROR($U' + $r + '/SUM($U$' + $spuFirst + ':$U$' + $spuEnd + '),NA())'
  }
  $wsX.Range($wsX.Cells.Item($spuFirst, 21), $wsX.Cells.Item($spuEnd, 23)).NumberFormat = '#,##0'
  $wsX.Range($wsX.Cells.Item($spuFirst, 24), $wsX.Cells.Item($spuEnd, 24)).NumberFormat = '0.0%'
  $null = $wsX.Columns.Item(20).AutoFit()

  # ======================= 商品渠道（下拉联动） =======================
  $wsP.Cells.Item(1, 1) = '七(下)、单品-各渠道贡献：在 D2 下拉选择 SPU，下方表格与饼图随之更新'
  $wsP.Cells.Item(1, 1).Font.Bold = $true; $wsP.Cells.Item(1, 1).Font.Size = 13
  $wsP.Cells.Item(2, 1) = '选择商品'; $wsP.Cells.Item(2, 1).Font.Bold = $true
  try {
    $dvSrc = "='图表数据'!" + '$T$' + $spuFirst + ':$T$' + $spuEnd
    $wsP.Range('D2').Validation.Delete()
    $null = $wsP.Range('D2').Validation.Add(3, 1, 1, $dvSrc)
    $wsP.Range('D2').Validation.InCellDropdown = $true
  } catch { $null = $log.Add("D2 下拉验证失败：$($_.Exception.Message)") }
  if ($spus.Count -gt 0) { $wsP.Range('D2') = $spus[0] }
  $wsP.Range('D2').Interior.Pattern = 1; $wsP.Range('D2').Interior.Color = $CLR_INPUT
  $wsP.Range('D2').Font.Bold = $true
  $wsP.Columns.Item(1).ColumnWidth = 16; $wsP.Columns.Item(4).ColumnWidth = 26
  $null = Head $wsP 4 1 @('渠道', '销售额', '销售件数', '下单人数', '占该品比')
  $pcFirst = 5; $pcEnd = 4 + $chCap
  for ($i = 0; $i -lt $chCap; $i++) {
    $r = $pcFirst + $i
    if ($i -lt $channels.Count) { $wsP.Cells.Item($r, 1) = $channels[$i] }
    $base = @($cRep, $pcR, $cRole, '"本期值"', $cSpuN, '$D$2', $cChan, ('$A' + $r))
    $wsP.Cells.Item($r, 2).Formula = ('=SUMIFS(' + $cVal + ',' + (($base + @($cMet, (CQ $M.pc_gmv.met))) -join ',') + ')')
    $wsP.Cells.Item($r, 3).Formula = ('=SUMIFS(' + $cVal + ',' + (($base + @($cMet, (CQ $M.pc_qty.met))) -join ',') + ')')
    $wsP.Cells.Item($r, 4).Formula = ('=SUMIFS(' + $cVal + ',' + (($base + @($cMet, (CQ $M.pc_buyers.met))) -join ',') + ')')
    $wsP.Cells.Item($r, 5).Formula = '=IFERROR($B' + $r + '/SUM($B$' + $pcFirst + ':$B$' + $pcEnd + '),NA())'
  }
  $wsP.Range($wsP.Cells.Item($pcFirst, 2), $wsP.Cells.Item($pcEnd, 4)).NumberFormat = '#,##0'
  $wsP.Range($wsP.Cells.Item($pcFirst, 5), $wsP.Cells.Item($pcEnd, 5)).NumberFormat = '0.0%'
  $null = $wsP.Columns.Item(1).AutoFit()
  $wsP.Cells.Item($pcEnd + 2, 1) = '说明：需导入「商品×渠道」交叉明细后本表才有数；未导入前为 0，属预期。'
  $wsP.Cells.Item($pcEnd + 2, 1).Font.Italic = $true; $wsP.Cells.Item($pcEnd + 2, 1).Font.Color = RGB 120 120 120

  # ============================== 图表 ==============================
  $wsB.Activate()
  try { $wsB.ChartObjects().Delete() } catch { }
  try { $wsP.ChartObjects().Delete() } catch { }
  $wsB.Cells.Item(1, 1) = '经营看板'
  $wsB.Cells.Item(1, 1).Font.Size = 18; $wsB.Cells.Item(1, 1).Font.Bold = $true
  $wsB.Cells.Item(2, 1) = $(if ($Demo) { '演示模式：数值为随机演示数据，不代表真实经营结果' } else { '数据来源：明细数据（SUMIFS 活公式）+ 目标预算（可编辑）' })
  $wsB.Cells.Item(2, 1).Font.Italic = $true
  $wsB.Cells.Item(2, 1).Font.Color = $(if ($Demo) { (RGB 255 0 0) } else { (RGB 120 120 120) })

  function New-Ch([string]$nm, [int]$l, [int]$t, [int]$w, [int]$h) {
    $co = $wsB.ChartObjects().Add($l, $t, $w, $h); $co.Name = $nm
    return $co.Chart
  }
  function Clean($ch) { try { $ch.ChartArea.Format.Line.Visible = 0 } catch { }; try { $ch.PlotArea.Format.Fill.Visible = 0 } catch { } }
  function Bind-Single($ch, $catRng, $valRng, [string]$title) {
    # 只绑一个数值列 + 显式类目，避免把类目列当成第二条系列
    $null = $ch.SetSourceData($valRng)
    $ch.PlotBy = 2
    try { $ch.SeriesCollection(1).XValues = $catRng } catch { }
    $ch.HasTitle = $true; $ch.ChartTitle.Text = $title
  }

  # 一、全渠道GMV 柱形图
  try {
    $ch = New-Ch 'c1' 20 90 470 300
    $ch.ChartType = 51
    Bind-Single $ch $wsX.Range("A$chFirst`:A$chEnd") $wsX.Range("B$chFirst`:B$chEnd") '一、全渠道GMV'
    try { $ch.SeriesCollection(1).Format.Fill.ForeColor.RGB = $CLR_ACTUAL } catch { }
    try { $ch.SeriesCollection(1).HasDataLabels = $true; $ch.SeriesCollection(1).DataLabels.NumberFormat = '#,##0' } catch { }
    try { $ch.Axes(2).HasTitle = $true; $ch.Axes(2).AxisTitle.Text = '渠道'; $ch.Axes(1).HasTitle = $true; $ch.Axes(1).AxisTitle.Text = '金额' } catch { }
    try { $ch.Legend.Delete() } catch { }
    Clean $ch
    $chartStatus['一 全渠道GMV(柱形)'] = 'OK'
  } catch { $chartStatus['一 全渠道GMV(柱形)'] = 'FAIL ' + $_.Exception.Message }

  # 二、渠道贡献 饼图
  try {
    $ch = New-Ch 'c2' 510 90 430 300
    $ch.ChartType = 5
    Bind-Single $ch $wsX.Range("A$chFirst`:A$chEnd") $wsX.Range("B$chFirst`:B$chEnd") '二、各渠道销售额占比'
    try {
      $n = [int]$ch.SeriesCollection(1).Points.Count
      for ($i = 1; $i -le $n; $i++) { $ch.SeriesCollection(1).Points($i).Format.Fill.ForeColor.RGB = $C_PIE[($i - 1) % $C_PIE.Length] }
    } catch { }
    try {
      $dl = $ch.SeriesCollection(1).DataLabels
      $dl.ShowPercentage = $true; try { $dl.ShowCategoryName = $false } catch { }; try { $dl.Font.Size = 9 } catch { }
      $ch.HasLegend = $true
    } catch { }
    Clean $ch
    $chartStatus['二 渠道贡献(饼图)'] = 'OK'
  } catch { $chartStatus['二 渠道贡献(饼图)'] = 'FAIL ' + $_.Exception.Message }

  # 三、日/周/月/年 同环比：双柱（不同色）+ 同比折线（次轴）
  function Combo-Trend([string]$nm, $blk, [int]$l, [int]$t, [string]$title) {
    if ($blk.Last -le $blk.Hdr) { return '空区间（无数据且无占位）' }
    $ch = New-Ch $nm $l $t 470 250
    $ch.ChartType = 51
    # 柱系列只用纯数字列 G/H；含文本的列会被 SetSourceData 整列丢弃。
    # 同比% 通过 NewSeries()（PowerShell 必须带括号）显式追加为次轴折线。
    $srcRng = $wsX.Range($wsX.Cells.Item($blk.Hdr, 7), $wsX.Cells.Item($blk.Last, 8))
    $null = $ch.SetSourceData($srcRng, 2)
    $catRng = $wsX.Range($wsX.Cells.Item($blk.First, 6), $wsX.Cells.Item($blk.Last, 6))
    $nser = [int]$ch.SeriesCollection().Count
    for ($s = 1; $s -le $nser; $s++) { try { $ch.SeriesCollection($s).XValues = $catRng } catch { } }
    try { $ch.SeriesCollection(1).Name = '本期销售额' } catch { }
    try { $ch.SeriesCollection(2).Name = '上期销售额' } catch { }
    try { $ch.SeriesCollection(1).Format.Fill.ForeColor.RGB = $CLR_ACTUAL } catch { }
    try { $ch.SeriesCollection(2).Format.Fill.ForeColor.RGB = $CLR_PREV } catch { }
    try {
      $s3 = $ch.SeriesCollection().NewSeries()
      $s3.Values = $wsX.Range($wsX.Cells.Item($blk.First, 9), $wsX.Cells.Item($blk.Last, 9))
      $s3.XValues = $catRng
      $s3.Name = '同比%'
      $s3.ChartType = 4
      $s3.AxisGroup = 2
      $s3.Format.Line.ForeColor.RGB = $CLR_LINE
      $s3.Format.Line.Weight = 2.25
      $s3.MarkerStyle = 2; $s3.MarkerSize = 5
      try { $s3.HasDataLabels = $true; $s3.DataLabels.NumberFormat = '0.0%'; $s3.DataLabels.Font.Size = 8 } catch { }
    } catch { return "折线系列追加失败: $($_.Exception.Message)" }
    $ch.HasTitle = $true; $ch.ChartTitle.Text = $title
    try { $ch.HasLegend = $true } catch { }
    Clean $ch
    return 'OK'
  }
  try { $chartStatus['三 日 同环比(柱+折线)'] = Combo-Trend 'c3d' $pDay 20 410 '三、日销售额 本期vs上期 + 同比折线' } catch { $chartStatus['三 日 同环比(柱+折线)'] = 'FAIL ' + $_.Exception.Message }
  try { $chartStatus['三 月 同环比(柱+折线)'] = Combo-Trend 'c3m' $pMon 510 410 '三、月销售额 本期vs上期 + 同比折线' } catch { $chartStatus['三 月 同环比(柱+折线)'] = 'FAIL ' + $_.Exception.Message }
  try { $chartStatus['三 年 同环比(柱+折线)'] = Combo-Trend 'c3y' $pYear 20 1000 '三、年销售额 本期vs上期 + 同比' } catch { $chartStatus['三 年 同环比(柱+折线)'] = 'FAIL ' + $_.Exception.Message }

  # 四、目标达成率：目标空心 + 实际实心 + 完成率折线标注
  try {
    $ch = New-Ch 'c4' 510 690 430 290
    $ch.ChartType = 51
    $srcRng = $wsX.Range($wsX.Cells.Item(4, 21), $wsX.Cells.Item(6, 22))
    $null = $ch.SetSourceData($srcRng, 2)
    for ($s = 1; $s -le [int]$ch.SeriesCollection().Count; $s++) { try { $ch.SeriesCollection($s).XValues = $wsX.Range('T5:T6') } catch { } }
    try { $ch.SeriesCollection(1).Name = '目标' } catch { }
    try { $ch.SeriesCollection(2).Name = '实际' } catch { }
    try {
      $ch.SeriesCollection(1).Format.Fill.Visible = 0
      $ch.SeriesCollection(1).Format.Line.Visible = -1
      $ch.SeriesCollection(1).Format.Line.ForeColor.RGB = $CLR_TARGET
      $ch.SeriesCollection(1).Format.Line.Weight = 2.25
    } catch { $null = $log.Add("c4 目标空心样式失败：$($_.Exception.Message)") }
    try { $ch.SeriesCollection(2).Format.Fill.ForeColor.RGB = $CLR_ACTUAL } catch { }
    try { $ch.SeriesCollection(2).GapWidth = 80; $ch.SeriesCollection(2).Overlap = -12 } catch { }
    try {
      $s3 = $ch.SeriesCollection().NewSeries()
      $s3.Values = $wsX.Range('W5:W6'); $s3.XValues = $wsX.Range('T5:T6'); $s3.Name = '达成率'
      $s3.ChartType = 4; $s3.AxisGroup = 2
      $s3.Format.Line.ForeColor.RGB = $CLR_LINE
      $s3.MarkerStyle = 2; $s3.MarkerSize = 7
      $s3.HasDataLabels = $true
      $s3.DataLabels.NumberFormat = '0.0%'
      try { $s3.DataLabels.Position = 1 } catch { }
    } catch { $null = $log.Add("c4 达成率折线失败：$($_.Exception.Message)") }
    $ch.HasTitle = $true; $ch.ChartTitle.Text = '四、月度/年度目标达成率（目标空心·实际实心·折线为完成率）'
    Clean $ch
    $chartStatus['四 目标达成率'] = 'OK'
  } catch { $chartStatus['四 目标达成率'] = 'FAIL ' + $_.Exception.Message }

  # 五、广告 ROI：目标空心 + 实际实心 + 费比数值
  try {
    $ch = New-Ch 'c5' 980 90 430 290
    $ch.ChartType = 51
    $srcRng = $wsX.Range($wsX.Cells.Item(10, 21), $wsX.Cells.Item(11, 22))
    $null = $ch.SetSourceData($srcRng, 2)
    for ($s = 1; $s -le [int]$ch.SeriesCollection().Count; $s++) { try { $ch.SeriesCollection($s).XValues = $wsX.Range('T11:T11') } catch { } }
    try { $ch.SeriesCollection(1).Name = 'ROI目标' } catch { }
    try { $ch.SeriesCollection(2).Name = '实际ROI' } catch { }
    try {
      $ch.SeriesCollection(1).Format.Fill.Visible = 0
      $ch.SeriesCollection(1).Format.Line.Visible = -1
      $ch.SeriesCollection(1).Format.Line.ForeColor.RGB = $CLR_TARGET
      $ch.SeriesCollection(1).Format.Line.Weight = 2.25
    } catch { }
    try {
      $ch.SeriesCollection(2).Format.Fill.ForeColor.RGB = $CLR_ACTUAL
      $ch.SeriesCollection(2).HasDataLabels = $true
      $ch.SeriesCollection(2).DataLabels.NumberFormat = '0.00'
    } catch { }
    try { $ch.SeriesCollection(2).GapWidth = 80; $ch.SeriesCollection(2).Overlap = -12 } catch { }
    try {
      $s3 = $ch.SeriesCollection().NewSeries()
      $s3.Values = $wsX.Range('W11:W11'); $s3.XValues = $wsX.Range('T11:T11'); $s3.Name = '费比'
      $s3.ChartType = 4; $s3.AxisGroup = 2
      $s3.Format.Line.ForeColor.RGB = $CLR_LINE
      $s3.MarkerStyle = 2; $s3.MarkerSize = 7
      $s3.HasDataLabels = $true
      $s3.DataLabels.NumberFormat = '0.0%'
    } catch { $null = $log.Add("c5 费比折线失败：$($_.Exception.Message)") }
    $ch.HasTitle = $true; $ch.ChartTitle.Text = '五、广告ROI：目标(空心) vs 实际(实心)，折线为费比'
    Clean $ch
    # 费比用单元格直读（Excel 2007 文本框不能承载公式，改为看板单元格）
    $wsB.Cells.Item(18, 22) = '费比'; $wsB.Cells.Item(18, 22).Font.Bold = $true
    $wsB.Cells.Item(18, 23).Formula = '=IFERROR(图表数据!W11,"-")'; $wsB.Cells.Item(18, 23).NumberFormat = '0.0%'
    $wsB.Cells.Item(19, 22) = '费比上限'; $wsB.Cells.Item(19, 22).Font.Bold = $true
    $wsB.Cells.Item(19, 23).Formula = '=目标预算!H7'; $wsB.Cells.Item(19, 23).NumberFormat = '0.0%'
    $chartStatus['五 广告ROI(空心+实心+费比)'] = 'OK'
  } catch { $chartStatus['五 广告ROI(空心+实心+费比)'] = 'FAIL ' + $_.Exception.Message }

  # 六、预算使用率/全月进度 —— 表格（引用目标预算表）
  try {
    $wsB.Cells.Item(4, 10) = '六、预算使用率与全月使用进度（表格）'
    $wsB.Cells.Item(4, 10).Font.Bold = $true; $wsB.Cells.Item(4, 10).Font.Size = 12
    $null = Head $wsB 5 10 @('指标', '数值', '口径说明')
    $want = @('当月广告花费', '当月销售额', '月度广告预算', '当前使用率', '时间进度', '全月使用进度', '实际ROI', 'ROI达成', '花费比例(费比)', '费比上限', '预算预警')
    $rm = @{}
    for ($r = 12; $r -le 24; $r++) { $t1 = ([string]$wsT.Cells.Item($r, 1).Value2); if ($t1) { $rm[$t1] = $r } }
    $k = 6
    foreach ($w in $want) {
      if (-not $rm.ContainsKey($w)) { continue }
      $rr = $rm[$w]
      $wsB.Cells.Item($k, 10) = $w
      $wsB.Cells.Item($k, 11).Formula = '=目标预算!$B$' + $rr
      try { $wsB.Cells.Item($k, 11).NumberFormat = [string]$wsT.Cells.Item($rr, 2).NumberFormat } catch { }
      $wsB.Cells.Item($k, 12) = [string]$wsT.Cells.Item($rr, 3).Value2
      $wsB.Cells.Item($k, 12).Font.Color = RGB 120 120 120
      $k++
    }
    $wsB.Columns.Item(10).ColumnWidth = 18
    $wsB.Columns.Item(11).ColumnWidth = 14
    $wsB.Columns.Item(12).ColumnWidth = 40
    $chartStatus['六 预算使用率/全月进度(表格)'] = "OK（$($k - 6) 行）"
  } catch { $chartStatus['六 预算使用率/全月进度(表格)'] = 'FAIL ' + $_.Exception.Message }

  # 七、公司商品销售饼图（看板）+ 单品渠道饼图（商品渠道 sheet）
  try {
    $ch = New-Ch 'c7a' 980 410 430 300
    $ch.ChartType = 5
    Bind-Single $ch $wsX.Range($wsX.Cells.Item($spuFirst, 20), $wsX.Cells.Item($spuEnd, 20)) `
                     $wsX.Range($wsX.Cells.Item($spuFirst, 21), $wsX.Cells.Item($spuEnd, 21)) `
                     '七、公司商品销售额构成'
    try { $ch.SeriesCollection(1).DataLabels.ShowPercentage = $true } catch { }
    Clean $ch
    $chartStatus['七 商品销售构成(饼)'] = 'OK'
  } catch { $chartStatus['七 商品销售构成(饼)'] = 'FAIL ' + $_.Exception.Message }

  try {
    $co = $wsP.ChartObjects().Add(340, 130, 430, 300); $co.Name = 'c7b'
    $ch = $co.Chart
    $ch.ChartType = 5
    $null = $ch.SetSourceData($wsP.Range($wsP.Cells.Item($pcFirst, 2), $wsP.Cells.Item($pcEnd, 2)))
    $ch.PlotBy = 2
    try { $ch.SeriesCollection(1).XValues = $wsP.Range($wsP.Cells.Item($pcFirst, 1), $wsP.Cells.Item($pcEnd, 1)) } catch { }
    try {
      $n = [int]$ch.SeriesCollection(1).Points.Count
      for ($i = 1; $i -le $n; $i++) { $ch.SeriesCollection(1).Points($i).Format.Fill.ForeColor.RGB = $C_PIE[($i - 1) % $C_PIE.Length] }
    } catch { }
    try { $ch.SeriesCollection(1).DataLabels.ShowPercentage = $true } catch { }
    $ch.HasTitle = $true; $ch.ChartTitle.Text = '所选 SPU 的各渠道贡献占比（随 D2 联动）'
    Clean $ch
    $chartStatus['七 单品渠道贡献(联动饼图)'] = 'OK'
  } catch { $chartStatus['七 单品渠道贡献(联动饼图)'] = 'FAIL ' + $_.Exception.Message }

  # 看板提示：板块七下方的 SPU 明细表位置指引
  $wsB.Cells.Item(8, 1) = '商品/渠道明细见「图表数据」T15 起 与「商品渠道」sheet'
  $wsB.Cells.Item(8, 1).Font.Italic = $true; $wsB.Cells.Item(8, 1).Font.Color = RGB 120 120 120

  # ---- 演示数据落到 明细数据（图表公式引用整列，追加即生效）
  if ($Demo -and $demoSeed -and $demoSeed.Count -gt 0) {
    $nr = $demoSeed.Count
    $arr = New-Object 'object[,]' $nr, 13
    for ($i = 0; $i -lt $nr; $i++) {
      $rw = $demoSeed[$i]
      $arr[$i, 0] = '京东'; $arr[$i, 1] = '演示店铺'; $arr[$i, 2] = $rw[0]
      $arr[$i, 3] = '分天'
      $arr[$i, 4] = ([DateTime]::ParseExact([string]$rw[1], 'yyyy-MM-dd', $null)).ToOADate()
      $arr[$i, 5] = $rw[2]; $arr[$i, 6] = ''; $arr[$i, 7] = $rw[3]
      $dimp = ''
      if ($rw[2]) { $dimp = '一级渠道=' + $rw[2] }
      if ($rw[3]) { $dimp = $dimp + $(if ($dimp) { ' | ' } else { '' }) + 'SPU名称=' + $rw[3] }
      $arr[$i, 8] = $dimp; $arr[$i, 9] = $rw[4]; $arr[$i, 10] = '本期值'
      $arr[$i, 11] = [double]$rw[5]; $arr[$i, 12] = '演示数据(非真实)'
    }
    $tgt = $wsD.Range($wsD.Cells.Item($nDet + 1, 1), $wsD.Cells.Item($nDet + $nr, 13))
    $tgt.Value2 = $arr
    $wsD.Range($wsD.Cells.Item($nDet + 1, 5), $wsD.Cells.Item($nDet + $nr, 5)).NumberFormat = 'yyyy-mm-dd'
    $null = $log.Add("演示：向 明细数据 追加 $nr 行")
  }

  $xl.Calculate()
  $wsB.Activate()
  $xl.ScreenUpdating = $true
  $wb.Save()
  $wb.Close($false); $wb = $null

  $check = [ordered]@{
    output = $OutFile
    demo   = [bool]$Demo
    detail_rows_read = $rowsRead
    reports = $R
    unresolved = $missing
    channels = $channels.Count
    spus = $spus.Count
    dates = $dateList.Count
    period_tables = @{ day = $pDay; week = $pWeek; month = $pMon; year = $pYear }
    charts = $chartStatus
    log = @($log)
  }
  $json = $check | ConvertTo-Json -Depth 6
  [IO.File]::WriteAllText((Join-Path $scratch 'dashboard_check.json'), $json, (New-Object Text.UTF8Encoding $false))
  Write-Host $json
}
catch {
  $xl.ScreenUpdating = $true
  try {
    $dump = New-Object Collections.ArrayList
    $null = $dump.Add('FATAL: ' + $_.Exception.Message)
    $null = $dump.Add('STACK: ' + $_.ScriptStackTrace)
    foreach ($l in $log) { $null = $dump.Add($l) }
    [IO.File]::WriteAllLines((Join-Path $scratch 'dashboard_diag.txt'), $dump, (New-Object Text.UTF8Encoding $true))
  } catch { }
  throw
}
finally {
  if ($wb) { try { $wb.Close($false) } catch { } }
  $xl.Quit()
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl)
  [GC]::Collect(); [GC]::WaitForPendingFinalizers()
}
