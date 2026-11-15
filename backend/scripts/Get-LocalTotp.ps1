<#
Generador local para demostrar TOTP sin telefono. No llama al backend ni al proveedor.
Introducir la clave Base32 del registro, no la clave AES ni GESTOPAGO_TOKEN_STORAGE_KEY.
No usar secretos productivos ni pegar la clave en argumentos o servicios web.
#>
[CmdletBinding()]
param(
    [switch]$Continuo,
    [ValidatePattern('^[a-zA-Z0-9_-]{1,40}$')][string]$PerfilLocal,
    [switch]$SoloCodigo
)
$ErrorActionPreference = 'Stop'
if ($PerfilLocal) {
    # Solo demostracion local: DPAPI CurrentUser, no clave en texto plano ni en Git.
    $rutaPerfilTotp = Join-Path $PSScriptRoot ('../data/totp-' + $PerfilLocal + '.secret.dpapi')
    if (-not (Test-Path -LiteralPath $rutaPerfilTotp)) { throw 'No existe ese perfil TOTP local.' }
    $claveProtegidaTotp = Get-Content -LiteralPath $rutaPerfilTotp -Raw -Encoding UTF8 | ConvertTo-SecureString
} else {
    $claveProtegidaTotp = Read-Host 'Clave de registro TOTP (32 caracteres Base32)' -AsSecureString
}
$punteroTotp = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($claveProtegidaTotp)
try {
    $textoTotp = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($punteroTotp).Trim().ToUpperInvariant()
    if ($textoTotp -notmatch '^[A-Z2-7]{32}$') { throw 'La clave debe tener 32 caracteres Base32.' }
    $alfabetoTotp = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $bytesTotp = New-Object byte[] 20
    [long]$bufferTotp = 0; $bitsTotp = 0; $indiceTotp = 0
    foreach ($caracterTotp in $textoTotp.ToCharArray()) {
        $bufferTotp = (($bufferTotp -shl 5) -bor $alfabetoTotp.IndexOf($caracterTotp)) -band 65535
        $bitsTotp += 5
        if ($bitsTotp -ge 8) {
            $bitsTotp -= 8
            $bytesTotp[$indiceTotp] = [byte](($bufferTotp -shr $bitsTotp) -band 255)
            $indiceTotp++
        }
    }
    $textoTotp = $null
    $hmacTotp = New-Object System.Security.Cryptography.HMACSHA1
    $hmacTotp.Key = $bytesTotp
    try {
        do {
            $segundosTotp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
            [long]$pasoTotp = [Math]::Floor($segundosTotp / 30)
            $contadorTotp = [BitConverter]::GetBytes($pasoTotp)
            if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($contadorTotp) }
            $hashTotp = $hmacTotp.ComputeHash($contadorTotp)
            $offsetTotp = $hashTotp[$hashTotp.Length - 1] -band 15
            [long]$numeroTotp = (([long]($hashTotp[$offsetTotp] -band 127)) -shl 24) -bor
                (([long]$hashTotp[$offsetTotp + 1]) -shl 16) -bor
                (([long]$hashTotp[$offsetTotp + 2]) -shl 8) -bor $hashTotp[$offsetTotp + 3]
            $codigoTotp = '{0:D6}' -f ($numeroTotp % 1000000)
            if ($SoloCodigo) { Write-Output $codigoTotp }
            else { Write-Host ('Codigo: {0} | cambia en {1} segundos' -f $codigoTotp, (30 - ($segundosTotp % 30))) }
            if ($Continuo) { Start-Sleep -Seconds (30 - ($segundosTotp % 30)) }
        } while ($Continuo)
    } finally {
        $hmacTotp.Dispose()
        [Array]::Clear($bytesTotp, 0, $bytesTotp.Length)
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($punteroTotp)
    $claveProtegidaTotp.Dispose()
}
