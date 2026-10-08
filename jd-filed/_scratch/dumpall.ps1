param([string]$dir, [string]$out, [int]$maxRows = 30)
$ErrorActionPreference = 'Stop'
$lines = New-Object Collections.ArrayList
$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false; $excel.DisplayAlerts = $false
try {
  Get-ChildItem -LiteralPath $dir -Filter *.xlsx | Sort-Object Name | ForEach-Object {
    [void]$lines.Add("===================FILE`t$($_.Name)")
    $wb = $excel.Workbooks.Open($_.FullName, 0, $true)
    foreach ($ws in $wb.Worksheets) {
      $ur = $ws.UsedRange
      $rows = $ur.Rows.Count; $cols = $ur.Columns.Count
      [void]$lines.Add("###SHEET`t$($ws.Name)`trows=$rows`tcols=$cols")
      $maxR = [Math]::Min($rows, $maxRows)
      for ($r = 1; $r -le $maxR; $r++) {
        $vals = @()
        for ($c = 1; $c -le $cols; $c++) {
          $v = $ur.Cells.Item($r,$c).Text
          if ($null -eq $v) { $v = '' }
          $vals += (("$v" -replace "`t",' ') -replace "`r|`n",' ')
        }
        [void]$lines.Add(("R{0}`t{1}" -f $r, ($vals -join "`t")))
      }
    }
    $wb.Close($false)
  }
} finally {
  $excel.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($excel)
}
[IO.File]::WriteAllLines($out, $lines, (New-Object Text.UTF8Encoding $false))
