$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb = $xl.Workbooks.Add()
  $wsT = $wb.Worksheets.Item(1); $wsT.Name='目标预算'
  $map = [IO.File]::ReadAllText('D:\AI-XBY\AI-Project\jd-filed\scripts\dashboard_map_jd.json',[Text.UTF8Encoding]::new($true)) | ConvertFrom-Json
  $maps = @(@($map.target_columns.monthly, 2, '#,##0'), @($map.target_columns.budget, 3, '#,##0'))
  "maps.Count=" + $maps.Count
  $n = 0
  foreach ($mp in $maps) {
    $n++
    "[$n] mpType=" + $mp.GetType().Name + " len=" + @($mp).Count
    $col = $mp[1]
    "     colType=" + $col.GetType().Name + " value=$col"
    "     fmtType=" + $mp[2].GetType().Name
    try { $wsT.Cells.Item(4,$col).NumberFormat = $mp[2]; "     NumberFormat OK" } catch { "     NumberFormat FAIL: " + $_.Exception.Message }
    try { $wsT.Cells.Item(4,$col).Value2 = 900000 + 0 * 45000; "     Value2 OK" } catch { "     Value2 FAIL: " + $_.Exception.Message }
  }
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
