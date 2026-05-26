[System.Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPlainTextForPassword', '')]
param(
    [string]$BaseUrl = "https://client-search-backend.onrender.com",
    [string]$AdminEmail = "admin@kazikonnect.com",
    [string]$AdminPassword = "admin123",
    [string]$ClientEmail = "client@user.com",
    [string]$ClientPassword = "password123",
    [string]$WorkerEmail = "worker4@kazikonnect.com",
    [string]$WorkerPassword = "password123"
)

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        $Body = $null
    )
    $options = @{ Uri = $Url; Method = $Method; Headers = $Headers; ContentType = 'application/json'; ErrorAction = 'Stop' }
    if ($null -ne $Body) {
        $options.Body = $Body | ConvertTo-Json -Depth 10
    }
    return Invoke-RestMethod @options
}

function LoginOrRegister {
    [System.Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingPlainTextForPassword', '')]
    param(
        [string]$Email,
        [string]$Password,
        [string]$Role,
        [string]$FirstName,
        [string]$SecondName
    )

    $loginUrl = "$BaseUrl/api/auth/login"
    $registerUrl = "$BaseUrl/api/auth/register"
    try {
        Write-Host "Logging in user $Email..."
        $loginResponse = Invoke-JsonRequest -Method 'POST' -Url $loginUrl -Body @{ email = $Email; password = $Password }
        return $loginResponse
    }
    catch {
        Write-Host "Login failed, registering $Email..."
        $userName = ($Email -split '@')[0]
        $registerPayload = @{ username = $userName; email = $Email; password = $Password; firstName = $FirstName; secondName = $SecondName; role = $Role }
        $registerResponse = Invoke-JsonRequest -Method 'POST' -Url $registerUrl -Body $registerPayload
        Write-Host ("Registered {0}: {1}" -f $Email, $registerResponse)
        return Invoke-JsonRequest -Method 'POST' -Url $loginUrl -Body @{ email = $Email; password = $Password }
    }
}

function CreateWorkerProfile {
    param(
        [string]$Token,
        [string]$Email
    )
    $url = "$BaseUrl/api/workers/profile?email=$Email"
    $headers = @{ Authorization = "Bearer $Token" }
    $body = @{
        fullName        = "Automation Worker"
        phoneNumber     = "254700000001"
        location        = "Nairobi"
        experienceYears = 2
        bio             = "Automated test worker profile."
        hourlyRate      = 1500
        category        = "General"
        skills          = @("Plumbing", "Electrical")
    }
    return Invoke-JsonRequest -Method 'POST' -Url $url -Headers $headers -Body $body
}

function ApproveWorkerProfile {
    param(
        [string]$AdminToken,
        [string]$WorkerProfileId,
        [string]$AdminUserId
    )
    $url = "$BaseUrl/api/admin/workers/$WorkerProfileId/approve?adminId=$AdminUserId"
    $headers = @{ Authorization = "Bearer $AdminToken" }
    return Invoke-JsonRequest -Method 'PUT' -Url $url -Headers $headers
}

function CreateJobRequest {
    param(
        [string]$ClientToken,
        [string]$ClientId,
        [string]$WorkerUserId
    )
    $url = "$BaseUrl/api/jobs/request?clientId=$ClientId&workerUserId=$WorkerUserId"
    $headers = @{ Authorization = "Bearer $ClientToken" }
    $body = @{ description = "Test job for automated payment flow"; location = "Nairobi"; scheduledDate = [DateTime]::UtcNow.AddDays(3).ToString("yyyy-MM-ddTHH:mm:ss") }
    return Invoke-JsonRequest -Method 'POST' -Url $url -Headers $headers -Body $body
}

function InitiateStkPush {
    param(
        [string]$ClientToken,
        [string]$JobId,
        [string]$PhoneNumber
    )
    $url = "$BaseUrl/api/payments/mpesa/stkpush?jobId=$JobId&phoneNumber=$PhoneNumber"
    $headers = @{ Authorization = "Bearer $ClientToken" }
    return Invoke-JsonRequest -Method 'POST' -Url $url -Headers $headers
}

function SimulateMpesaCallback {
    param(
        [string]$CheckoutRequestId
    )
    $url = "$BaseUrl/api/payments/mpesa/callback"
    $body = @{
        Body = @{
            stkCallback = @{
                MerchantRequestID = "MR-$(Get-Random -Maximum 999999)"
                CheckoutRequestID = $CheckoutRequestId
                ResultCode        = 0
                ResultDesc        = "The service request is processed successfully."
                CallbackMetadata  = @{
                    Item = @(
                        @{ Name = "Amount"; Value = 1500 }
                        @{ Name = "MpesaReceiptNumber"; Value = "ABC123XYZ" }
                        @{ Name = "PhoneNumber"; Value = "254700000001" }
                    )
                }
            }
        }
    }
    return Invoke-JsonRequest -Method 'POST' -Url $url -Body $body
}

function GetPaymentStatus {
    param(
        [string]$Token,
        [string]$JobId
    )
    $url = "$BaseUrl/api/payments/status/$JobId"
    $headers = @{ Authorization = "Bearer $Token" }
    return Invoke-JsonRequest -Method 'GET' -Url $url -Headers $headers
}

function ReleaseEscrow {
    param(
        [string]$Token,
        [string]$JobId
    )
    $url = "$BaseUrl/api/payments/escrow/release?jobId=$JobId"
    $headers = @{ Authorization = "Bearer $Token" }
    return Invoke-JsonRequest -Method 'POST' -Url $url -Headers $headers
}

# Start script
$admin = LoginOrRegister -Email $AdminEmail -Password $AdminPassword -Role 'ADMIN' -FirstName 'Admin' -SecondName 'User'
$client = LoginOrRegister -Email $ClientEmail -Password $ClientPassword -Role 'CLIENT' -FirstName 'Client' -SecondName 'User'
$worker = LoginOrRegister -Email $WorkerEmail -Password $WorkerPassword -Role 'WORKER' -FirstName 'Worker' -SecondName 'User'

Write-Host "Admin userId: $($admin.userId)"
Write-Host "Client userId: $($client.userId)"
Write-Host "Worker userId: $($worker.userId)"

$workerProfile = CreateWorkerProfile -Token $worker.accessToken -Email $WorkerEmail
Write-Host "Worker profile created: $($workerProfile.profile.id)"

$approval = ApproveWorkerProfile -AdminToken $admin.accessToken -WorkerProfileId $workerProfile.profile.id -AdminUserId $admin.userId
Write-Host "Worker profile approved: $approval"

$job = CreateJobRequest -ClientToken $client.accessToken -ClientId $client.userId -WorkerUserId $worker.userId
Write-Host "Job created: $($job.id)"

$stk = InitiateStkPush -ClientToken $client.accessToken -JobId $job.id -PhoneNumber "254700000001"
Write-Host "STK push response: $($stk | ConvertTo-Json -Depth 5)"

$checkoutRequestId = if ($stk.checkoutRequestId) { $stk.checkoutRequestId } else { "TEST-CHECKOUT-$(Get-Random -Maximum 999999)" }
Write-Host "Using CheckoutRequestID: $checkoutRequestId"

$callback = SimulateMpesaCallback -CheckoutRequestId $checkoutRequestId
Write-Host "Callback response: $($callback | ConvertTo-Json -Depth 5)"

$status = GetPaymentStatus -Token $client.accessToken -JobId $job.id
Write-Host "Payment status: $($status | ConvertTo-Json -Depth 5)"

$release = ReleaseEscrow -Token $client.accessToken -JobId $job.id
Write-Host "Release response: $($release | ConvertTo-Json -Depth 5)"
