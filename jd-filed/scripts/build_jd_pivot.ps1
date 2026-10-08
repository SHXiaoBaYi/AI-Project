<#
.SYNOPSIS
  目录级多源 Excel/CSV 透视流水线（京东商智日报场景，无 Python 依赖）。

.DESCRIPTION
  1) 扫描目录下的 .xlsx / .csv 源文件
  2) 按命名规则解析文件名 -> 店铺/品牌、报表类型、下载方式、时间粒度、统计区间、对比口径
  3) 读取表头与数据行 -> 列语义分类（维度 / 本期值 / 对比日 / 较对比日 / 同比）
  4) 归一化为「指标总览」元数据表 + 「明细数据」长表
  5) 输出单个工作簿：Summary / 指标总览 / 明细数据 / 透视表 / 时间趋势 / 商品维度 / 渠道维度 / 文件清单

.PARAMETER Dir
  源文件所在目录，同时是默认输出目录。

.PARAMETER Platform / ShopLabel / DateTag
  输出文件名 = {Platform}_{ShopLabel}_{DateTag}.xlsx，默认 京东_汇总_yy-MM-dd.xlsx

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File build_jd_pivot.ps1 -Dir D:\data -Platform 京东 -ShopLabel 奔养养

.NOTES
  依赖：本机 Excel + PowerShell COM（无需 Python/pandas）。
  源文件只有表头时，值列标注「待填充」，结构照常产出；放入真实数据后重跑即自动出数。
  同名旧产物会先备份到 <Dir>\_scratch\ 再覆盖，绝不删除用户文件。
#>
[CmdletBinding()]
param(
  [string] $Dir         = (Get-Location).Path,
  [string] $OutDir      = '',
  [string] $Platform    = '京东',
  [string] $ShopLabel   = '',
  [string] $DateTag     = (Get-Date -Format 'yy-MM-dd'),
  [int]    $MaxDataRows = 5000,
  [switch] $SkipCsv
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Dir)) { throw "目录不存在：$Dir" }
$scratch = Join-Path $Dir '_scratch'
if (-not (Test-Path -LiteralPath $scratch)) { $null = New-Item -ItemType Directory -Path $scratch }
$log = New-Object Collections.ArrayList

# ========================================================== 文件名规则 ==
#  含 '_' 的名称按 '_' 切段，否则按 '-' 切段（保留品牌/报表内部的连字符）。
#   · 6 位以上纯数字段                           -> 店铺ID
#   · 日期段 2026-09-28 / 2026.09.28 / 20260928  -> 统计起/止（紧随的 _00_00_00 段并入为时间戳）
#   · 关键词段 离线/实时/分天下载/汇总下载/包括对比时间/同比/环比 -> 元数据
#   · 其余段按原顺序拼接                          -> 报表类型
$KEYWORDS = @{
  '离线' = '下载方式'; '实时' = '下载方式'
  '分天下载' = '时间粒度'; '分天' = '时间粒度'; '汇总下载' = '时间粒度'; '汇总' = '时间粒度'
  '包括对比时间' = '对比口径'; '同比' = '对比口径'; '环比' = '对比口径'
}
$SHOP_RX = '^\d{6,}$'
$TIME_RX = '^\d{2}$'
$REPORT_HINTS = @('概况','概览','明细','来源','洞察','报表','营销','推广','计划','中心','管理','分析','场域','结构',
                  '指标','数据','排行','诊断','趋势','对比','订单','曝光','转化','流量','商品','交易','用户')

function Split-DateToken([string]$s) {
  # 只有语义合法的年月日才算日期；否则 8 位店铺ID（如 11623441）会被误读成 1162-34-41
  $y = 0; $m = 0; $d = 0
  if ($s -match '^(\d{4})[-.](\d{2})[-.](\d{2})$') { $y = [int]$matches[1]; $m = [int]$matches[2]; $d = [int]$matches[3] }
  elseif ($s -match '^(\d{4})(\d{2})(\d{2})$')     { $y = [int]$matches[1]; $m = [int]$matches[2]; $d = [int]$matches[3] }
  else { return $null }
  if ($y -lt 1990 -or $y -gt 2100) { return $null }
  if ($m -lt 1 -or $m -gt 12) { return $null }
  if ($d -lt 1 -or $d -gt 31) { return $null }
  return ('{0:d4}-{1:d2}-{2:d2}' -f $y, $m, $d)
}

function Parse-JdFileName([string]$leaf) {
  $base  = [IO.Path]::GetFileNameWithoutExtension($leaf)
  $hasUs = ($base.IndexOf('_') -ge 0)
  $toks  = if ($hasUs) { $base.Split('_') } else { $base.Split('-') }
  $join  = if ($hasUs) { '_' } else { '-' }

  $shop = ''; $mode = ''; $gran = ''
  $report = New-Object Collections.ArrayList
  $dates  = New-Object Collections.ArrayList
  $cmp    = New-Object Collections.ArrayList

  $i = 0
  while ($i -lt $toks.Count) {
    $t = $toks[$i]
    $d = Split-DateToken $t
    if ($d) {
      $hh = ''; $j = $i + 1; $nh = 0
      while ($j -lt $toks.Count -and $nh -lt 3 -and $toks[$j] -match $TIME_RX) {
        if ($nh) { $hh += ':' }
        $hh += $toks[$j]; $nh++; $j++
      }
      if ($hh) { $null = $dates.Add("$d $hh") } else { $null = $dates.Add($d) }
      $i = $j; continue
    }
    if ($KEYWORDS.ContainsKey($t)) {
      switch ($KEYWORDS[$t]) {
        '下载方式' { $mode = $t }
        '时间粒度' { if ($t -like '分天*') { $gran = '分天' } else { $gran = '汇总' } }
        '对比口径' { $null = $cmp.Add($t) }
      }
      $i++; continue
    }
    if ($shop -eq '' -and $i -eq 0) {
      # 首段：纯数字 = 店铺ID；不含任何「报表语义词」的品牌串 = 店铺/品牌
      if ($t -match $SHOP_RX) { $shop = $t; $i++; continue }
      $looksReport = $false
      foreach ($hint in $REPORT_HINTS) { if ($t.Contains($hint)) { $looksReport = $true; break } }
      if (-not $looksReport) { $shop = $t; $i++; continue }
    }
    $null = $report.Add($t); $i++
  }

  $df = ''; $dto = ''
  if ($dates.Count -ge 1) { $df = $dates[0] }
  if ($dates.Count -ge 2) { $dto = $dates[$dates.Count - 1] } else { $dto = $df }

  @{
    File        = $leaf
    Shop        = $shop
    Report      = ($report -join $join)
    Mode        = $(if ($mode) { $mode } else { '未标注' })
    Granularity = $(if ($gran) { $gran } else { '未标注' })
    Compare     = $(if ($cmp.Count) { $cmp -join '+' } else { '无' })
    DateFrom    = $df
    DateTo      = $dto
    ColCount    = 0
    RowCount    = 0
  }
}

# ==================================================== 列语义分类规则 ==
$DIM_NAMES = @('时间','日期','月份','统计月份','目标月份','周','季度','年','SPU','SPU名称','SPU编码','款号','货号','SKU','SKU名称',
               '商品名称','商品图片','商品ID','一级类目','二级类目','三级类目',
               '一级渠道','二级渠道','三级渠道','四级渠道','渠道','渠道名称','投放渠道',
               '计划类型','计划名称','商品计划名称','ID','状态','出价方式','创建时间','省份','城市')

function Classify-Column([string]$name, [hashtable]$allNames) {
  $n = $name.Trim()
  if ($n.Length -eq 0) { return @{ Base = ''; Role = '空列' } }
  if ($n.EndsWith('-较对比日')) { return @{ Base = $n.Substring(0, $n.Length - 5); Role = '较对比日' } }
  if ($n.EndsWith('-对比日'))   { return @{ Base = $n.Substring(0, $n.Length - 4); Role = '对比日' } }
  if ($n.EndsWith('同比') -and $n.Length -gt 2 -and $allNames.ContainsKey($n.Substring(0, $n.Length - 2))) {
    return @{ Base = $n.Substring(0, $n.Length - 2); Role = '同比' } }
  if ($DIM_NAMES -contains $n) { return @{ Base = $n; Role = '维度' } }
  return @{ Base = $n; Role = '本期值' }
}

function Guess-DataType([string]$base) {
  if ($base -match '(率$|占比|投产比|时长|单价|金额|成本|价值)') { return '小数' }
  if ($base -match '(数$|量$|件$|行$|客数|单量|曝光)')           { return '整数' }
  return '文本'
}

# ============================================================ 读取源 ==
function Read-CsvSource([string]$path) {
  foreach ($cp in @(936, 65001)) {
    try {
      $txt = [IO.File]::ReadAllText($path, [Text.Encoding]::GetEncoding($cp))
      if ($txt -notmatch '[\u4e00-\u9fa5]') { continue }
      $lines = [regex]::Matches($txt, '(?m)^(.+?)\r?$') | ForEach-Object { $_.Groups[1].Value }
      $kept = New-Object Collections.ArrayList
      foreach ($ln in $lines) { if ($ln.Trim() -ne '') { $null = $kept.Add($ln) } }
      if ($kept.Count -eq 0) { continue }
      $hdr = ($kept[0] -replace '^﻿', '').Split(',')
      $data = New-Object Collections.ArrayList
      for ($i = 1; $i -lt $kept.Count; $i++) { $null = $data.Add($kept[$i].Split(',')) }
      return @{ Headers = $hdr; Data = $data }
    } catch { }
  }
  return $null
}

# 先算出产物路径：用于把「本脚本自己生成的透析表」排除在源文件之外，避免二次摄取
if (-not $OutDir) { $OutDir = $Dir }
if (-not (Test-Path -LiteralPath $OutDir)) { $null = New-Item -ItemType Directory -Path $OutDir }
$outName = '{0}_{1}_{2}.xlsx' -f $Platform, $(if ($ShopLabel) { $ShopLabel } else { '汇总' }), $DateTag
$outPath = Join-Path $OutDir $outName
$ownSig  = '^{0}_.*_{1}' -f [regex]::Escape($Platform), [regex]::Escape($DateTag)

$files = @(Get-ChildItem -LiteralPath $Dir -File | Where-Object {
             $_.Extension -eq '.xlsx' -or (-not $SkipCsv -and $_.Extension -eq '.csv') }) |
          Where-Object { $_.Name -notlike '~$*' -and $_.FullName -ne $outPath -and $_.Name -notmatch $ownSig } |
          Sort-Object Name
if ($files.Count -eq 0) { throw "目录 $Dir 下没有可透视的 .xlsx/.csv 源文件（产物自身已被排除）" }

$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
$sources = New-Object Collections.ArrayList

try {
  foreach ($f in $files) {
    $meta    = Parse-JdFileName $f.Name
    # -ShopLabel 只决定输出文件名；「店铺/品牌」列始终按文件名自动识别，避免多店铺被抹平
    $headers = @(); $data = New-Object Collections.ArrayList

    if ($f.Extension -eq '.csv') {
      $c = Read-CsvSource $f.FullName
      if ($c) {
        $headers = @($c.Headers | ForEach-Object { "$_".Trim() })
        $data    = $c.Data
      }
    } else {
      $wb = $excel.Workbooks.Open($f.FullName, 0, $true)
      # 结构性自检：带「明细数据/指标总览」sheet 的就是本流水线自己的产物，无论文件名怎么改都不许再摄取
      $selfMade = $false
      foreach ($sh in $wb.Worksheets) { if ($sh.Name -eq '明细数据' -or $sh.Name -eq '指标总览') { $selfMade = $true; break } }
      if ($selfMade) { $wb.Close($false); $null = $log.Add("跳过本流水线自建产物：$($f.Name)"); continue }
      $ws = $wb.Worksheets.Item(1)
      $ur = $ws.UsedRange
      $rowsCnt = [int]$ur.Rows.Count; $colsCnt = [int]$ur.Columns.Count
      if ($colsCnt -gt 0) {
        $h = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item(1, $colsCnt)).Value2
        $headers = @(@($h) | ForEach-Object { "$_".Trim() })
      }
      if ($rowsCnt -gt 1) {
        $cap = [Math]::Min($rowsCnt - 1, $MaxDataRows)
        $a = $ws.Range($ws.Cells.Item(2, 1), $ws.Cells.Item(1 + $cap, $colsCnt)).Value2
        for ($r = 1; $r -le $cap; $r++) {
          $line = New-Object Collections.ArrayList
          for ($c = 1; $c -le $colsCnt; $c++) {
            $v = if ($colsCnt -eq 1 -and $rowsCnt -eq 2) { $a } else { $a.GetValue($r, $c) }
            $null = $line.Add($v)
          }
          $null = $data.Add($line)
        }
      }
      $wb.Close($false)
    }

    $meta.ColCount = $headers.Count
    $meta.RowCount = $data.Count
    $null = $sources.Add(@{ Meta = $meta; Headers = $headers; Data = $data })
    $null = $log.Add("读取 $($f.Name) -> 列 $($meta.ColCount) / 数据行 $($meta.RowCount) / 报表[$($meta.Report)]")
  }

  # ================================================== 归一化透视输入 ==
  $dtMeta = New-Object Data.DataTable
  foreach ($cn in @('平台','店铺/品牌','报表类型','时间粒度','统计区间','列序号','原始列名','基础指标名','列角色','是否度量','本期首行值','推断数据类型','来源文件')) {
    $null = $dtMeta.Columns.Add($cn, [string])
  }
  $dtDetail = New-Object Data.DataTable
  foreach ($cn in @('平台','店铺/品牌','报表类型','时间粒度','统计日期','渠道','SPU','SPU名称','维度组合','基础指标名','列角色','来源文件')) {
    $null = $dtDetail.Columns.Add($cn, [string])
  }
  # 数值必须是真数字列，否则下游 SUMIFS/SUMPRODUCT 图表全部得 0
  $colNum = $dtDetail.Columns.Add('数值', [double])
  $colNum.SetOrdinal(11)

  $metricNames  = @{}
  $dimNameSet   = @{}
  $dateSet      = @{}
  $reportMetric = @{}
  $skippedNonNumeric = 0

  foreach ($s in $sources) {
    $m = $s.Meta
    $allNames = @{}
    foreach ($h in $s.Headers) { if ($h) { $allNames[$h] = $true } }

    $cls = New-Object Collections.ArrayList
    foreach ($h in $s.Headers) { $null = $cls.Add((Classify-Column $h $allNames)) }

    $firstRow = $null
    if ($s.Data.Count -gt 0) { $firstRow = $s.Data[0] }

    for ($i = 0; $i -lt $s.Headers.Count; $i++) {
      $h = $s.Headers[$i]
      if (-not $h) { continue }
      $c = $cls[$i]
      if ($c.Role -eq '维度') { $dimNameSet[$c.Base] = $true }
      else {
        $metricNames[$c.Base] = $true
        if (-not $reportMetric.ContainsKey($m.Report)) { $reportMetric[$m.Report] = @{} }
        $reportMetric[$m.Report][$c.Base] = $true
      }
      $val = ''
      if ($null -ne $firstRow -and $i -lt $firstRow.Count -and -not [string]::IsNullOrWhiteSpace("$($firstRow[$i])")) { $val = "$($firstRow[$i])" }
      $null = $dtMeta.Rows.Add([object[]]@(
        $Platform,
        $(if ($m.Shop) { $m.Shop } else { '未标注' }),
        $m.Report, $m.Granularity, "$($m.DateFrom) ~ $($m.DateTo)",
        "$($i + 1)", $h, $c.Base, $c.Role,
        $(if ($c.Role -eq '维度') { '否' } else { '是' }),
        $(if ($val -ne '') { $val } else { '待填充' }),
        $(Guess-DataType $c.Base), $m.File
      ))
    }

    foreach ($dr in $s.Data) {
      $timeVal = ''
      $chan = ''; $spu = ''; $spuName = ''
      $dims = New-Object Collections.ArrayList
      $n = [Math]::Min($cls.Count, $dr.Count)
      for ($i = 0; $i -lt $n; $i++) {
        $c = $cls[$i]
        if ($c.Role -ne '维度') { continue }
        $dv = "$($dr[$i])"
        switch -Regex ($c.Base) {
          '^(时间|日期)$' { $timeVal = $dv; continue }
        }
        $null = $dims.Add("$($c.Base)=$dv")
        # 把看板要用的关键维度提升成独立列（SUMIFS 精确匹配，避免子串误命中）
        if ($c.Base -match '渠道$' -and -not $chan) { $chan = $dv }
        if ($c.Base -eq 'SPU')     { $spu = $dv }
        if ($c.Base -eq 'SPU名称') { $spuName = $dv }
      }
      if ($timeVal) { $dateSet[$timeVal] = $true }
      for ($i = 0; $i -lt $n; $i++) {
        $c = $cls[$i]
        if ($c.Role -eq '维度' -or $c.Role -eq '空列') { continue }
        $raw = ([string]$dr[$i]).Replace(',', '').Replace('￥','').Replace('¥','').Trim()
        $isPct = $false
        if ($raw -match '^(.+)%$') { $raw = $matches[1]; $isPct = $true }
        $dv = [double]0
        if (-not [double]::TryParse($raw, [ref]$dv)) { $skippedNonNumeric++; continue }
        # Excel 会把 "12.3%" 直接存成 0.123；PS 侧必须同样除以 100，否则率类指标差 100 倍
        if ($isPct) { $dv = $dv / 100 }
        $null = $dtDetail.Rows.Add([object[]]@(
          $Platform, $(if ($m.Shop) { $m.Shop } else { '未标注' }), $m.Report, $m.Granularity,
          $(if ($timeVal) { $timeVal } else { $m.DateFrom }), $chan, $spu, $spuName,
          ($dims -join ' | '), $c.Base, $c.Role, $dv, $m.File
        ))
      }
    }

    foreach ($d in @($m.DateFrom, $m.DateTo)) { if ($d -and $d -notmatch ':' -and -not $dateSet.ContainsKey($d)) { $dateSet[$d] = $true } }
  }

  $uniqDates = @($dateSet.Keys | Sort-Object)

  # ========================================================= 写出工作簿 ==
  if (Test-Path -LiteralPath $outPath) {
    $bak = Join-Path $scratch ('backup__' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '__' + $outName)
    Copy-Item -LiteralPath $outPath -Destination $bak -Force
    $null = $log.Add("同名旧产物已备份 -> $bak")
  }

  $outWb = $excel.Workbooks.Add()
  while ($outWb.Worksheets.Count -gt 1) { $outWb.Worksheets.Item($outWb.Worksheets.Count).Delete() }

  function Write-DataTable($ws, [Data.DataTable]$dt, [string]$note) {
    $nc = $dt.Columns.Count
    $nr = $dt.Rows.Count + 1
    $arr = New-Object 'object[,]' $nr, $nc
    for ($j = 0; $j -lt $nc; $j++) { $arr[0, $j] = $dt.Columns[$j].ColumnName }
    $i = 1
    foreach ($row in $dt.Rows) {
      for ($j = 0; $j -lt $nc; $j++) { $arr[$i, $j] = $row[$j] }
      $i++
    }
    $rng = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item($nr, $nc))
    $rng.Value2 = $arr
    $hr = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item(1, $nc))
    $hr.Font.Bold = $true
    $hr.Interior.Pattern = 1
    $hr.Interior.Color = 15917529
    $ws.Activate()
    try { $excel.ActiveWindow.SplitRow = 1; $excel.ActiveWindow.FreezePanes = $true } catch { }
    if ($dt.Rows.Count -gt 0) { $null = $hr.AutoFilter($nc) }
    $null = $ws.Columns.AutoFit()
    for ($j = 1; $j -le $nc; $j++) { if ($ws.Columns.Item($j).ColumnWidth -gt 55) { $ws.Columns.Item($j).ColumnWidth = 55 } }
    if ($note) {
      try {
        $cm = $ws.Cells.Item(1, ($nc + 2)).AddComment($note)
        $cm.Shape.Height = 120; $cm.Shape.Width = 340
        $cm.Shape.TextFrame.Characters().Font.Size = 9
      } catch { }
    }
    return $nr
  }
  function New-Sheet([string]$name, $after) {
    $ws = $script:outWb.Worksheets.Add([Type]::Missing, $after); $ws.Name = $name; return $ws
  }
  function Distinct-Column([Data.DataTable]$dt, [string]$col) {
    $seen = @{}; $out = New-Object Collections.ArrayList
    foreach ($row in $dt.Rows) { $v = [string]$row[$col]; if ($v -and -not $seen.ContainsKey($v)) { $seen[$v] = $true; $null = $out.Add($v) } }
    $arr = @($out | Sort-Object)
    return $arr
  }

  # --- 指标总览
  $wsMeta = $outWb.Worksheets.Item(1); $wsMeta.Name = '指标总览'
  $metaNr = Write-DataTable $wsMeta $dtMeta '本表由源文件表头自动解析生成。「本期首行值」=待填充 表示该源文件当前没有数据行。'

  # --- 明细数据
  $wsDetail = New-Sheet '明细数据' $wsMeta
  $null = Write-DataTable $wsDetail $dtDetail '长表结构：源文件放入数据行后重跑本脚本，此处即成为透视数据源。统计日期已被 Excel 转成日期序列，故显式设格式。'
  # 统计日期写成字符串会被 Excel 静默转成日期序列值（如 2026-09-28 -> 46293），必须补格式，否则用户看到裸数字
  $hasTime = $false
  foreach ($row in $dtDetail.Rows) { if (([string]$row['统计日期']) -match '\d:\d') { $hasTime = $true; break } }
  try {
    $wsDetail.Columns.Item('E').NumberFormat = $(if ($hasTime) { 'yyyy-mm-dd hh:mm' } else { 'yyyy-mm-dd' })
    $wsDetail.Columns.Item('L').NumberFormat = 'General'
  } catch { $null = $log.Add("明细数据列格式设置失败：$($_.Exception.Message)") }

  # --- 透视表
  $wsPivot = New-Sheet '透视表' $wsDetail
  $pivotOk = $false; $pivotNote = ''; $pivotStage = 'init'
  try {
    $srcRange = $wsMeta.Range($wsMeta.Cells.Item(1, 1), $wsMeta.Cells.Item($metaNr, $dtMeta.Columns.Count))
    $srcAddr  = $wsMeta.Name + '!' + $srcRange.Address($true, $true, 1, $false)
    $srcAddrX = $srcRange.Address($true, $true, 1, $true)
    $pivotStage = 'cache'
    try {
      $cache = $outWb.PivotCaches().Create(1, $srcRange)
    } catch {
      $cache = $outWb.PivotCaches().Create(1, $srcAddrX)
    }
    $pivotStage = 'createPT'
    $pt = $cache.CreatePivotTable($wsPivot.Range('A4'), '指标透析')
    $pivotStage = 'rowField'
    $pt.PivotFields('报表类型').Orientation = 1
    $pivotStage = 'colField'
    $pt.PivotFields('列角色').Orientation = 2
    $pivotStage = 'dataField'
    $null = $pt.AddDataField($pt.PivotFields('原始列名'), '列数', -4112)
    $null = $pt.AddDataField($pt.PivotFields('是否度量'), '度量列数', -4112)
    $pivotStage = 'format'
    try { $pt.HasGrandTotals = $true } catch { $null = $log.Add("HasGrandTotals 不受本机 Excel 支持，已跳过（不影响透视表）") }
    $wsPivot.Cells.Item(1, 1) = '活透视表：行=报表类型，列=列角色，值=列数计数。数据行落地后可把值字段换成「明细数据」的数值求和。'
    $wsPivot.Cells.Item(1, 1).Font.Bold = $true
    $pivotOk = $true; $pivotNote = "数据源 指标总览!$srcAddr"
  } catch {
    $pivotNote = "原生透视表创建失败[阶段 $pivotStage]：$($_.Exception.Message)"
    [IO.File]::WriteAllText((Join-Path $scratch 'pivot_err.txt'), ($_.Exception | Out-String) + "`n--- STACK ---`n" + $_.ScriptStackTrace, (New-Object Text.UTF8Encoding $true))
    try { while ($wsPivot.PivotTables().Count -gt 0) { $wsPivot.PivotTables().Item(1).TableRange2.Clear() } } catch { }
  }

  if (-not $pivotOk) {
    $roles = @('维度','本期值','对比日','较对比日','同比')
    $dtCross = New-Object Data.DataTable
    $null = $dtCross.Columns.Add('报表类型', [string])
    foreach ($r in $roles) { $null = $dtCross.Columns.Add($r, [string]) }
    $null = $dtCross.Columns.Add('合计列数', [string])
    foreach ($rep in (Distinct-Column $dtMeta '报表类型')) {
      $vals = New-Object Collections.ArrayList
      $null = $vals.Add($rep)
      foreach ($r in $roles) {
        $q = $rep.Replace("'", "''")
        $null = $vals.Add(("=COUNTIFS('指标总览'!`$C:`$C,`"{0}`",'指标总览'!`$I:`$I,`"{1}`")" -f $q, $r))
      }
      $sumRow = $dtCross.Rows.Count + 2
      $null = $vals.Add('=SUM(B' + $sumRow + ':F' + $sumRow + ')')
      $null = $dtCross.Rows.Add([object[]]@($vals))
    }
    $null = Write-DataTable $wsPivot $dtCross "降级透视表（COUNTIFS 活表，随「指标总览」自动重算）。原因：$pivotNote"
  }

  # --- 时间趋势
  $coreMetrics = @('成交金额','成交商品件数','成交客户数','成交单量','成交转化率','客单价','件单价','UV价值',
                   '店铺浏览量','店铺访客数','商品浏览量','商品访客数','加购客户数','加购商品件数','退款金额','下单金额')
  $metricList = @($metricNames.Keys | Where-Object { $coreMetrics -contains $_ } | Sort-Object)
  if ($metricList.Count -eq 0) { $metricList = @($metricNames.Keys | Sort-Object | Select-Object -First 20) }

  $dtTrend = New-Object Data.DataTable
  $null = $dtTrend.Columns.Add('日期', [string]); $null = $dtTrend.Columns.Add('粒度', [string])
  foreach ($mm in $metricList) { $null = $dtTrend.Columns.Add($mm, [string]) }
  foreach ($mm in $metricList) { $null = $dtTrend.Columns.Add("$mm 日环比", [string]) }
  foreach ($d in $uniqDates) {
    $vals = New-Object Collections.ArrayList
    $null = $vals.Add($d)
    if ($d -match '^\d{4}-\d{2}-\d{2}$') { $null = $vals.Add('日') } else { $null = $vals.Add('时点') }
    for ($k = 0; $k -lt ($metricList.Count * 2); $k++) { $null = $vals.Add('') }
    $null = $dtTrend.Rows.Add([object[]]@($vals))
  }
  $wsTrend = New-Sheet '时间趋势' $wsPivot
  $null = Write-DataTable $wsTrend $dtTrend '行=日期（优先取数据行，无数据时回退到文件名统计区间），列=核心指标 + 日环比。'

  # --- 维度 sheet 工厂
  function New-DimSheet([string]$name, [string]$reportLike, [string[]]$dims, [string]$note, $after) {
    $ws = New-Sheet $name $after
    $repName = ''
    foreach ($rep in (Distinct-Column $dtMeta '报表类型')) { if ($rep -like $reportLike) { $repName = $rep; break } }
    $mets = New-Object Collections.ArrayList
    $seen = @{}
    foreach ($row in $dtMeta.Rows) {
      if ([string]$row['报表类型'] -ne $repName) { continue }
      if ([string]$row['列角色'] -ne '本期值') { continue }
      $b = [string]$row['基础指标名']
      if ($b -and -not $seen.ContainsKey($b)) { $seen[$b] = $true; $null = $mets.Add($b) }
    }
    $dt = New-Object Data.DataTable
    foreach ($d in $dims) { $null = $dt.Columns.Add($d, [string]) }
    foreach ($mm in $mets) { $null = $dt.Columns.Add($mm, [string]) }
    # 真实聚合：统计日期 × 维度组合 × 指标 -> 求和（只取本期值）
    $agg = @{}; $rowKeys = @{}
    foreach ($row in $dtDetail.Rows) {
      if ([string]$row['报表类型'] -ne $repName) { continue }
      if ([string]$row['列角色'] -ne '本期值') { continue }
      $bdate  = [string]$row['统计日期']
      $cv     = [string]$row['维度组合']
      $metric = [string]$row['基础指标名']
      $num = [double]0
      $raw = ([string]$row['数值']).Replace(',', '').Trim()
      if (-not [double]::TryParse($raw, [ref]$num)) { continue }
      $rk = "$bdate`t$cv"
      $rowKeys[$rk] = $true
      $ak = "$rk`t$metric"
      if ($agg.ContainsKey($ak)) { $agg[$ak] = $agg[$ak] + $num } else { $agg[$ak] = $num }
    }
    $keys = @($rowKeys.Keys | Sort-Object)
    $lim = [Math]::Min($keys.Count, 3000)
    for ($i = 0; $i -lt $lim; $i++) {
      $parts = $keys[$i] -split "`t"
      $bdate = $parts[0]; $cv = ''
      if ($parts.Count -gt 1) { $cv = $parts[1] }
      $ph = @{}
      foreach ($p in ($cv -split ' \| ')) {
        $ix = $p.IndexOf('='); if ($ix -gt 0) { $ph[$p.Substring(0, $ix)] = $p.Substring($ix + 1) }
      }
      $vals = New-Object Collections.ArrayList
      foreach ($d in $dims) {
        if ($d -eq '日期' -or $d -eq '统计日期') { $null = $vals.Add($bdate); continue }
        if ($ph.ContainsKey($d)) { $null = $vals.Add($ph[$d]) } else { $null = $vals.Add('') }
      }
      foreach ($mm in $mets) {
        $ak = "$($keys[$i])`t$mm"
        if ($agg.ContainsKey($ak)) { $null = $vals.Add($agg[$ak]) } else { $null = $vals.Add('') }
      }
      $null = $dt.Rows.Add([object[]]@($vals))
    }
    if ($dt.Rows.Count -eq 0) {
      $vals = New-Object Collections.ArrayList
      foreach ($d in $dims) { $null = $vals.Add('') }
      for ($j = 0; $j -lt $mets.Count; $j++) { $null = $vals.Add('') }
      $null = $dt.Rows.Add([object[]]@($vals))
    }
    $head = $(if ($repName) { "来源报表：$repName；" } else { '目录中未找到匹配报表；' }) + $note
    $null = Write-DataTable $ws $dt $head
    return @{ sheet = $name; report = $repName; metrics = $mets.Count; rows = $dt.Rows.Count }
  }
  $lastWs = $wsTrend
  $prodInfo = New-DimSheet '商品维度' '商品明细*' @('日期','SPU','SPU名称','一级类目','二级类目','三级类目','货号') '维度取自「商品明细」，指标列自动展开。' $lastWs
  $chanInfo = New-DimSheet '渠道维度' '流量来源*' @('日期','一级渠道','二级渠道','三级渠道','四级渠道') '维度取自「流量来源-场域来源」，指标列自动展开。' $wsTrend

  # --- Summary
  $wsSum = New-Sheet 'Summary' $wsMeta
  try { $wsSum.Move(1) } catch { $null = $log.Add("Summary 前移失败：$($_.Exception.Message)") }
  $r = 1
  $wsSum.Cells.Item($r, 1) = "$Platform 商智数据透析表"
  $wsSum.Cells.Item($r, 1).Font.Size = 16
  $wsSum.Cells.Item($r, 1).Font.Bold = $true
  $r += 2
  $kpis = @(
    @('平台', $Platform),
    @('店铺/品牌(自动识别)', ((Distinct-Column $dtMeta '店铺/品牌') -join '、')),
    @('文件名店铺标识', $(if ($ShopLabel) { $ShopLabel } else { '汇总' })),
    @('出表日期', (Get-Date -Format 'yyyy-MM-dd HH:mm')),
    @('输出文件', $outName),
    @('源文件数', "$($sources.Count)"),
    @('报表类型数', "$((Distinct-Column $dtMeta '报表类型').Count)"),
    @('解析列总数', "$($dtMeta.Rows.Count)"),
    @('去重度量指标数', "$($metricNames.Count)"),
    @('去重维度数', "$($dimNameSet.Count)"),
    @('源数据行数', "$($dtDetail.Rows.Count)"),
    @('时间趋势行数', "$($dtTrend.Rows.Count)"),
    @('透视表模式', $(if ($pivotOk) { '原生透视表（可刷新）' } else { 'COUNTIFS 活表' }))
  )
  foreach ($k in $kpis) {
    $wsSum.Cells.Item($r, 1) = $k[0]; $wsSum.Cells.Item($r, 1).Font.Bold = $true
    $wsSum.Cells.Item($r, 2) = $k[1]; $r++
  }
  $r++
  if ($dtDetail.Rows.Count -eq 0) {
    $wsSum.Cells.Item($r, 1) = '注意：全部源文件当前只有表头、没有数据行（Excel UsedRange 与 xlsx 内部 dimension 双重核验）。'
    $wsSum.Cells.Item($r, 1).Font.Color = 255; $r++
    $wsSum.Cells.Item($r, 1) = '本次交付 =「元数据透析 + 待填充结构」。把带数据行的导出放回本目录后重跑脚本，各 sheet 自动出数。'
    $r += 2
  }
  $wsSum.Cells.Item($r, 1) = '命名规则与逐文件解析结果见「文件清单」sheet；字段语义见「指标总览」sheet。'
  $wsSum.Cells.Item($r, 1).Font.Italic = $true
  $wsSum.Columns.Item(1).ColumnWidth = 22
  $wsSum.Columns.Item(2).ColumnWidth = 46

  # --- 文件清单
  $dtFiles = New-Object Data.DataTable
  foreach ($cn in @('源文件','店铺/品牌','报表类型','下载方式','时间粒度','对比口径','统计起','统计止','列数','数据行数')) {
    $null = $dtFiles.Columns.Add($cn, [string])
  }
  foreach ($s in $sources) {
    $m = $s.Meta
    $sh = '-'; if ($m.Shop) { $sh = $m.Shop }
    $frm = '-'; if ($m.DateFrom) { $frm = $m.DateFrom }
    $to  = '-'; if ($m.DateTo) { $to = $m.DateTo }
    $null = $dtFiles.Rows.Add([object[]]@($m.File, $sh, $m.Report, $m.Mode, $m.Granularity, $m.Compare, $frm, $to, "$($m.ColCount)", "$($m.RowCount)"))
  }
  $wsFiles = New-Sheet '文件清单' $wsSum
  $null = Write-DataTable $wsFiles $dtFiles '命名规则：6位以上数字段=店铺ID；日期段(2026-09-28/2026.09.28/20260928)=统计起止；离线/实时/分天下载/汇总下载/包括对比时间/同比/环比=元数据；其余段按序拼接=报表类型。'

  $wsSum.Activate()
  $outWb.SaveAs($outPath, 51)
  $outWb.Close($false)
  $outWb = $null

  # ============================================================== 校验 ==
  $check = [ordered]@{
    output          = $outPath
    source_files    = $sources.Count
    columns_total   = $dtMeta.Rows.Count
    detail_rows     = $dtDetail.Rows.Count
    skipped_non_numeric = $skippedNonNumeric
    unique_metrics  = $metricNames.Count
    unique_dims     = $dimNameSet.Count
    unique_dates    = $dtTrend.Rows.Count
    pivot_native    = $pivotOk
    pivot_note      = $pivotNote
    dim_sheets      = @($prodInfo, $chanInfo)
    per_report      = @($sources | ForEach-Object { @{ report = $_.Meta.Report; cols = $_.Headers.Count; rows = $_.Data.Count } })
    role_breakdown  = @{}
    log             = @($log)
  }
  foreach ($row in $dtMeta.Rows) {
    $rl = [string]$row['列角色']
    if ($check['role_breakdown'].ContainsKey($rl)) { $check['role_breakdown'][$rl]++ } else { $check['role_breakdown'][$rl] = 1 }
  }
  $json = $check | ConvertTo-Json -Depth 6
  [IO.File]::WriteAllText((Join-Path $scratch 'pivot_check.json'), $json, (New-Object Text.UTF8Encoding $false))
  Write-Host $json
}
finally {
  if ($outWb) { try { $outWb.Close($false) } catch { } }
  $excel.Quit()
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($excel)
  [GC]::Collect(); [GC]::WaitForPendingFinalizers()
}
