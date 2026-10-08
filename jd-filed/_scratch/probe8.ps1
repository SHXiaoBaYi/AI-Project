$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb = $xl.Workbooks.Open('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx')
  $wsX = $wb.Worksheets.Item('图表数据'); $wsT = $wb.Worksheets.Add(); $wsT.Name='PT'
  $src = $wsX.Range('G3:I63')
  "src = " + $src.Address($false,$false) + " rows=" + [int]$src.Rows.Count + " cols=" + [int]$src.Columns.Count
  "G3=" + [string]$wsX.Range('G3').Value2 + " H3=" + [string]$wsX.Range('H3').Value2 + " I3=" + [string]$wsX.Range('I3').Value2
  $co = $wsT.ChartObjects().Add(10,10,400,250); $ch = $co.Chart
  $ch.ChartType = 51
  $null = $ch.SetSourceData($src)
  "default PlotBy=" + [int]$ch.PlotBy + " count=" + [int]$ch.SeriesCollection().Count
  $ch.PlotBy = 2
  "after PlotBy=2 count=" + [int]$ch.SeriesCollection().Count
  for($s=1;$s -le [int]$ch.SeriesCollection().Count;$s++){ "  s$s name=" + [string]$ch.SeriesCollection($s).Name + " pts=" + @($ch.SeriesCollection($s).Values).Count }
  $ch2 = $wsT.ChartObjects().Add(10,300,400,250).Chart
  $ch2.ChartType = 51
  $null = $ch2.SetSourceData($src, 2)
  "explicit PlotBy arg count=" + [int]$ch2.SeriesCollection().Count
  for($s=1;$s -le [int]$ch2.SeriesCollection().Count;$s++){ "  s$s name=" + [string]$ch2.SeriesCollection($s).Name }
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
