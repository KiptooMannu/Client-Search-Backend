param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "client_133286@user.com",
    [string]$Password = "password123",
    [string]$JobId = "5249d13d-c810-4ac1-b55d-79e527f2cefd",
    [string]$PhoneNumber = "254700000001"
)

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        $Body = $null
    )
    $options = @{ Uri = $Url; Method = $Method; Headers = $Headers; ContentType = 'application/json'; ErrorAction = 'Stop' }
    if ($null -ne $Body) { $options.Body = $Body | ConvertTo-Json -Depth 10 }
    return Invoke-RestMethod @options
}

try {
    $login = Invoke-JsonRequest -Method 'POST' -Url "$BaseUrl/api/auth/login" -Body @{ email = $Email; password = $Password }
    Write-Host "Login succeeded: $($login.userId)"
    $token = $login.accessToken
    $body = @{ jobId = $JobId; phoneNumber = $PhoneNumber }
    try {
        $stk = Invoke-JsonRequest -Method 'POST' -Url "$BaseUrl/api/payments/mpesa/stkpush" -Headers @{ Authorization = "Bearer $token" } -Body $body
        Write-Host "STK response:"
        $stk | ConvertTo-Json -Depth 5 | Write-Host
    } catch {
        Write-Host "STK push failed: $($_.Exception.Message)"
        if ($_.Exception.Response -ne $null) {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            Write-Host "BODY:"; Write-Host ($reader.ReadToEnd())
        }
    }
} catch {
    Write-Host "Login failed: $($_.Exception.Message)"
    if ($_.Exception.Response -ne $null) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "BODY:"; Write-Host ($reader.ReadToEnd())
    }
}
