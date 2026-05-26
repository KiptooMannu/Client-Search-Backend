# Redefine the function to call STK push and capture response details
$scriptPath = "c:\Users\User\Desktop\Web app\backend\scripts\run-mpesa-payment-harness.ps1"
$lines = Get-Content $scriptPath
$functionsOnly = @()
foreach ($line in $lines) {
    if ($line -like "*# Start script*") {
        break
    }
    $functionsOnly += $line
}
$functionsCode = $functionsOnly -join "`n"
Invoke-Expression $functionsCode

$BaseUrl = "https://client-search-backend.onrender.com"
$AdminEmail = "admin@kazikonnect.com"
$AdminPassword = "admin123"
$ClientEmail = "client@user.com"
$ClientPassword = "password123"
$WorkerEmail = "worker4@kazikonnect.com"
$WorkerPassword = "password123"

$suffix = Get-Random -Minimum 100000 -Maximum 999999
$resolvedClientEmail = "client_$suffix@user.com"
$resolvedWorkerEmail = $WorkerEmail

$admin = LoginOrRegister -Email $AdminEmail -Password $AdminPassword -Role 'ADMIN' -FirstName 'Admin' -SecondName 'User'
$client = LoginOrRegister -Email $resolvedClientEmail -Password $ClientPassword -Role 'CLIENT' -FirstName 'Client' -SecondName 'User'
$worker = LoginOrRegister -Email $resolvedWorkerEmail -Password $WorkerPassword -Role 'WORKER' -FirstName 'Worker' -SecondName 'User'

# Create and approve profile
$workerProfile = CreateWorkerProfile -Token $worker.accessToken -Email $resolvedWorkerEmail
$approval = ApproveWorkerProfile -AdminToken $admin.accessToken -WorkerProfileId $workerProfile.profile.id -AdminUserId $admin.userId

# Create Job Request
$job = CreateJobRequest -ClientToken $client.accessToken -ClientId $client.userId -WorkerUserId $worker.userId
Write-Host "Created Job: $($job.id)"

# Try STK push and print exact response body
$url = "$BaseUrl/api/payments/mpesa/stkpush?jobId=$($job.id)&phoneNumber=254700000001"
$headers = @{ Authorization = "Bearer $($client.accessToken)" }

Write-Host "Calling STK push..."
try {
    $r = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -ContentType 'application/json'
    Write-Host "STK Push Success: $($r | ConvertTo-Json -Depth 5)"
} catch {
    Write-Host "STK Push Failed: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Response Body: $($reader.ReadToEnd())"
    }
}
