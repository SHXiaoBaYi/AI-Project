$M = @{}
$M['gmv_channel'] = [pscustomobject]@{ rep = '流量来源_流量结构分析_场域来源'; met = '引入成交金额' }
"rep=[" + $M.gmv_channel.rep + "]"
$chRep = $(if ($M.gmv_channel.rep) { $M.gmv_channel.rep } else { '流量来源_演示' })
"chRep=[" + $chRep + "] type=" + $(if ($null -eq $chRep) {'null'} else {$chRep.GetType().Name})
$rows = New-Object Collections.ArrayList
$ds='2026-09-01'; $c='搜索'; $g=8404.0
$null = $rows.Add(@($chRep, $ds, $c, '', $M.gmv_channel.met, $g))
$rw = $rows[0]
"rw.Count=" + @($rw).Count + " rw[0]=[" + $rw[0] + "] rw[2]=[" + $rw[2] + "]"
$arr = New-Object 'object[,]' 1,13
$arr[0,0]='京东'; $arr[0,2] = $rw[0]
"arr[0,2]=[" + $arr[0,2] + "]"
# 关键：多语句同行 + 中文字面量是否吃掉后面语句
$xl = New-Object -ComObject Excel.Application
$xl.Visible=$false; $xl.DisplayAlerts=$false
try {
  $wb=$xl.Workbooks.Add(); $ws=$wb.Worksheets.Item(1)
  $t=$ws.Range($ws.Cells.Item(1,1),$ws.Cells.Item(1,13)); $t.Value2=$arr
  "excel A1=[" + $ws.Cells.Item(1,1).Value2 + "] C1=[" + $ws.Cells.Item(1,3).Value2 + "]"
  $wb.Close($false)
} finally { $xl.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($xl) }
