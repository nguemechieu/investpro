param(
    [switch]$StartPredictor,
    [string]$PredictorHost = "localhost",
    [int]$PredictorPort = 50051,
    [string]$Python = ""
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\\java.exe"))) {
    $jdkRoot = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path $jdkRoot) {
        $candidate = Get-ChildItem $jdkRoot -Directory |
            Where-Object { $_.Name -like 'corretto-23*' -and (Test-Path (Join-Path $_.FullName "bin\\java.exe")) } |
            Select-Object -First 1

        if (-not $candidate) {
            $candidate = Get-ChildItem $jdkRoot -Directory |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\\java.exe") } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        }

        if ($candidate) {
            $env:JAVA_HOME = $candidate.FullName
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\\java.exe"))) {
    throw "JAVA_HOME is not set and no JDK was found under $env:USERPROFILE\\.jdks."
}

$env:PATH = (Join-Path $env:JAVA_HOME "bin") + [IO.Path]::PathSeparator + $env:PATH

if ((-not $env:JDK_JAVA_OPTIONS -or $env:JDK_JAVA_OPTIONS -notmatch '(^| )-Dprism\.order=') -and
    (-not $env:JAVA_TOOL_OPTIONS -or $env:JAVA_TOOL_OPTIONS -notmatch '(^| )-Dprism\.order=')) {
    if ($env:JDK_JAVA_OPTIONS) {
        $env:JDK_JAVA_OPTIONS = "$env:JDK_JAVA_OPTIONS -Dprism.order=sw"
    } else {
        $env:JDK_JAVA_OPTIONS = "-Dprism.order=sw"
    }
}

$aiOptions = "-Dinvestpro.ai.host=$PredictorHost -Dinvestpro.ai.port=$PredictorPort"
if ($env:JDK_JAVA_OPTIONS) {
    $env:JDK_JAVA_OPTIONS = "$env:JDK_JAVA_OPTIONS $aiOptions"
} else {
    $env:JDK_JAVA_OPTIONS = $aiOptions
}

$predictorProcess = $null
try {
    if ($StartPredictor) {
        if (-not $Python) {
            $venvPython = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
            if (Test-Path $venvPython) {
                $Python = $venvPython
            } else {
                $Python = "python"
            }
        }
        $predictorArgs = @("-m", "predictor", "--host", $PredictorHost, "--port", "$PredictorPort")
        $predictorProcess = Start-Process -FilePath $Python -ArgumentList $predictorArgs -PassThru
        Start-Sleep -Seconds 2
    }

    & ".\\mvnw.cmd" "-q" "-DskipTests" "javafx:run"
} finally {
    if ($predictorProcess -and -not $predictorProcess.HasExited) {
        Stop-Process -Id $predictorProcess.Id
    }
}
