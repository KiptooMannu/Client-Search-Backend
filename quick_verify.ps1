# Quick verification of restored endpoints
$baseUrl = "http://localhost:8080/api"

Write-Host "Checking Notification endpoint..."
try {
    $res = Invoke-WebRequest -Uri "$baseUrl/notifications/user/00000000-0000-0000-0000-000000000000" -Method Get -TimeoutSec 2
    Write-Host "✅ Notification endpoint reachable (Status: $($res.StatusCode))" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✅ Notification endpoint restored (Returned 401 as expected for no token)" -ForegroundColor Green
    } else {
        Write-Host "❌ Notification endpoint failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "Checking Messages endpoint..."
try {
    $res = Invoke-WebRequest -Uri "$baseUrl/messages/contacts" -Method Get -TimeoutSec 2
    Write-Host "✅ Messages endpoint reachable (Status: $($res.StatusCode))" -ForegroundColor Green
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "✅ Messages endpoint restored (Returned 401 as expected for no token)" -ForegroundColor Green
    } else {
        Write-Host "❌ Messages endpoint failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}
