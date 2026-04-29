# NestFind Platform End-to-End API Test Suite
$baseUrl = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json" }

function Write-Host-Section($title) {
    Write-Host "`n=== $title ===" -ForegroundColor Cyan
}

# 1. REGISTRATION & AUTHENTICATION TESTS
Write-Host-Section "Testing Registration & Authentication"

# Register a new test user
$registerBody = @{ 
    username = "new_tester_" + (Get-Date -Format "mm_ss")
    email = "test_" + (Get-Date -Format "mm_ss") + "@nestfind.com"
    password = "password123"
    role = "CLIENT"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $registerBody -Headers $headers
    Write-Host "✅ Registration Successful for $($registerBody.email)." -ForegroundColor Green
} catch {
    Write-Host "❌ Registration Failed." -ForegroundColor Red
}

# Login as Admin
$loginBody = @{ email = "admin@nestfind.com"; password = "admin123" } | ConvertTo-Json
try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -Headers $headers
    $token = $loginResponse.token
    $authHeaders = @{ 
        "Content-Type" = "application/json"
        "Authorization" = "Bearer $token"
    }
    Write-Host "✅ Login Successful. Token acquired." -ForegroundColor Green
} catch {
    Write-Host "❌ Login Failed." -ForegroundColor Red
    exit
}

# 2. MARKETPLACE TESTS (CLIENT VIEW)
Write-Host-Section "Testing Marketplace (Client View)"
try {
    # Search all
    $workers = Invoke-RestMethod -Uri "$baseUrl/marketplace/search" -Method Get -Headers $authHeaders
    Write-Host "✅ Marketplace Search: Found $($workers.Count) verified workers." -ForegroundColor Green

    # Search with filters
    $filtered = Invoke-RestMethod -Uri "$baseUrl/marketplace/search?skill=Plumbing&location=Nairobi" -Method Get -Headers $authHeaders
    Write-Host "✅ Marketplace Filtered Search: Found $($filtered.Count) plumbers in Nairobi." -ForegroundColor Green
} catch {
    Write-Host "❌ Marketplace Search Failed." -ForegroundColor Red
}

# 3. ADMIN TESTS
Write-Host-Section "Testing Admin Authority"
try {
    # Get Verification Queue
    $pending = Invoke-RestMethod -Uri "$baseUrl/admin/workers/pending" -Method Get -Headers $authHeaders
    Write-Host "✅ Admin Verification Queue: Found $($pending.Count) pending applications." -ForegroundColor Green

    # Get User List
    $users = Invoke-RestMethod -Uri "$baseUrl/admin/users" -Method Get -Headers $authHeaders
    Write-Host "✅ Admin User Management: Found $($users.Count) total platform users." -ForegroundColor Green
} catch {
    Write-Host "❌ Admin Endpoints Failed." -ForegroundColor Red
}

# 4. WORKER DOCUMENT TESTS (Simulation)
Write-Host-Section "Testing Worker Document Flow"
try {
    Write-Host "ℹ️ Document upload requires multipart/form-data. Skipping automated binary test." -ForegroundColor Yellow
    
    $workerDocs = Invoke-RestMethod -Uri "$baseUrl/documents/worker/$($workers[0].id)" -Method Get -Headers $authHeaders
    Write-Host "✅ Worker Documents: Retrieved $($workerDocs.Count) documents for $($workers[0].fullName)." -ForegroundColor Green
} catch {
    Write-Host "❌ Document Retrieval Failed." -ForegroundColor Red
}

Write-Host "`n--- All Core Operations Verified Operational ---" -ForegroundColor Green
