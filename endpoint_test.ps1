# Backend Endpoint Test Suite
$baseUrl = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json" }

Write-Host "=== Testing Backend Endpoints ===" -ForegroundColor Cyan

# Test 1: Login to get token
Write-Host "`nStep 1: Authenticating..." -ForegroundColor Yellow
$loginBody = @{ email = "admin@nestfind.com"; password = "admin123" } | ConvertTo-Json
try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -Headers $headers -ErrorAction Stop
    $token = $loginResponse.token
    Write-Host "[OK] Login successful" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] Login error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Setup auth headers
$authHeaders = @{ 
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
}

# Test 2: Test admin/workers/pending endpoint
Write-Host "`nStep 2: Testing GET /api/admin/workers/pending..." -ForegroundColor Yellow
try {
    $pendingResponse = Invoke-RestMethod -Uri "$baseUrl/admin/workers/pending" -Method Get -Headers $authHeaders -ErrorAction Stop
    Write-Host "[OK] Endpoint responded successfully" -ForegroundColor Green
    Write-Host "    Pending workers count: $($pendingResponse.Count)" -ForegroundColor Cyan
    if ($pendingResponse.Count -gt 0) {
        Write-Host "    Sample worker: $($pendingResponse[0].fullName)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "[FAIL] Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 3: Test admin/workers (all)
Write-Host "`nStep 3: Testing GET /api/admin/workers..." -ForegroundColor Yellow
try {
    $allWorkersResponse = Invoke-RestMethod -Uri "$baseUrl/admin/workers" -Method Get -Headers $authHeaders -ErrorAction Stop
    Write-Host "[OK] Endpoint responded successfully" -ForegroundColor Green
    Write-Host "    Total workers count: $($allWorkersResponse.Count)" -ForegroundColor Cyan
} catch {
    Write-Host "[FAIL] Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 4: Test admin/users
Write-Host "`nStep 4: Testing GET /api/admin/users..." -ForegroundColor Yellow
try {
    $usersResponse = Invoke-RestMethod -Uri "$baseUrl/admin/users" -Method Get -Headers $authHeaders -ErrorAction Stop
    Write-Host "[OK] Endpoint responded successfully" -ForegroundColor Green
    Write-Host "    Total users count: $($usersResponse.Count)" -ForegroundColor Cyan
} catch {
    Write-Host "[FAIL] Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 5: Test admin/clients
Write-Host "`nStep 5: Testing GET /api/admin/clients..." -ForegroundColor Yellow
try {
    $clientsResponse = Invoke-RestMethod -Uri "$baseUrl/admin/clients" -Method Get -Headers $authHeaders -ErrorAction Stop
    Write-Host "[OK] Endpoint responded successfully" -ForegroundColor Green
    Write-Host "    Total clients count: $($clientsResponse.Count)" -ForegroundColor Cyan
} catch {
    Write-Host "[FAIL] Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 6: Test marketplace search
Write-Host "`nStep 6: Testing GET /api/marketplace/search..." -ForegroundColor Yellow
try {
    $marketplaceResponse = Invoke-RestMethod -Uri "$baseUrl/marketplace/search" -Method Get -Headers $authHeaders -ErrorAction Stop
    Write-Host "[OK] Endpoint responded successfully" -ForegroundColor Green
    Write-Host "    Available workers count: $($marketplaceResponse.Count)" -ForegroundColor Cyan
} catch {
    Write-Host "[FAIL] Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== Test Complete ===" -ForegroundColor Green
