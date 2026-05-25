$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
$MVN="C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd"

# Load environment variables from .env if present
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path $envFile) {
	Write-Host "Loading environment variables from $envFile" -ForegroundColor Yellow
	Get-Content $envFile | ForEach-Object {
		if (-not [string]::IsNullOrWhiteSpace($_) -and -not $_.TrimStart().StartsWith('#')) {
			$parts = $_ -split '=', 2
			if ($parts.Count -eq 2) {
				$name = $parts[0].Trim()
				$value = $parts[1].Trim()
				if ($name) {
					# Build provider path as a string to avoid parser issues with Env:<name>
					$envPath = "Env:${name}"
					Set-Item -Path $envPath -Value $value -Force
				}
			}
		}
	}
}

Write-Host "Starting Kazi Konnect Backend in TEST (H2) mode..." -ForegroundColor Cyan
& $MVN spring-boot:run "-Dspring-boot.run.profiles=test"
