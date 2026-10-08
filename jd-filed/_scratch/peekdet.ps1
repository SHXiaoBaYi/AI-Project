$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb = $xl.Workbooks.Open('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx',0,$true)
  $ws = $wb.Worksheets.Item('明细数据')
  for ($r=1; $r -le 8; $r++) {
    $v=@()
    for ($c=1; $c -le 13; $c++) { $v += [string]$ws.Cells.Item($r,$c).Text }
    "R$r | " + ($v -join ' | ')
  }
  '---- 指标总览 渠道相关行 ----'
  $wsM = $wb.Worksheets.Item('指标总览')
  $out=@{}
  $n=[int]$wsM.UsedRange.Rows.Count
  $ma=$wsM.Range($wsM.Cells.Item(2,3),$wsM.Cells.Item($n,9)).Value2
  for($i=1;$i -le ($n-1);$i++){
    $rep=[string]$ma.GetValue($i,1); $met=[string]$ma.GetValue($i,5)
    if($rep -like '流量来源*'){ $k=$rep+' :: '+$met; if(-not $out.ContainsKey($k)){$out[$k]=0}; $out[$k]++ }
  }
  $out.Keys | Sort-Object | Select-Object -First 12 | ForEach-Object { "  $_ = $($out[$_])" }
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
