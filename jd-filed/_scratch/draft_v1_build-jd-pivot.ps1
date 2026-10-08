<#
.SYNOPSIS
  目录级多源 Excel/CSV 透视流水线（京东商智日报场景，无 Python 依赖）。

.DESCRIPTION
  1) 扫描目录下的 .xlsx / .csv 源文件
  2) 按命名规则解析文件名 -> 店铺/品牌、报表类型、下载方式、时间粒度、统计区间、对比口径
  3) 读取表头与数据行 -> 列语义分类（维度 / 本期值 / 对比日 / 较对比日 / 同比）
  4) 归一化为「指标总览」元数据长表 + 「明细数据」长表
  5) 输出单一工作簿：Summary / 指标总览 / 透视表(活表) / 时间趋势 / 商品维度 / 渠道维度 / 明细数据

.NOTES
  依赖：本机 Excel + PowerShell COM（无需 Python/pandas）。
  源文件只有表头时，值列留空并标注「待填充」，结构照常产出；放入真实数据后重跑即自动出数。
#>
[CmdletBinding()]
param(
  [string] $Dir        = (Get-Location).Path,
  [string] $OutDir     = '',
  [string] $Platform   = '京东',
  [string] $ShopLabel  = '',                                  # 覆盖店铺/品牌标识；空则自动取文件名
  [string] $DateTag    = (Get-Date -Format 'yy-MM-dd'),
  [int]    $MaxDataRows = 5000,
  [switch] $SkipCsv
)

$ErrorActionPreference = 'Stop'
$scratch = Join-Path $Dir '_scratch'
if (-not (Test-Path $scratch)) { $null = New-Item -ItemType Directory -Path $scratch }
$log = @()

# ---------------------------------------------------------------- 命名规则 --
# 规则（写死，可在此处调整）：
#   含 '_' 的名称按 '_' 切段；否则按 '-' 切段（保留品牌/报表内部的连字符）。
#   段为 6 位以上纯数字            -> 店铺ID
#   段为 日期(2026-09-28/2026.09.28/20260928) -> 统计起/止；其后的 HH_mm_ss 段并入该时间戳
#   段命中关键词表(离线/实时/分天下载/汇总下载/包括对比时间/同比/环比/30天/实时) -> 元数据
#   剩余段按原顺序拼接            -> 报表类型
$KEYWORDS = @{
  '离线' = '下载方式'; '实时' = '下载方式'
  '分天下载' = '时间粒度'; '分天' = '时间粒度'; '汇总下载' = '时间粒度'; '汇总' = '时间粒度'
  '包括对比时间' = '对比口径'; '同比' = '对比口径'; '环比' = '对比口径'
}
$DATE_RX  = '^(\d{4})[-.](\d{2})[-.](\d{2})$|^\d{8}$'
$TIME_RX  = '^\d{2}$'
$SHOP_RX  = '^\d{6,}$'

function Split-DateTimeToken([string]$s) {
  if ($s -match '^(\d{4})[-.](\d{2})[-.](\d{2})$') { return "$($matches[1])-$($matches[2])-$($matches[3])" }
  if ($s -match '^(\d{4})(\d{2})(\d{2})$')         { return "$($matches[1])-$($matches[2])-$($matches[3])" }
  return $null
}

function Parse-JdFileName([string]$leaf) {
  $base = [IO.Path]::GetFileNameWithoutExtension($leaf)
  $ext  = [IO.Path]::GetExtension($leaf).TrimStart('.').ToLower()
  $seps = @([regex]::Matches($base, '_')).Count
  $toks = if ($seps -gt 0) { $base.Split('_') } else { $base.Split('-') }
  $join = if ($seps -gt 0) { '_' } else { '-' }

  $shop = ''; $report = New-Object Collections.ArrayList
  $dates = New-Object Collections.ArrayList; $mode = ''; $gran = ''; $cmp = New-Object Collections.ArrayList
  $i = 0
  while ($i -lt $toks.Count) {
    $t = $toks[$i]
    $d = Split-DateTimeToken $t
    if ($d) {
      # 吸收 _00_00_00 形式的时间段
      $tail = ''
      $j = $i + 1; $nh = 0
      while ($j -lt $toks.Count -and $nh -lt 3 -and $toks[$j] -match $TIME_RX) { $tail += "$(if($nh){':'})$($toks[$j])"; $nh++; $j++ }
      $null = $dates.Add($(if ($tail) { "$d $tail" } else { $d }))
      $i = $j; continue
    }
    if ($KEYWORDS.ContainsKey($t)) {
      $k = $KEYWORDS[$t]
      switch ($k) {
        '下载方式' { $mode = $t }
        '时间粒度' { if ($t -like '分天*') { $gran = '分天' } else { $gran = '汇总' } }
        '对比口径' { $null = $cmp.Add($t) }
      }
      $i++; continue
    }
    if ($t -match $SHOP_RX -and $shop -eq '') { $shop = $t; $i++; continue }
    $null = $report.Add($t)
    $i++
  }
  [pscustomobject]@{
    File        = $leaf
    Ext         = $ext
    Shop        = $shop
    Report      = ($report -join $join)
    Mode        = $(if ($mode) { $mode } else { '未标注' })
    Granularity = $(if ($gran) { $gran } else { '未标注' })
    Compare     = $(if ($cmp.Count) { $cmp -join '+' } else { '无' })
    DateFrom    = $(if ($dates.Count -ge 1) { $dates[0] } else { '' })
    DateTo      = $(if ($dates.Count -ge 2) { $dates[$dates.Count - 1] } else { $dates[0] })
  }
}

# ------------------------------------------------------- 列语义分类规则 --
$DIM_NAMES = @('时间','日期','SPU','SPU名称','SKU','SKU名称','货号','商品名称','商品图片','一级类目','二级类目','三级类目',
               '一级渠道','二级渠道','三级渠道','四级渠道','渠道','计划类型','商品计划名称','ID','状态','出价方式','创建时间','省份','城市')

function Classify-Column([string]$name, [hashtable]$allNames) {
  $n = $name.Trim()
  if ($n.EndsWith('-对比日'))    { return @{ Base = $n.Substring(0, $n.Length - 4); Role = '对比日' } }
  if ($n.EndsWith('-较对比日'))  { return @{ Base = $n.Substring(0, $n.Length - 5); Role = '较对比日' } }
  if ($n.EndsWith('同比') -and $allNames.ContainsKey($n.Substring(0, $n.Length - 2))) {
    return @{ Base = $n.Substring(0, $n.Length - 2); Role = '同比' } }
  if ($DIM_NAMES -contains $n) { return @{ Base = $n; Role = '维度' } }
  return @{ Base = $n; Role = '本期值' }
}

function Guess-DataType([string]$base) {
  if ($base -match '(率|占比|比$|时长|单价|金额|成本|价值|投产比)') { return '小数' }
  if ($base -match '(数|量|件$|行$|客数|单量)')                     { return '整数' }
  return '文本'
}

# --------------------------------------------------------------- 读取源 --
function Read-CsvSource([string]$path) {
  foreach ($enc in @(936, 65001)) {
    try {
      $txt = [IO.File]::ReadAllText($path, [Text.Encoding]::GetEncoding($enc))
      if ($txt -match '[\u4e00-\u9fa5]') {
        $lines = @($txt -split "`r?`n" | Where-Object { $_.Trim() -ne '' })
        if ($lines.Count -eq 0) { return $null }
        # 简易 CSV 解析（本场景字段内不含逗号）
        $rows = @($lines | ForEach-Object { $_.Split(',') })
        return @{ Headers = $rows[0]; Data = @($rows | Select-Object -Skip 1) }
      }
    } catch { }
  }
  return $null
}

$sources = @()
$files = @(Get-ChildItem -LiteralPath $Dir -File | Where-Object {
            $_.Extension -eq '.xlsx' -or (-not $SkipCsv -and $_.Extension -eq '.csv') }) |
          Where-Object { $_.Name -notlike '~$*' } | Sort-Object Name

if ($files.Count -eq 0) { throw "目录 $Dir 下没有 .xlsx/.csv 源文件" }

$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false; $excel.DisplayAlerts = $false
$outWb = $null
try {
  $parsed = @{}
  foreach ($f in $files) {
    $meta = Parse-JdFileName $f.Name
    if ($ShopLabel) { $meta.Shop = $ShopLabel }
    $parsed[$f.Name] = $meta
    $headers = @(); $data = @()
    if ($f.Extension -eq '.csv') {
      $c = Read-CsvSource $f.FullName
      if ($c) { $headers = $c.Headers; $data = @($c.Data) }
    } else {
      $wb = $excel.Workbooks.Open($f.FullName, 0, $true)
      $ws = $wb.Worksheets.Item(1)
      $ur = $ws.UsedRange
      $rows = [int]$ur.Rows.Count; $cols = [int]$ur.Columns.Count
      if ($cols -gt 0) {
        $h = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(1,$cols)).Value2
        if ($cols -eq 1) { $headers = @($h) } else { $headers = @($h) | ForEach-Object { $_ } }
        $headers = @($headers) | ForEach-Object { "$_".Trim() }
      }
      if ($rows -gt 1) {
        $cap = [Math]::Min($rows - 1, $MaxDataRows)
        $a = $ws.Range($ws.Cells.Item(2,1), $ws.Cells.Item(1 + $cap, $cols)).Value2
        for ($r = 1; $r -le $cap; $r++) {
          $line = @()
          for ($c = 1; $c -le $cols; $c++) { $line += $a.GetValue($r, $c) }
          $data += ,$line
        }
      }
      $wb.Close($false)
      $meta | Add-Member -Force NoteProperty ColCount $cols
    }
    if (-not $meta.PSObject.Properties['ColCount']) { $meta | Add-Member NoteProperty ColCount $headers.Count }
    $meta | Add-Member -Force NoteProperty RowCount $data.Count
    $sources += ,@{ Meta = $meta; Headers = $headers; Data = $data }
  }

  # ---------------------------------------------------- 构建透视输入表 --
  $metaRows = New-Object Collections.ArrayList   # 指标总览
  $detailRows = New-Object Collections.ArrayList # 明细数据（长表）
  $dateSet = New-Object Collections.ArrayList
  $metricNames = @{}
  $perFileMetric = @{}

  foreach ($s in $sources) {
    $m = $s.Meta
    $allNames = @{}; foreach ($h in $s.Headers) { if ($h) { $allNames[$h] = $true } }
    $cls = @(); $ci = 0
    foreach ($h in $s.Headers) {
      if (-not $h) { $cls += $null; $ci++; continue }
      $cls += ,(Classify-Column $h $allNames)
      $ci++
    }
    $firstRow = if ($s.Data.Count -gt 0) { $s.Data[0] } else { $null }
    for ($i = 0; $i -lt $s.Headers.Count; $i++) {
      $h = $s.Headers[$i]; if (-not $h) { continue }
      $c = $cls[$i]
      $metricNames[$c.Base] = $true
      if ($c.Role -in @('本期值','对比日','同比','较对比日')) {
        if (-not $perFileMetric.ContainsKey($m.Report)) { $perFileMetric[$m.Report] = @{} }
        $perFileMetric[$m.Report][$c.Base] = $true
      }
      $val = ''
      if ($firstRow -and $i -lt $firstRow.Count -and $null -ne $firstRow[$i]) { $val = "$($firstRow[$i])" }
      $null = $metaRows.Add(@(
        $Platform, $(if ($m.Shop) { $m.Shop } else { '未标注' }), $m.Report, $m.Granularity,
        "$($m.DateFrom)~$($m.DateTo)", ($i + 1), $h, $c.Base, $c.Role, $(if ($c.Role -eq '维度') { '否' } else { '是' }),
        $(if ($val -ne '') { $val } else { '待填充' }), $(Guess-DataType $c.Base), $m.File
      ))
    }
    # 明细长表：一个数据行 × 一个度量列 = 一条
    foreach ($dr in $s.Data) {
      $timeVal = ''
      for ($i = 0; $i -lt $cls.Count; $i++) {
        if ($cls[$i] -and $cls[$i].Role -eq '维度' -and $cls[$i].Base -in @('时间','日期') -and $i -lt $dr.Count) { $timeVal = "$($dr[$i])" }
      }
      if ($timeVal) { $null = $dateSet.Add($timeVal) }
      $dims = @()
      for ($i = 0; $i -lt $cls.Count; $i++) {
        if ($cls[$i] -and $cls[$i].Role -eq '维度' -and $i -lt $dr.Count -and $cls[$i].Base -notin @('时间','日期')) {
          $dims += "$($cls[$i].Base)=$($dr[$i])"
        }
      }
      for ($i = 0; $i -lt $cls.Count; $i++) {
        $c = $cls[$i]; if (-not $c -or $c.Role -eq '维度') { continue }
        if ($i -ge $dr.Count) { continue }
        $v = $dr[$i]; if ($null -eq $v -or "$v" -eq '') { continue }
        $null = $detailRows.Add(@(
          $Platform, $(if ($m.Shop) { $m.Shop } else { '未标注' }), $m.Report, $m.Granularity,
          $(if ($timeVal) { $timeVal } else { $m.DateFrom }), ($dims -join ' | '), $c.Base, $c.Role, $v, $m.File
        ))
      }
    }
    foreach ($d in @($m.DateFrom, $m.DateTo)) { if ($d -and $d -notmatch ':') { $null = $dateSet.Add($d) } }
  }

  $uniqDates = @($dateSet | Where-Object { $_ } | Sort-Object -Unique)

  # ---------------------------------------------------------- 写出工作簿 --
  if (-not $OutDir) { $OutDir = $Dir }
  if (-not (Test-Path $OutDir)) { $null = New-Item -ItemType Directory -Path $OutDir }
  $outName = "{0}_{1}_{2}.xlsx" -f $Platform, $(if ($ShopLabel) { $ShopLabel } else { '汇总' }), $DateTag
  $outPath = Join-Path $OutDir $outName
  if (Test-Path -LiteralPath $outPath) {
    $bak = Join-Path $scratch ("backup__" + (Get-Date -Format 'yyyyMMdd_HHmmss') + "__" + $outName)
    Copy-Item -LiteralPath $outPath -Destination $bak -Force
    $log += "已备份同名旧文件 -> $bak"
  }

  $outWb = $excel.Workbooks.Add()
  while ($outWb.Worksheets.Count -gt 1) { $outWb.Worksheets.Item($outWb.Worksheets.Count).Delete() }

  function Write-Table($ws, $header, $rows, [string]$titleNote) {
    $nr = $rows.Count + 1
    if ($nr -lt 1) { $nr = 1 }
    $nc = $header.Count
    $arr = New-Object 'object[,]' $nr, $nc
    for ($j = 0; $j -lt $nc; $j++) { $arr[0, $j] = $header[$j] }
    for ($i = 0; $i -lt $rows.Count; $i++) {
      $r = $rows[$i]
      for ($j = 0; $j -lt [Math]::Min($nc, $r.Count); $j++) { $arr[($i + 1), $j] = $r[$j] }
    }
    $rng = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item($nr, $nc))
    $rng.Value2 = $arr
    $hr = $ws.Range($ws.Cells.Item(1,1), $ws.Cells.Item(1, $nc))
    $hr.Font.Bold = $true
    $hr.Interior.Color = 15917529          # 浅灰蓝
    $hr.Interior.Pattern = 1
    $ws.Activate()
    $excel.ActiveWindow.SplitRow = 1
    $excel.ActiveWindow.FreezePanes = $true
    if ($nr -gt 1) { $null = $rng.CurrentRegion.AutoFilter($nc) }
    $null = $ws.Columns.AutoFit()
    if ($ws.Columns.Item($nc).ColumnWidth -gt 60) { $ws.Columns.Item($nc).ColumnWidth = 60 }
    if ($titleNote) {
      $cmt = $ws.Cells.Item(1, ($nc + 2)); $cmt.AddComment($titleNote)
      $cmt.Comment.TextSize = 9; $cmt.Shape.Height = 120; $cmt.Shape.Width = 300
    }
  }

  # --- 指标总览
  $wsMeta = $outWb.Worksheets.Item(1); $wsMeta.Name = '指标总览'
  $metaHeader = @('平台','店铺/品牌','报表类型','时间粒度','统计区间','列序号','原始列名','基础指标名','列角色','是否度量','本期首行值','推断数据类型','来源文件')
  $metaList = New-Object Collections.ArrayList; foreach ($r in $metaRows) { $null = $metaList.Add($r) }
  Write-Table $wsMeta $metaHeader $metaList '本表由源文件表头自动解析生成；「本期首行值」为待填充表示源文件当前没有数据行。'

  # --- 明细数据
  $wsDetail = $outWb.Worksheets.Add([Type]::Missing, $wsMeta); $wsDetail.Name = '明细数据'
  $detHeader = @('平台','店铺/品牌','报表类型','时间粒度','统计日期','维度组合','基础指标名','列角色','数值','来源文件')
  $detList = New-Object Collections.ArrayList; foreach ($r in $detailRows) { $null = $detList.Add($r) }
  Write-Table $wsDetail $detHeader $detList '长表结构：源文件放入数据行后重跑本脚本，此处即成为透视数据源。'

  # --- 透视表（活表）
  $wsPivot = $outWb.Worksheets.Add([Type]::Missing, $wsDetail); $wsPivot.Name = '透视表'
  $pivotOk = $false; $pivotNote = ''
  try {
    $lastRow = $wsMeta.UsedRange.Rows.Count
    $lastCol = $wsMeta.UsedRange.Columns.Count
    $src = $wsMeta.Range($wsMeta.Cells.Item(1,1), $wsMeta.Cells.Item($lastRow, $lastCol))
    $cache = $outWb.PivotCaches().Create(1, $src.Address($false, $false, 1, 1))
    $pt = $cache.CreatePivotTable($wsPivot.Range('A3'), '指标透析')
    $pt.PivotFields('报表类型').Orientation = 1
    $pt.PivotFields('列角色').Orientation = 2
    $null = $pt.AddDataField($pt.PivotFields('基础指标名'), '指标/列数', -4112)
    $pt.RowAxisLayout(0)
    $pt.HasGrandTotals = $true
    $pivotOk = $true
    $pivotNote = "数据源 指标总览!A1:$([char](64+$lastCol))$lastRow"
  } catch { $pivotNote = "原生透视表创建失败：$($_.Exception.Message)" }

  if (-not $pivotOk) {
    # 降级：COUNTIFS 交叉表，同样是活表（可随指标总览刷新）
    $roles = @('维度','本期值','对比日','较对比日','同比')
    $reports = @($metaRows | ForEach-Object { $_[2] } | Sort-Object -Unique)
    $ctHeader = New-Object Collections.ArrayList; $null = $ctHeader.Add('报表类型')
    foreach ($r in $roles) { $null = $ctHeader.Add($r) }
    $null = $ctHeader.Add('合计列数')
    $ctRows = New-Object Collections.ArrayList
    $i = 2
    foreach ($rep in $reports) {
      $line = New-Object Collections.ArrayList; $null = $line.Add($rep)
      foreach ($r in $roles) {
        $null = $line.Add("=COUNTIFS('指标总览'!`$C:`$A,`"$rep`",('指标总览'!`$I:``$I),`"$r`")")
      }
      $null = $line.Add("=SUM(B$i:F$i)")
      $null = $ctRows.Add($line); $i++
    }
    $null = $wsPivot.Range('A1').Value2 = '透视表（COUNTIFS 降级版）'
    Write-Table $wsPivot @($ctHeader) $ctRows $pivotNote
  }

  # --- 时间趋势 / 商品维度 / 渠道维度
  $coreMetrics = @('成交金额','成交商品件数','成交客户数','成交单量','成交转化率','客单价','件单价','UV价值',
                   '店铺浏览量','店铺访客数','商品浏览量','商品访客数','加购客户数','加购商品件数','退款金额','下单金额')
  $metricList = @($metricNames.Keys | Where-Object { $coreMetrics -contains $_ } | Sort-Object)
  if ($metricList.Count -eq 0) { $metricList = @($metricNames.Keys | Sort-Object | Select-Object -First 20) }

  $wsTrend = $outWb.Worksheets.Add([Type]::Missing, $wsPivot); $wsTrend.Name = '时间趋势'
  $trHeader = New-Object Collections.ArrayList; $null = $trHeader.Add('日期'); $null = $trHeader.Add('日期粒度')
  foreach ($mm in $metricList) { $null = $trHeader.Add($mm) }
  foreach ($mm in $metricList) { $null = $trHeader.Add("$mm 日环比") }
  $trRows = New-Object Collections.ArrayList
  foreach ($d in $uniqDates) {
    $line = New-Object Collections.ArrayList; $null = $line.Add($d); $null = $line.Add($(if ($d -match '^\d{4}-\d{2}-\d{2}$') { '日' } else { '时点' }))
    for ($k = 0; $k -lt ($metricList.Count * 2); $k++) { $null = $line.Add('') }
    $null = $trRows.Add($line)
  }
  if ($trRows.Count -eq 0) { $null = $trRows.Add(@('（源文件无日期数据行）','') ) }
  Write-Table $wsTrend @($trHeader) $trRows '行=日期，列=核心指标 + 日环比。数据行落到「明细数据」后按日期填值即可。'

  function Dim-Sheet([string]$name, [string]$report, [string[]]$dims, [string]$note) {
    $ws = $outWb.Worksheets.Add([Type]::Missing, $outWb.Worksheets.Item($outWb.Worksheets.Count))
    $ws.Name = $name
    $src2 = @($metaRows | Where-Object { $_[2] -eq $report })
    $mets = @($src2 | Where-Object { $_[8] -eq '本期值' } | ForEach-Object { $_[7] } | Sort-Object -Unique)
    $hdr = New-Object Collections.ArrayList
    foreach ($d in $dims) { $null = $hdr.Add($d) }
    foreach ($mm in $mets) { $null = $hdr.Add($mm) }
    $rows2 = New-Object Collections.ArrayList
    $vals = @($detailRows | Where-Object { $_[2] -eq $report } | ForEach-Object { $_[5] } | Sort-Object -Unique)
    if ($vals.Count -gt 0) {
      $k = 0
      foreach ($v in $vals) {
        $line = New-Object Collections.ArrayList
        $pairs = @($v -split ' \| ')
        $ph = @{}; foreach ($p in $pairs) { $ix = $p.IndexOf('='); if ($ix -gt 0) { $ph[$p.Substring(0,$ix)] = $p.Substring($ix+1) } }
        foreach ($d in $dims) { $null = $line.Add($(if ($ph.ContainsKey($d)) { $ph[$d] } else { '' })) }
        for ($j = 0; $j -lt $mets.Count; $j++) { $null = $line.Add('') }
        $null = $rows2.Add($line); $k++
        if ($k -ge 2000) { break }
      }
    } else {
      $line = New-Object Collections.ArrayList
      foreach ($d in $dims) { $null = $line.Add('') }
      for ($j = 0; $j -lt $mets.Count; $j++) { $null = $line.Add('') }
      $null = $rows2.Add($line)
    }
    Write-Table $ws @($hdr) $rows2 $note
    return $mets.Count
  }
  $prodMetrics = Dim-Sheet '商品维度' '商品明细' @('日期','SPU','SPU名称','一级类目','二级类目','三级类目','货号') '维度取自「商品明细」报表，指标列自动展开。'
  Dim-Sheet '渠道维度' '流量来源_流量结构分析_场域来源' @('日期','一级渠道','二级渠道','三级渠道','四级渠道') '维度取自「流量来源-场域来源」报表。' > $null

  # --- Summary（置于首位）
  $wsSum = $outWb.Worksheets.Add([Type]::Missing, $wsMeta); $wsSum.Name = 'Summary'; $wsSum.Move(1)
  $r = 1
  $wsSum.Cells.Item($r,1) = "$Platform 商智数据透析表"
  $wsSum.Range("A$r").Font.Size = 16; $wsSum.Range("A$r").Font.Bold = $true; $r += 2
  $kpis = @(
    @('平台', $Platform), @('店铺/品牌', $(if ($ShopLabel) { $ShopLabel } else { '多店铺' })),
    @('出表日期', (Get-Date -Format 'yyyy-MM-dd HH:mm')), @('源文件数', $sources.Count),
    @('报表类型数', @($sources | ForEach-Object { $_.Meta.Report } | Sort-Object -Unique).Count),
    @('去重基础指标数', $metricNames.Count),
    @('解析列总数', $metaRows.Count),
    @('源数据行数', $detailRows.Count),
    @('透视表模式', $(if ($pivotOk) { '原生透视表(可刷新)' } else { 'COUNTIFS 活表' }))
  )
  foreach ($k in $kpis) {
    $wsSum.Cells.Item($r,1) = $k[0]; $wsSum.Cells.Item($r,1).Font.Bold = $true
    $wsSum.Cells.Item($r,2) = $k[1]; $r++
  }
  $r++
  if ($detailRows.Count -eq 0) {
    $wsSum.Cells.Item($r,1) = '注意：全部源文件当前只有表头、没有数据行（已用 UsedRange 与 xlsx 内部 dimension 双重核验）。'
    $wsSum.Range("A$r").Font.Color = 255
    $r++
    $wsSum.Cells.Item($r,1) = '本次产出的是「元数据透析 + 待填充结构」。把带数据行的导出文件放回本目录后重跑脚本，各 sheet 会自动出数。'
    $r += 2
  }
  $fh = @('源文件','店铺/品牌','报表类型','下载方式','时间粒度','对比口径','统计起','统计止','列数','数据行数')
  $fl = New-Object Collections.ArrayList
  foreach ($s in $sources) {
    $m = $s.Meta
    $null = $fl.Add(@($m.File,$m.Shop,$m.Report,$m.Mode,$m.Granularity,$m.Compare,$m.DateFrom,$m.DateTo,$m.ColCount,$m.RowCount))
  }
  $wsSum.Cells.Item($r,1) = '文件解析结果（命名规则命中情况）'; $wsSum.Cells.Item($r,1).Font.Bold = $true; $r++
  $startRng = $wsSum.Cells.Item($r,1)
  Write-Table $wsSum $fh @($fl) '' | Out-Null
  # Write-Table 从第 1 行写；这里改为专用子表避免覆盖，故把文件清单挪到独立 sheet
  $wsFiles = $outWb.Worksheets.Add([Type]::Missing, $wsSum); $wsFiles.Name = '文件清单'
  Write-Table $wsFiles $fh @($fl) '命名规则：段=6位以上数字→店铺ID；日期段(2026-09-28/20260928/2026.09.28)→统计起止；离线/实时/分天下载/汇总下载/包括对比时间/同比→元数据；其余按序拼接→报表类型。'
  $wsSum.Cells.Item($r,1) = '见「文件清单」sheet'; $wsSum.Cells.Item($r,1).Font.Italic = $true
  $wsSum.Columns.Item(1).ColumnWidth = 26
  $wsSum.Columns.Item(2).ColumnWidth = 40
  if ($log.Count) { $wsSum.Cells.Item(($r + 2),1) = ($log -join '；') }

  $wsSum.Activate()
  if (Test-Path -LiteralPath $outPath) { $outWb.Save() } else { $outWb.SaveAs($outPath, 51) }
  $outWb.Close($false)

  # ---------------------------------------------------------- 校验输出 --
  $check = [ordered]@{
    output        = $outPath
    source_files  = $sources.Count
    columns_total = $metaRows.Count
    detail_rows   = $detailRows.Count
    unique_dates  = $uniqDates.Count
    pivot_native  = $pivotOk
    pivot_note    = $pivotNote
    reports       = @($sources | ForEach-Object { @{ report = $_.Meta.Report; cols = $_.Headers.Count; rows = $_.Data.Count } })
    top_metrics   = @($perFileMetric.GetEnumerator() | Sort-Object { -$_.Value.Count } | ForEach-Object { "$($_.Key)=$($_.Value.Count)" } | Select-Object -First 8)
  }
  $check | ConvertTo-Json -Depth 6 | ForEach-Object { $log += $_ }
  [IO.File]::WriteAllText((Join-Path $scratch 'pivot_check.json'), ($check | ConvertTo-Json -Depth 6), (New-Object Text.UTF8Encoding $false))
  Write-Host ($check | ConvertTo-Json -Depth 6)
}
finally {
  if ($outWb) { try { $outWb.Close($false) } catch {} }
  $excel.Quit()
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($excel)
  [GC]::Collect(); [GC]::WaitForPendingFinalizers()
}
