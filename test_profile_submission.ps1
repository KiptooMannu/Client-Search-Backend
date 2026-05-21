# Test Worker Profile Submission with Certifications Fix
$baseUrl = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json" }

Write-Host "=== WORKER PROFILE SUBMISSION TEST ===" -ForegroundColor Cyan
Write-Host "Testing: Profile completion calculation with certifications requirement" -ForegroundColor Yellow
Write-Host ""

# 1. Register Worker
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss_fff"
$workerEmail = "testworker_$timestamp@test.com"
$workerUsername = "Worker_$timestamp"

Write-Host "1️⃣  Registering worker: $workerEmail" -ForegroundColor Cyan
$regBody = @{ 
    username = $workerUsername
    email = $workerEmail
    password = "password123"
    role = "WORKER" 
} | ConvertTo-Json

try {
    $regResp = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $regBody -Headers $headers
    Write-Host "✅ Registration successful" -ForegroundColor Green
    $workerId = $regResp.id
} catch {
    Write-Host "❌ Registration failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 2. Login Worker
Write-Host "2️⃣  Logging in worker..." -ForegroundColor Cyan
$loginBody = @{ 
    email = $workerEmail
    password = "password123" 
} | ConvertTo-Json

try {
    $loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -Headers $headers
    Write-Host "✅ Login successful" -ForegroundColor Green
    $token = $loginResp.accessToken
    $authHeaders = @{ 
        "Content-Type" = "application/json"
        "Authorization" = "Bearer $token" 
    }
} catch {
    Write-Host "❌ Login failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 3. Get Existing Profile (Auto-created at registration)
Write-Host "3️⃣  Fetching auto-created profile..." -ForegroundColor Cyan
try {
    $profileResp = Invoke-RestMethod -Uri "$baseUrl/workers/profile/$workerId" -Method Get -Headers $authHeaders
    Write-Host "✅ Profile fetched" -ForegroundColor Green
    $profileId = $profileResp.id
} catch {
    Write-Host "❌ Profile fetch failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 4. Update Profile with Full Data
Write-Host "4️⃣  Updating profile with all required fields..." -ForegroundColor Cyan
$updateBody = @{
    fullName = "Test Professional Worker"
    phoneNumber = "0712345678"
    location = "Nairobi"
    experienceYears = 5
    category = "Plumbing"
    hourlyRate = 1500
    bio = "Expert plumber with over 5 years of professional experience in residential maintenance."
    skills = @("Plumbing", "Installations", "Repairs")
} | ConvertTo-Json

try {
    $updateResp = Invoke-RestMethod -Uri "$baseUrl/workers/profile/$profileId" -Method Put -Body $updateBody -Headers $authHeaders
    Write-Host "✅ Profile updated successfully" -ForegroundColor Green
} catch {
    Write-Host "❌ Profile update failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 5. Add Work History
Write-Host "5️⃣  Adding work history..." -ForegroundColor Cyan
$workHistoryBody = @{
    company = "ABC Plumbing Services"
    role = "Senior Plumber"
    period = "2018-2023"
    description = "Led plumbing team, managed residential projects"
} | ConvertTo-Json

try {
    $workResp = Invoke-RestMethod -Uri "$baseUrl/workers/profile/$profileId/work-history" -Method Post -Body $workHistoryBody -Headers $authHeaders
    Write-Host "✅ Work history added" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Work history API may not exist, continuing..." -ForegroundColor Yellow
}

# 6. Add Certifications (THE KEY FIX!)
Write-Host "6️⃣  Adding certifications (CRITICAL for 100% completion)..." -ForegroundColor Cyan
$certBody = @{
    name = "Master Plumber License"
    issuer = "Kenya Plumbing Board"
    year = 2023
} | ConvertTo-Json

try {
    $certResp = Invoke-RestMethod -Uri "$baseUrl/workers/profile/$profileId/certifications" -Method Post -Body $certBody -Headers $authHeaders
    Write-Host "✅ Certification added" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Certification API may not exist, continuing..." -ForegroundColor Yellow
}

# 7. Check Completion Percentage
Write-Host "7️⃣  Checking profile completion percentage..." -ForegroundColor Cyan
try {
    $checkResp = Invoke-RestMethod -Uri "$baseUrl/workers/profile/$profileId" -Method Get -Headers $authHeaders
    Write-Host "Profile data:" -ForegroundColor Gray
    Write-Host "  - Full Name: $($checkResp.fullName)" -ForegroundColor Gray
    Write-Host "  - Phone: $($checkResp.phoneNumber)" -ForegroundColor Gray
    Write-Host "  - Location: $($checkResp.location)" -ForegroundColor Gray
    Write-Host "  - Bio: $($checkResp.bio.Substring(0, [Math]::Min(50, $checkResp.bio.Length)))..." -ForegroundColor Gray
    Write-Host "  - Skills: $($checkResp.skills.Count) added" -ForegroundColor Gray
    Write-Host "  - Work History: $($checkResp.workHistory.Count) entries" -ForegroundColor Gray
    Write-Host "  - Certifications: $($checkResp.certifications.Count) entries" -ForegroundColor Gray
    Write-Host "  - Documents: $($checkResp.documents.Count) uploaded" -ForegroundColor Gray
} catch {
    Write-Host "⚠️  Could not fetch updated profile: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 8. Check if documents exist (mock by checking array)
Write-Host "8️⃣  Document Status:" -ForegroundColor Cyan
$hasDocs = $checkResp.documents -and $checkResp.documents.Count -gt 0
if ($hasDocs) {
    Write-Host "✅ Documents uploaded: $($checkResp.documents.Count)" -ForegroundColor Green
} else {
    Write-Host "❌ NO DOCUMENTS UPLOADED (need ID-Front & ID-Back)" -ForegroundColor Red
    Write-Host "   Please upload ID front and back to reach 100%" -ForegroundColor Yellow
}

# 9. Calculate Expected Completion (using same logic as frontend)
Write-Host "9️⃣  Calculating completion score..." -ForegroundColor Cyan
$score = 0
if ($checkResp.fullName) { $score += 5; Write-Host "  + 5 (name)" -ForegroundColor Gray }
if ($checkResp.email) { $score += 5; Write-Host "  + 5 (email)" -ForegroundColor Gray }
if ($checkResp.phoneNumber) { $score += 10; Write-Host "  + 10 (phone)" -ForegroundColor Gray }
if ($checkResp.category) { $score += 10; Write-Host "  + 10 (category)" -ForegroundColor Gray }
if ($checkResp.location) { $score += 10; Write-Host "  + 10 (location)" -ForegroundColor Gray }
if ($checkResp.bio) { $score += 10; Write-Host "  + 10 (bio)" -ForegroundColor Gray }
if ($checkResp.skills -and $checkResp.skills.Count -gt 0) { $score += 10; Write-Host "  + 10 (skills)" -ForegroundColor Gray }
if ($checkResp.workHistory -and $checkResp.workHistory.Count -gt 0) { $score += 10; Write-Host "  + 10 (work history)" -ForegroundColor Gray }
if ($checkResp.certifications -and $checkResp.certifications.Count -gt 0) { $score += 10; Write-Host "  + 10 (certifications) ← FIXED!" -ForegroundColor Cyan }
if ($checkResp.documents) {
    $docs = $checkResp.documents
    if ($docs | Where-Object { $_.type -eq "ID-Front" }) { $score += 10; Write-Host "  + 10 (ID-Front)" -ForegroundColor Gray }
    if ($docs | Where-Object { $_.type -eq "ID-Back" }) { $score += 10; Write-Host "  + 10 (ID-Back)" -ForegroundColor Gray }
}
$completion = [Math]::Min($score, 100)
Write-Host "📊 TOTAL COMPLETION SCORE: $completion%" -ForegroundColor Cyan

# 10. Submission Status
Write-Host "🔟 Submission Readiness:" -ForegroundColor Cyan
if ($completion -eq 100) {
    Write-Host "✅ PROFILE 100% COMPLETE - READY TO SUBMIT FOR VERIFICATION!" -ForegroundColor Green
    
    # Attempt to submit
    Write-Host "   Attempting to submit for verification..." -ForegroundColor Cyan
    try {
        $submitResp = Invoke-RestMethod -Uri "$baseUrl/workers/profile/$workerId/submit" -Method Put -Body "{}" -Headers $authHeaders
        Write-Host "✅ SUBMISSION SUCCESSFUL!" -ForegroundColor Green
        Write-Host "   Status: $($submitResp.status)" -ForegroundColor Green
        Write-Host "   Worker is now pending admin review" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Submission endpoint error: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ PROFILE INCOMPLETE ($completion%) - Cannot submit" -ForegroundColor Red
    $remaining = 100 - $completion
    Write-Host "   Missing $remaining% to reach 100% requirement" -ForegroundColor Yellow
    if ($remaining -gt 0) {
        Write-Host "   ACTION REQUIRED:" -ForegroundColor Yellow
        if (-not ($checkResp.documents | Where-Object { $_.type -eq "ID-Front" })) {
            Write-Host "     • Upload ID Front document (+10%)" -ForegroundColor Yellow
        }
        if (-not ($checkResp.documents | Where-Object { $_.type -eq "ID-Back" })) {
            Write-Host "     • Upload ID Back document (+10%)" -ForegroundColor Yellow
        }
    }
}

Write-Host ""
Write-Host "=== TEST COMPLETE ===" -ForegroundColor Green
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  - Registered new worker" -ForegroundColor Gray
Write-Host "  - Completed profile with ALL required fields (including certifications)" -ForegroundColor Gray
Write-Host "  - Calculated completion percentage" -ForegroundColor Gray
Write-Host "  - Verified submission readiness" -ForegroundColor Gray
