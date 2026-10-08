$r = New-Object Collections.ArrayList
$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb = $xl.Workbooks.Open('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx')
  $wsX = $wb.Worksheets.Item('图表数据')
  $wsT = $wb.Worksheets.Add(); $wsT.Name='PTA'
  $v = $wsX.Range('I4').Value2
  $r.Add("I4 raw type=" + $(if($null -eq $v){'null'}else{$v.GetType().Name}) + " val=[" + [string]$v + "]")
  $r.Add("G4 type=" + $wsX.Range('G4').Value2.GetType().Name)

  $co = $wsT.ChartObjects().Add(10,10,300,200); $ch = $co.Chart; $ch.ChartType=51
  $null = $ch.SetSourceData($wsX.Range('G3:I63'), 2)
  $r.Add("A) SetSourceData(G3:I63, PlotBy=2 arg) series=" + [int]$ch.SeriesCollection().Count)

  $co2 = $wsT.ChartObjects().Add(340,10,300,200); $ch2 = $co2.Chart; $ch2.ChartType=51
  $null = $ch2.SetSourceData($wsX.Range('G3:H63'), 2)
  $r.Add("B) SetSourceData(G3:H63) series=" + [int]$ch2.SeriesCollection().Count)
  $ns = $null
  try { $ns = $ch2.SeriesCollection().NewSeries(); $r.Add("   NewSeries() ok -> series=" + [int]$ch2.SeriesCollection().Count) }
  catch { $r.Add("   NewSeries() FAILED: " + $_.Exception.Message) }
  if ($ns) {
    try { $ns.Values = $wsX.Range('I4:I63'); $ns.XValues = $wsX.Range('F4:F63'); $ns.Name='同比%'
          $ns.ChartType = 4; $ns.AxisGroup = 2
          $r.Add("   3rd series bound; count=" + [int]$ch2.SeriesCollection().Count + " type3=" + [int]$ch2.SeriesCollection(3).ChartType + " axis3=" + [int]$ch2.SeriesCollection(3).AxisGroup) }
    catch { $r.Add("   bind failed: " + $_.Exception.Message) }
  }
  $co3 = $wsT.ChartObjects().Add(10,260,300,200); $ch3 = $co3.Chart; $ch3.ChartType=51
  $null = $ch3.SetSourceData($wsX.Range('U4:W6'), 2)
  $r.Add("C) SetSourceData(U4:W6) series=" + [int]$ch3.SeriesCollection().Count)
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
[IO.File]::WriteAllLines('D:\AI-XBY\AI-Project\jd-filed\_scratch\probeA.txt', $r, (New-Object Text.UTF8Encoding $true))
