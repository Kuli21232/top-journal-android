$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$token = & (Join-Path $serverDir 'read-secret.ps1')
$headers = @{ Authorization = "Bearer $token" }
$user = Invoke-RestMethod -Uri 'https://beta.rurouter.ai/v1/user/info' -Headers $headers -Method Get
$models = Invoke-RestMethod -Uri 'https://beta.rurouter.ai/v1/models' -Headers $headers -Method Get
$glm = @($models.data | Where-Object { $_.id -match 'glm' } | Select-Object -ExpandProperty id)
$billing = [ordered]@{}
foreach ($property in $user.PSObject.Properties) {
    if ($property.Name -match 'balance|credit|limit|usage|spent|currency') {
        $billing[$property.Name] = $property.Value
    }
}
[ordered]@{
    reachable = $true
    billing = $billing
    glmModels = $glm
} | ConvertTo-Json -Depth 8
