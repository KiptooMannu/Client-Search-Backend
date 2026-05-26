$loginUrl = "https://client-search-backend.onrender.com/api/auth/login"
$body = @{ email = 'admin@kazikonnect.com'; password = 'admin123' } | ConvertTo-Json

Write-Host "Sending POST to $loginUrl with body: $body"
try {
    $r = Invoke-RestMethod -Uri $loginUrl -Method Post -Body $body -ContentType 'application/json'
    Write-Host "Success! Token: $($r.accessToken)"
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Response: $($reader.ReadToEnd())"
    }
}
