Get-Content .env | Where-Object { $_ -notmatch '^\s*#' -and $_ -like '*=*' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), [System.EnvironmentVariableTarget]::Process)
}
C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd test -Dtest=com.kazikonnect.backend.DbPrintTest
