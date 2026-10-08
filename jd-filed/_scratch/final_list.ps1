$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
$out = New-Object Collections.ArrayList
try {
  foreach ($p in @('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30.xlsx','D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx')) {
    $wb = $xl.Workbooks.Open($p,0,$true)
    $nc = 0
    foreach ($ws in $wb.Worksheets) { $nc += [int]$ws.ChartObjects().Count }
    $out.Add([IO.Path]::GetFileName($p) + "  sheets=" + [int]$wb.Worksheets.Count + "  charts=" + $nc + "  pivot=" + [int]$wb.Worksheets.Item('透视表').PivotTables().Count)
    $out.Add('   ' + (($wb.Worksheets | ForEach-Object { $_.Name }) -join ' / '))
    $wb.Close($false)
  }
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
[IO.File]::WriteAllLines('D:\AI-XBY\AI-Project\jd-filed\_scratch\final_list.txt', $out, (New-Object Text.UTF8Encoding $true))
