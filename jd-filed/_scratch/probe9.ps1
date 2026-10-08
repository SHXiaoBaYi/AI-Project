$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
function Try-Src($wb,$wsT,$label,$addr){
  $co = $wsT.ChartObjects().Add(10,10,300,200); $ch = $co.Chart
  $ch.ChartType = 51
  try {
    $null = $ch.SetSourceData($wb.Worksheets.Item('图表数据').Range($addr), 2)
    $c = [int]$ch.SeriesCollection().Count
    $names = @(); for($s=1;$s -le $c;$s++){ $names += [string]$ch.SeriesCollection($s).Name }
    "$label addr=$addr series=$c names=" + ($names -join '/')
  } catch { "$label FAILED: " + $_.Exception.Message }
  try { $co.Delete() } catch {}
}
try {
  $wb = $xl.Workbooks.Open('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx')
  $wsX = $wb.Worksheets.Item('图表数据')
  "G4..I4 values: G=" + [string]$wsX.Range('G4').Value2 + " H=" + [string]$wsX.Range('H4').Value2 + " I=[" + [string]$wsX.Range('I4').Value2 + "]"
  "G4 formula: " + [string]$wsX.Range('G4').Formula
  $wsT = $wb.Worksheets.Add(); $wsT.Name='PT9'
  Try-Src $wb $wsT 'ctrl_UW ' 'U4:W6'
  Try-Src $wb $wsT 'GH      ' 'G4:H63'
  Try-Src $wb $wsT 'GHI_hdr ' 'G3:I63'
  Try-Src $wb $wsT 'GH_hdr  ' 'G3:H63'
  # 纯数字小块
  $wsX.Range('AA1').Value2 = 'x'; $wsX.Range('AB1').Value2 = 'y'
  for($i=1;$i -le 6;$i++){ $wsX.Range("AA$($i+1)").Value2 = $i*2; $wsX.Range("AB$($i+1)").Value2 = $i*3 }
  $co=$wsT.ChartObjects().Add(10,240,300,200); $ch=$co.Chart; $ch.ChartType=51
  $null = $ch.SetSourceData($wsX.Range('AA1:AB7'),2)
  "synthetic AA1:AB7 series=" + [int]$ch.SeriesCollection().Count
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
