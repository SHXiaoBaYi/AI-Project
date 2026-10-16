# 测试环境运维一键脚本（需本机已配置 SSH 私钥）
# 用法：
#   .\deploy\run-ops-test.ps1 -Identity C:\path\to\test-ecs.pem -RdsUser xiaobayi0099 -RdsPass '***'
#   .\deploy\run-ops-test.ps1 -Identity ... -SkipSync          # 只 bootstrap + 发布
#   .\deploy\run-ops-test.ps1 -Identity ... -SkipBuild         # 用已有 jar/dist
param(
  [Parameter(Mandatory = $true)]
  [string]$Identity,
  [string]$EcsHost = '121.40.170.82',
  [string]$EcsUser = 'root',
  [string]$RdsUser = 'xiaobayi0099',
  [string]$RdsPass = '',
  [switch]$SkipSync,
  [switch]$SkipBuild,
  [switch]$SkipBootstrap
)

$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
$Remote = "${EcsUser}@${EcsHost}"
$Ssh = @('-i', $Identity, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=20')

function Invoke-Remote([string]$Cmd) {
  & ssh @Ssh $Remote $Cmd
  if ($LASTEXITCODE -ne 0) { throw "远程命令失败: $Cmd" }
}

Write-Host "==> SSH 探测 $Remote"
& ssh @Ssh $Remote 'uname -a'
if ($LASTEXITCODE -ne 0) { throw 'SSH 失败，请检查密钥/安全组/root 登录' }

if (-not $SkipBootstrap) {
  Write-Host '==> 上传 bootstrap 文件'
  & scp @Ssh `
    (Join-Path $Root 'deploy/bootstrap-test-ecs.sh') `
    (Join-Path $Root 'deploy/nginx-xby-admin.conf') `
    (Join-Path $Root 'deploy/xby-admin-test.service') `
    (Join-Path $Root 'deploy/sync-prod-to-test-on-ecs.sh') `
    "${Remote}:/tmp/"
  Invoke-Remote 'bash /tmp/bootstrap-test-ecs.sh /tmp/nginx-xby-admin.conf /tmp/xby-admin-test.service'
}

if (-not $SkipSync) {
  if (-not $RdsPass) { throw '同步需要 -RdsPass（或之后改用 -SkipSync）' }
  Write-Host '==> 生产库 → 测试库（在测试 ECS 上执行）'
  $escaped = $RdsPass.Replace("'", "'\''")
  Invoke-Remote "export RDS_USER='$RdsUser' RDS_PASS='$escaped'; bash /tmp/sync-prod-to-test-on-ecs.sh"
}

if (-not $SkipBuild) {
  Write-Host '==> 构建后端'
  Push-Location (Join-Path $Root 'BackEnd')
  try {
    . 'D:\AI-XBY\dev-tools\env.ps1'
    mvn -s 'D:\AI-XBY\dev-tools\maven-settings.xml' -DskipTests package
  } finally { Pop-Location }

  Write-Host '==> 构建前端'
  Push-Location (Join-Path $Root 'FrontEnd')
  try {
    $env:VITE_BASE = '/shxby/'
    pnpm build
  } finally { Pop-Location }
}

Write-Host '==> 发布到测试 ECS'
$env:ECS_HOST = $EcsHost
$env:ECS_USER = $EcsUser
$env:ECS_IDENTITY = $Identity
bash (Join-Path $Root 'deploy/publish-ecs-test.sh')

Invoke-Remote 'systemctl restart xby-admin; sleep 3; systemctl is-active xby-admin'
Write-Host "==> 打开 http://${EcsHost}/shxby/ 验证"
curl.exe -sS -o NUL -w "public_fe %{http_code}`n" "http://${EcsHost}/shxby/"
curl.exe -sS -o NUL -w "public_api %{http_code}`n" "http://${EcsHost}/shxby/api/auth/info"
