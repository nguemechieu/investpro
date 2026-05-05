#!/usr/bin/env pwsh
# Setup Coinbase Credentials for InvestPro
# This script sets up environment variables for Coinbase Advanced Trade API authentication
# 
# Usage: .\setup-coinbase-credentials.ps1
# 

$ErrorActionPreference = "Stop"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "InvestPro - Coinbase Credentials Setup" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Check if .env file already exists
$envFilePath = ".env"
if (Test-Path $envFilePath) {
    Write-Host "⚠️  .env file already exists!" -ForegroundColor Yellow
    Write-Host "This script will show you how to set environment variables." -ForegroundColor Yellow
    Write-Host ""
} else {
    Write-Host "✓ Creating .env file from template..." -ForegroundColor Green
    if (Test-Path ".env.example") {
        Copy-Item ".env.example" ".env"
        Write-Host "✓ .env file created from .env.example" -ForegroundColor Green
    } else {
        Write-Host "⚠️  .env.example not found. Creating .env manually..." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "STEP 1: Enter Your Coinbase Credentials" -ForegroundColor Cyan
Write-Host "Get these from: https://coinbase.com/settings/api" -ForegroundColor Gray
Write-Host ""

# Get credentials from user
$keyName = Read-Host "Enter your Key Name (organizations/.../apiKeys/...)" -AsSecureString
$privateKey = Read-Host "Enter your EC Private Key (paste entire key)" -AsSecureString

# Convert to plain text for environment variable
$keyNamePlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemAlloc($keyName))
$privateKeyPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemAlloc($privateKey))

Write-Host ""
Write-Host "STEP 2: Setting Environment Variables (Current Session)" -ForegroundColor Cyan
Write-Host ""

# Set environment variables for current session
[Environment]::SetEnvironmentVariable("COINBASE_KEY_NAME", $keyNamePlain, "Process")
[Environment]::SetEnvironmentVariable("COINBASE_PRIVATE_KEY", $privateKeyPlain, "Process")

Write-Host "✓ Environment variables set for this PowerShell session" -ForegroundColor Green
Write-Host ""

Write-Host "STEP 3: Save to .env File" -ForegroundColor Cyan
Write-Host ""

$saveToEnv = Read-Host "Save credentials to .env file for future sessions? (y/n)" -AsSecureString
$saveToEnvPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemAlloc($saveToEnv))

if ($saveToEnvPlain -eq "y") {
    # Read current .env if exists
    $envContent = if (Test-Path ".env") { Get-Content ".env" -Raw } else { "" }
    
    # Update or add COINBASE_KEY_NAME
    if ($envContent -match "COINBASE_KEY_NAME") {
        $envContent = $envContent -replace "COINBASE_KEY_NAME=.*", "COINBASE_KEY_NAME=$keyNamePlain"
    } else {
        $envContent += "`nCOINBASE_KEY_NAME=$keyNamePlain"
    }
    
    # Update or add COINBASE_PRIVATE_KEY
    if ($envContent -match "COINBASE_PRIVATE_KEY") {
        $envContent = $envContent -replace "COINBASE_PRIVATE_KEY=.*", "COINBASE_PRIVATE_KEY=$privateKeyPlain"
    } else {
        $envContent += "`nCOINBASE_PRIVATE_KEY=$privateKeyPlain"
    }
    
    $envContent | Set-Content ".env" -Encoding UTF8
    Write-Host "✓ Credentials saved to .env file" -ForegroundColor Green
    Write-Host ""
    Write-Host "⚠️  IMPORTANT: Keep .env file SECURE!" -ForegroundColor Yellow
    Write-Host "   - Add .env to .gitignore (if using git)" -ForegroundColor Gray
    Write-Host "   - Never commit .env to version control" -ForegroundColor Gray
    Write-Host "   - Restrict file permissions: icacls .env /grant ""%username%:F"" /inheritance:r" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "STEP 4: Load Environment Variables at Startup" -ForegroundColor Cyan
Write-Host ""
Write-Host "Option A - From PowerShell (for this session):" -ForegroundColor Gray
Write-Host "  Get-Content .env | ForEach-Object { `$parts = `$_ -split '='; if (`$parts[0]) { [Environment]::SetEnvironmentVariable(`$parts[0], `$parts[1], 'Process') } }" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Option B - Persistent System Environment Variables:" -ForegroundColor Gray
Write-Host "  1. Open Settings > Environment Variables" -ForegroundColor DarkGray
Write-Host "  2. Add new User variables:" -ForegroundColor DarkGray
Write-Host "     - COINBASE_KEY_NAME: $keyNamePlain" -ForegroundColor DarkGray
Write-Host "     - COINBASE_PRIVATE_KEY: $privateKeyPlain" -ForegroundColor DarkGray
Write-Host "  3. Restart IDE/terminal for changes to take effect" -ForegroundColor DarkGray
Write-Host ""

Write-Host "STEP 5: Verify Configuration" -ForegroundColor Cyan
Write-Host ""

$verifyKey = [Environment]::GetEnvironmentVariable("COINBASE_KEY_NAME", "Process")
$verifyPrivate = [Environment]::GetEnvironmentVariable("COINBASE_PRIVATE_KEY", "Process")

if ($verifyKey) {
    Write-Host "✓ COINBASE_KEY_NAME is set (length: $($verifyKey.Length) chars)" -ForegroundColor Green
} else {
    Write-Host "✗ COINBASE_KEY_NAME is NOT set" -ForegroundColor Red
}

if ($verifyPrivate) {
    Write-Host "✓ COINBASE_PRIVATE_KEY is set (length: $($verifyPrivate.Length) chars)" -ForegroundColor Green
} else {
    Write-Host "✗ COINBASE_PRIVATE_KEY is NOT set" -ForegroundColor Red
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Setup Complete!" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "1. Start InvestPro application" -ForegroundColor Gray
Write-Host "2. Select 'COINBASE' from the exchange dropdown" -ForegroundColor Gray
Write-Host "3. Leave API Key/Secret fields empty (uses environment variables)" -ForegroundColor Gray
Write-Host "4. Or enter custom credentials in the UI fields if needed" -ForegroundColor Gray
Write-Host ""

# Clear sensitive data from memory
$keyNamePlain = $null
$privateKeyPlain = $null
[GC]::Collect()
