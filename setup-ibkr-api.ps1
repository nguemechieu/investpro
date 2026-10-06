$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot 'lib/ibkr'
$downloadDirectory = Join-Path $PSScriptRoot 'output/ibkr-sdk-install'
New-Item -ItemType Directory -Force -Path $destination, $downloadDirectory | Out-Null
$archive = Join-Path $downloadDirectory 'twsapi-1050.02.zip'
Invoke-WebRequest 'https://interactivebrokers.github.io/downloads/twsapi_macunix.1050.02.zip' -OutFile $archive
Expand-Archive -LiteralPath $archive -DestinationPath $downloadDirectory -Force
$sdkRoot = Join-Path $downloadDirectory 'IBJts'
$apiJar = Get-ChildItem -LiteralPath $sdkRoot -Filter 'TwsApi.jar' -Recurse | Select-Object -First 1
if ($null -eq $apiJar) { throw 'Official SDK archive does not contain TwsApi.jar.' }
Copy-Item -LiteralPath $apiJar.FullName -Destination (Join-Path $destination 'TwsApi.jar')
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/com/google/protobuf/protobuf-java/4.29.5/protobuf-java-4.29.5.jar' -OutFile (Join-Path $destination 'protobuf-java-4.29.5.jar')
Copy-Item -LiteralPath (Join-Path $sdkRoot 'LICENSE') -Destination (Join-Path $destination 'LICENSE')
Write-Host "Official IBKR API installed in $destination. Restart InvestPro to load it."
