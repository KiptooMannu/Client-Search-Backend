# 202 Status Verification Script - Kazi Konnect
# Confirms all successful API responses return HTTP 202 Accepted
$baseUrl = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json" }
$pass = 0
$fail = 0

function Test-Status {
    param($name, $expectedCode, [scriptblock]$scriptBlock)
    try {
        $response = & $scriptBlock
        $code = $response.StatusCode
        if ($code -eq $expectedCode) {
            Write-Host "[PASS] $name => $code (expected $expectedCode)" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "[FAIL] $name => $code (expected $expectedCode)" -ForegroundColor Red
            $script:fail++
        }
    } catch {
        $code = 0
        if ($null -ne $_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq $expectedCode) {
            Write-Host "[PASS] $name => $code (expected $expectedCode)" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "[FAIL] $name => $code (expected $expectedCode) -- $($_.Exception.Message)" -ForegroundColor Red
            $script:fail++
        }
    }
}

# 1. Register (should return 202)
$ts      = Get-Date -Format "HHmmss_fff"
$regBody = @{ username="verifier_$ts"; email="verifier_$ts@test.com"; password="Test1234!"; role="CLIENT" } | ConvertTo-Json

Test-Status "POST /auth/register -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/auth/register" -Method Post -Body $regBody -Headers $headers -ErrorAction SilentlyContinue
}

# 2. Login (should return 202)
$loginBody = @{ email="admin@kazikonnect.com"; password="admin123" } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -Headers $headers
$token     = $loginResp.accessToken
$userId    = $loginResp.userId
$authH     = @{ "Content-Type"="application/json"; "Authorization"="Bearer $token" }

Test-Status "POST /auth/login -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -Headers $headers -ErrorAction SilentlyContinue
}

# 3. Marketplace Search (should return 202)
Test-Status "GET /marketplace/search -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/marketplace/search" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

Test-Status "GET /marketplace/skills -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/marketplace/skills" -Method Get -ErrorAction SilentlyContinue
}

# 4. Admin Endpoints (should return 202)
Test-Status "GET /admin/users -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/admin/users" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

Test-Status "GET /admin/workers/pending -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/admin/workers/pending" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

# 5. Notifications - Own User (should return 202)
Test-Status "GET /notifications/user/{id} -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/notifications/user/$userId" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

Test-Status "GET /notifications/user/{id}/unread-count -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/notifications/user/$userId/unread-count" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

# 6. Messages - Own User (should return 202)
Test-Status "GET /messages/user/{id}/recent -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/messages/user/$userId/recent" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

Test-Status "GET /messages/contacts -> 202" 202 {
    Invoke-WebRequest -Uri "$baseUrl/messages/contacts" -Method Get -Headers $authH -ErrorAction SilentlyContinue
}

# 7. SECURITY: Foreign user must be denied (should return 403)
$fakeId = "00000000-0000-0000-0000-000000000001"

Test-Status "GET /notifications/user/{otherId} -> 403 (IDOR blocked)" 403 {
    Invoke-WebRequest -Uri "$baseUrl/notifications/user/$fakeId" -Method Get -Headers $authH -ErrorAction Stop
}

Test-Status "GET /messages/user/{otherId}/recent -> 403 (IDOR blocked)" 403 {
    Invoke-WebRequest -Uri "$baseUrl/messages/user/$fakeId/recent" -Method Get -Headers $authH -ErrorAction Stop
}

# Summary
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  RESULTS: $pass passed  |  $fail failed" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================" -ForegroundColor Cyan
if ($fail -eq 0) {
    Write-Host "  All endpoints return 202 on success." -ForegroundColor Green
    Write-Host "  IDOR attacks return 403 as expected." -ForegroundColor Green
}
