$ErrorActionPreference = 'Stop'
$tokenPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '.rurouter-token.xml'
$secure = Import-Clixml -LiteralPath $tokenPath
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
