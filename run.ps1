$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
$MVN="C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd"
Write-Host "Starting Kazi Konnect Backend in TEST (H2) mode..." -ForegroundColor Cyan
& $MVN spring-boot:run "-Dspring-boot.run.profiles=test"
