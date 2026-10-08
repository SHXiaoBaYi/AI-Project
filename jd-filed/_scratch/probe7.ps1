$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb=$xl.Workbooks.Open('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx',0,$false)
  $ws=$wb.Worksheets.Item('明细数据')
  "C2 Value2 =[" + [string]$ws.Cells.Item(2,3).Value2 + "]"
  "J2 Value2 =[" + [string]$ws.Cells.Item(2,10).Value2 + "]"
  "K2 Value2 =[" + [string]$ws.Cells.Item(2,11).Value2 + "]"
  "F2 Value2 =[" + [string]$ws.Cells.Item(2,6).Value2 + "]"
  "L2 Value2 =[" + [string]$ws.Cells.Item(2,12).Value2 + "] type=" + $ws.Cells.Item(2,12).Value2.GetType().Name
  $ws2 = $wb.Worksheets.Item('图表数据')
  "B4 formula = " + [string]$ws2.Range('B4').Formula
  "B4 value   = " + [string]$ws2.Range('B4').Value2
  # 直接在明细数据里试探 COUNTIF/SUMIF 各条件组合
  $tests = [ordered]@{
    t_report  = '=COUNTIF($C:$C,"流量来源_流量结构分析_场域来源")'
    t_met     = '=COUNTIF($J:$J,"引入成交金额")'
    t_both    = '=COUNTIFS($C:$C,"流量来源_流量结构分析_场域来源",$J:$J,"引入成交金额")'
    t_3       = '=COUNTIFS($C:$C,"流量来源_流量结构分析_场域来源",$J:$J,"引入成交金额",$K:$K,"本期值")'
    t_4       = '=COUNTIFS($C:$C,"流量来源_流量结构分析_场域来源",$J:$J,"引入成交金额",$K:$K,"本期值",$F:$F,"搜索")'
    t_sum4    = '=SUMIFS($L:$L,$C:$C,"流量来源_流量结构分析_场域来源",$J:$J,"引入成交金额",$K:$K,"本期值",$F:$F,"搜索")'
    t_refcell = '=SUMIFS($L:$L,$C:$C,$R$1,$J:$J,$R$2,$K:$K,"本期值",$F:$F,"搜索")'
    t_pcsum   = '=SUMIFS($L:$L,$C:$C,"商品渠道明细_演示",$K:$K,"本期值",$H:$H,"羊毛衫A",$F:$F,"搜索")'
  }
  $ws.Cells.Item(1,18)='流量来源_流量结构分析_场域来源'; $ws.Cells.Item(2,18)='引入成交金额'
  $r=20
  foreach($k in $tests.Keys){
    $ws.Cells.Item($r,19) = $k
    $ws.Cells.Item($r,20).Formula = $tests[$k]
    "  {0,-10} => {1}" -f $k, [string]$ws.Cells.Item($r,20).Value2
    $r++
  }
  $wb.Save(); $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
