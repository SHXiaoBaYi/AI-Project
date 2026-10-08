param([string]$src, [string]$out)
$ErrorActionPreference = 'Stop'
$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
try {
  $wb = $excel.Workbooks.Open($src, 0, $true)
  foreach ($ws in $wb.Worksheets) {
    $ur = $ws.UsedRange
    $rows = $ur.Rows.Count; $cols = $ur.Columns.Count
    "###SHEET`t$($ws.Name)`t$rows`t$cols"
    $maxR = [Math]::Min($rows, 40)
    for ($r = 1; $r -le $maxR; $r++) {
      $vals = @()
      for ($c = 1; $c -le $cols; $c++) {
        $v = $ur.Cells.Item($r, $c).Text
        $vals += ($v -replace "`t"," " -replace "`r|`n"," ")
      }
      "R{0}`t{1}" -f $r, ($vals -join "`t")
    }
  }
  $wb.Close($false)
} finally {
  $excel.Quit()
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($excel)
}
