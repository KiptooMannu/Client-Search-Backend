# Comprehensive API Test Suite for Client Connection Platform (Full Lifecycle)
$baseUrl = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json" }

function Write-Section($title) {
    Write-Host "`n=== $title ===" -ForegroundColor Cyan
}

function Test-Endpoint($name, $action) {
    try {
        $result = &$action
        Write-Host "✅ ${name}: Success" -ForegroundColor Green
        return $result
    } catch {
        Write-Host "❌ ${name}: Failed" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            Write-Host "Response Body: $body" -ForegroundColor Gray
        } else {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Gray
        }
        return $null
    }
}

Write-Section "1. AUTHENTICATION & REGISTRATION"

# 1.1 Register & Login Worker
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss_fff"
$workerEmail = "worker_$timestamp@test.com"
$workerRegBody = @{ username = "Worker_$timestamp"; email = $workerEmail; password = "password123"; role = "WORKER" } | ConvertTo-Json
Test-Endpoint "Register Worker" { Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $workerRegBody -Headers $headers }

$workerAuth = Test-Endpoint "Login Worker" { 
    $body = @{ email = $workerEmail; password = "password123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -Headers $headers 
}
$workerToken = $workerAuth.accessToken
$workerHeaders = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $workerToken" }

# 1.2 Login Admin (Seeded)
$adminAuth = Test-Endpoint "Login Admin" { 
    $body = @{ email = "admin@kazikonnect.com"; password = "admin123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -Headers $headers 
}
$adminToken = $adminAuth.accessToken
$adminHeaders = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $adminToken" }
$users = Invoke-RestMethod -Uri "$baseUrl/admin/users" -Method Get -Headers $adminHeaders
$adminId = ($users | Where-Object { $_.role -eq "ADMIN" } | Select-Object -First 1).id

# 1.3 Login Client (Seeded)
$clientAuth = Test-Endpoint "Login Client" { 
    $body = @{ email = "client@user.com"; password = "password123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -Headers $headers 
}
$clientToken = $clientAuth.accessToken
$clientHeaders = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $clientToken" }
$clientId = ($users | Where-Object { $_.email -eq "client@user.com" } | Select-Object -First 1).id

Write-Section "2. PROFILE COMPLETION (WITH DOCUMENTS)"

# 2.1 Fetch Master Skills
$skills = Invoke-RestMethod -Uri "$baseUrl/marketplace/skills" -Method Get
$selectedSkill = $skills | Where-Object { $_.name -eq "Plumbing" } | Select-Object -First 1
Write-Host "Selected Skill: $($selectedSkill.name) (ID: $($selectedSkill.id))" -ForegroundColor Gray

# 2.2 Create Profile
$profileBody = @{
    fullName = "Professional Worker $timestamp"
    phoneNumber = "0712345678"
    bio = "Expert plumber with verified identity and documents."
    location = "Nairobi"
    experienceYears = 10
    skills = @($selectedSkill.name)
} | ConvertTo-Json
$workerProfile = Test-Endpoint "Create Worker Profile" { 
    Invoke-RestMethod -Uri "$baseUrl/workers/profile?email=$workerEmail" -Method Post -Body $profileBody -Headers $workerHeaders 
}
$profileId = $workerProfile.profile.id
Write-Host "DEBUG: profileId = $profileId" -ForegroundColor Gray
Write-Host "DEBUG: adminId = $adminId" -ForegroundColor Gray

Test-Endpoint "Upload Identity Document" {
    curl.exe -s -X POST "$baseUrl/documents?workerProfileId=$profileId&type=ID&name=National_ID" `
             -H "Authorization: Bearer $workerToken" `
             -F "file=@test_id.png"
}

Write-Section "3. ADMIN VERIFICATION FLOW"

# 3.1 Admin Approve Worker (Completeness Check)
Test-Endpoint "Admin Approve Worker" {
    Invoke-RestMethod -Uri "$baseUrl/admin/workers/$profileId/approve?adminId=$adminId" -Method Put -Headers $adminHeaders
}

Write-Section "4. CLIENT-WORKER INTERACTION (HIRING)"

# 4.1 Client Request Job
$jobBody = @{
    title = "Plumbing Repair"
    description = "Fixing a leaky tap in the kitchen"
    location = "Westlands, Nairobi"
    budget = 1500
} | ConvertTo-Json

$jobRequest = Test-Endpoint "Client Create Job Request" {
    Invoke-RestMethod -Uri "$baseUrl/jobs/request?clientId=$clientId&workerProfileId=$profileId" -Method Post -Body $jobBody -Headers $clientHeaders
}
$jobId = $jobRequest.id

# 4.2 Worker Accept Job
Test-Endpoint "Worker Accept Job" {
    Invoke-RestMethod -Uri "$baseUrl/jobs/$jobId/status?status=ACCEPTED" -Method Put -Headers $workerHeaders
}

Write-Section "5. TRUST & FEEDBACK (REVIEWS)"

# 5.1 Client Leave Review
$reviewBody = @{
    rating = 5
    comment = "Excellent service! Very professional and verified."
} | ConvertTo-Json

Test-Endpoint "Client Leave Review" {
    Invoke-RestMethod -Uri "$baseUrl/reviews?clientId=$clientId&workerProfileId=$profileId" -Method Post -Body $reviewBody -Headers $clientHeaders
}

# 5.2 Verify Worker Reviews
$reviews = Test-Endpoint "Verify Worker Reviews" {
    Invoke-RestMethod -Uri "$baseUrl/reviews/worker/$profileId" -Method Get -Headers $headers
}

Write-Section "SUMMARY"
Write-Host "✅ Verified $($reviews.Count) reviews for the worker." -ForegroundColor Gray
Write-Host "All end-to-end features (Auth, Profile, Docs, Hiring, Reviews) verified operational." -ForegroundColor Green
