# Installe la base PostgreSQL de DataShare (Windows / PowerShell).
# Usage : .\database\setup.ps1 [-DbName datashare] [-DbUser datashare] [-DbPassword datashare] [-AdminUser postgres]
param(
  [string]$DbName = "datashare",
  [string]$DbUser = "datashare",
  [string]$DbPassword = "datashare",
  [string]$AdminUser = "postgres"
)
$ErrorActionPreference = "Stop"
$sql = Join-Path $PSScriptRoot "setup.sql"
Get-Content $sql -Raw | psql -U $AdminUser -d postgres -v "db_name=$DbName" -v "db_user=$DbUser" -v "db_password=$DbPassword" -f -
if ($LASTEXITCODE -ne 0) { throw "La création de la base a échoué." }
Write-Host "Base prête. Le schéma sera créé par Flyway au premier démarrage de l'API."
