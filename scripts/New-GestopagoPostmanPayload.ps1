<#
Prepara una recarga de precio fijo para Postman. NO realiza peticiones HTTP.
Usar IDs vigentes del catalogo y un telefono autorizado para la prueba.
Guarda el payload y su UPC para consultar confirmTx si se pierde la respuesta.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateRange(1,2147483647)][int]$IdProducto,
    [Parameter(Mandatory=$true)][ValidateRange(1,2147483647)][int]$IdServicio,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9]{10,20}$')][string]$Telefono,
    [string]$PropertiesPath = (Join-Path $PSScriptRoot '../src/main/resources/application-local.properties'),
    [string]$Salida,
    [switch]$CopiarSigned
)
$ErrorActionPreference = 'Stop'
$propiedadesTx = @{}
foreach ($lineaTx in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $PropertiesPath), [Text.Encoding]::UTF8)) {
    if ($lineaTx -match '^\s*(gestopago\.crypto\.(?:secret-key|iv))\s*=(.*)$') {
        $propiedadesTx[$matches[1]] = $matches[2].Trim()
    }
}
try {
    $claveTx = [Convert]::FromBase64String($propiedadesTx['gestopago.crypto.secret-key'])
    $ivTx = [Convert]::FromBase64String($propiedadesTx['gestopago.crypto.iv'])
    if ($claveTx.Length -ne 32 -or $ivTx.Length -ne 16) { throw 'Configuracion invalida' }
} catch { throw 'Configura la clave AES de 32 bytes y el IV de 16 bytes en Base64 en el properties local.' }

$rngTx = [Security.Cryptography.RandomNumberGenerator]::Create()
$aesTx = [Security.Cryptography.Aes]::Create()
$cifradorTx = $null
try {
    $alfabetoTx = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    $byteTx = New-Object byte[] 1
    $upcTx = ''
    while ($upcTx.Length -lt 10) {
        $rngTx.GetBytes($byteTx)
        # Rejection sampling: distribucion uniforme sobre los 36 caracteres.
        if ($byteTx[0] -lt 252) { $upcTx += $alfabetoTx[$byteTx[0] % 36] }
    }
    $zonaTx = [TimeZoneInfo]::FindSystemTimeZoneById('Central Standard Time (Mexico)')
    $horaTx = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $zonaTx).ToString('yyyyMMddHHmmss')
    $payloadTx = [ordered]@{
        idProducto=$IdProducto; idServicio=$IdServicio; telefono=$Telefono
        horaLocal=$horaTx; upc=$upcTx; unidad='083'
    }
    $jsonTx = $payloadTx | ConvertTo-Json -Compress
    $aesTx.Key = $claveTx; $aesTx.IV = $ivTx
    $aesTx.Mode = [Security.Cryptography.CipherMode]::CBC
    # PKCS7 para bloques AES equivale a PKCS5Padding de la implementacion Java.
    $aesTx.Padding = [Security.Cryptography.PaddingMode]::PKCS7
    $cifradorTx = $aesTx.CreateEncryptor()
    $textoTx = [Text.Encoding]::UTF8.GetBytes($jsonTx)
    $signedTx = [Convert]::ToBase64String($cifradorTx.TransformFinalBlock($textoTx, 0, $textoTx.Length))
    $randomTx = [Guid]::NewGuid().ToString('N')
    if ([string]::IsNullOrWhiteSpace($Salida)) {
        $carpetaTx = Join-Path $PSScriptRoot '../data'
        [IO.Directory]::CreateDirectory($carpetaTx) | Out-Null
        $Salida = Join-Path $carpetaTx ('postman-sendTx-' + $randomTx + '.json')
    }
    $rutaTx = [IO.Path]::GetFullPath($Salida)
    if (Test-Path -LiteralPath $rutaTx) { throw 'El archivo ya existe; no se sobrescribe una operacion anterior.' }
    $registroTx = [ordered]@{ payload=$payloadTx; random=$randomTx; signed=$signedTx }
    [IO.File]::WriteAllText($rutaTx, ($registroTx | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    if ($CopiarSigned) { Set-Clipboard -Value $signedTx }
    Write-Output ('random: ' + $randomTx)
    Write-Output ('UPC: ' + $upcTx)
    Write-Output ('Archivo con payload, random y signed: ' + $rutaTx)
    if ($CopiarSigned) { Write-Output 'signed copiado al portapapeles. Pegalo en Postman.' }
    Write-Output 'Solo se preparo el contenido. No se envio ninguna transaccion.'
} finally {
    if ($null -ne $cifradorTx) { $cifradorTx.Dispose() }
    $aesTx.Dispose(); $rngTx.Dispose()
    [Array]::Clear($claveTx, 0, $claveTx.Length)
    [Array]::Clear($ivTx, 0, $ivTx.Length)
    $propiedadesTx.Clear()
}
