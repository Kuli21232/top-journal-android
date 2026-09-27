param(
    [int]$Port = 8787
)

$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $serverDir
$configPath = Join-Path $serverDir 'server-config.json'
$tokenPath = Join-Path $serverDir '.rurouter-token.xml'
$caStore = Join-Path $serverDir 'local-ca.p12'
$serverStore = Join-Path $serverDir 'server.p12'
$csrPath = Join-Path $serverDir 'server.csr'
$signedCert = Join-Path $serverDir 'server-cert.cer'
$rawDir = Join-Path $projectDir 'app\src\main\res\raw'
$caCertificate = Join-Path $rawDir 'topjournal_ai_ca.cer'
$keytool = (Get-Command keytool -ErrorAction Stop).Source

New-Item -ItemType Directory -Force -Path $rawDir | Out-Null

if (-not (Test-Path -LiteralPath $tokenPath)) {
    $secureToken = Read-Host 'RuRouter token' -AsSecureString
    $secureToken | Export-Clixml -LiteralPath $tokenPath
}

$ip = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
    Sort-Object InterfaceMetric |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $ip) { throw 'LAN IPv4 address was not found' }

if (Test-Path -LiteralPath $configPath) {
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $clientKey = [string]$config.clientKey
    $pfxPassword = [string]$config.pfxPassword
    $caPassword = [string]$config.caPassword
} else {
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    function New-Secret([int]$length) {
        $bytes = New-Object byte[] $length
        $random.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
    }
    $clientKey = New-Secret 32
    $pfxPassword = New-Secret 24
    $caPassword = New-Secret 24
}

if (-not (Test-Path -LiteralPath $caStore)) {
    & $keytool -genkeypair -alias topjournal-ca -keyalg RSA -keysize 3072 `
        -dname 'CN=TopJournal Local AI CA,O=TopJournal' -ext 'bc=ca:true' `
        -ext 'ku=keyCertSign,cRLSign' -validity 3650 -storetype PKCS12 `
        -keystore $caStore -storepass $caPassword -keypass $caPassword -noprompt
}

& $keytool -exportcert -alias topjournal-ca -keystore $caStore -storepass $caPassword `
    -file $caCertificate

Remove-Item -LiteralPath $serverStore,$csrPath,$signedCert -Force -ErrorAction SilentlyContinue
& $keytool -genkeypair -alias topjournal-server -keyalg RSA -keysize 3072 `
    -dname "CN=$ip,O=TopJournal" -ext "san=ip:$ip,ip:127.0.0.1,dns:localhost" `
    -ext 'eku=serverAuth' -validity 825 -storetype PKCS12 -keystore $serverStore `
    -storepass $pfxPassword -keypass $pfxPassword -noprompt
& $keytool -certreq -alias topjournal-server -keystore $serverStore -storepass $pfxPassword `
    -file $csrPath
& $keytool -gencert -alias topjournal-ca -keystore $caStore -storepass $caPassword `
    -infile $csrPath -outfile $signedCert -ext "san=ip:$ip,ip:127.0.0.1,dns:localhost" `
    -ext 'eku=serverAuth' -validity 825
& $keytool -importcert -alias topjournal-ca -file $caCertificate -keystore $serverStore `
    -storepass $pfxPassword -noprompt
& $keytool -importcert -alias topjournal-server -file $signedCert -keystore $serverStore `
    -storepass $pfxPassword -noprompt

$config = [ordered]@{
    host = '0.0.0.0'
    ip = $ip
    port = $Port
    model = 'auto'
    clientKey = $clientKey
    pfxPassword = $pfxPassword
    caPassword = $caPassword
}
$config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding UTF8

Write-Host "TopJournal AI server configured at https://${ip}:$Port"
Write-Host 'The RuRouter token is protected with Windows DPAPI and was not copied into the app.'
