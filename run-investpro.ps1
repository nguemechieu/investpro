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

if ((-not $env:JDK_JAVA_OPTIONS -or $env:JDK_JAVA_OPTIONS -notmatch '(^| )--enable-preview( |$)') -and
    (-not $env:JAVA_TOOL_OPTIONS -or $env:JAVA_TOOL_OPTIONS -notmatch '(^| )--enable-preview( |$)')) {
    if ($env:JDK_JAVA_OPTIONS) {
        $env:JDK_JAVA_OPTIONS = "$env:JDK_JAVA_OPTIONS --enable-preview"
    } else {
        $env:JDK_JAVA_OPTIONS = "--enable-preview"
    }
}

& ".\\mvnw.cmd" "-q" "-DskipTests" "javafx:run"
