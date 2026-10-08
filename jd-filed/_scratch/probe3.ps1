$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb = $xl.Workbooks.Open('D:\AI-XBY\AI-Project\jd-filed\京东_奔养养_26-09-30_演示.xlsx')
  $wsT = $wb.Worksheets.Item('目标预算')
  $map = [IO.File]::ReadAllText('D:\AI-XBY\AI-Project\jd-filed\scripts\dashboard_map_jd.json',[Text.UTF8Encoding]::new($true)) | ConvertFrom-Json
  $maps = @(@($map.target_columns.monthly, 2, '#,##0'), @($map.target_columns.budget, 3, '#,##0'))
  "maps.Count=" + $maps.Count
  foreach ($mp in $maps) {
    "  mpType=" + $mp.GetType().Name + " mpRank=" + $mp.Rank + " len=" + @($mp).Count
    $col = $mp[1]
    "  colType=" + $col.GetType().Name + " col=$col"
    $r = 4
    try { $wsT.Cells.Item($r,$col).NumberFormat = $mp[2]; "  NumberFormat OK" } catch { "  NumberFormat FAIL: " + $_.Exception.Message }
    try { $i = 0; $wsT.Cells.Item($r,$col).Value2 = 900000 + $i * 45000; "  Value2 OK" } catch { "  Value2 FAIL: " + $_.Exception.Message }
  }
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
