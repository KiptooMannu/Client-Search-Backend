# End-to-End Verification Script for Kazi Konnect
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080/api"
$testEmail = "final_rose@worker.com"
$testPass = "password123"

Write-Host "--- 1. Registering User ($testEmail) ---" -ForegroundColor Cyan
$regBody = @{
    username = "final_rose"
    email = $testEmail
    password = $testPass
    fullName = "Final Rose Worker"
    role = "WORKER"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/register" -ContentType "application/json" -Body $regBody
Write-Host "Response: $regResp"

Write-Host "`n--- 2. Logging In ---" -ForegroundColor Cyan
$loginBody = @{
    email = $testEmail
    password = $testPass
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/login" -ContentType "application/json" -Body $loginBody
$token = $loginResp.accessToken
Write-Host "Login Successful. Token obtained."

Write-Host "`n--- 3. Testing POST /api/workers/profile (Scenario in Screenshot) ---" -ForegroundColor Cyan
Write-Host "Note: This used to return 400 'Profile already exists'. Now it should be idempotent (200 OK)." -ForegroundColor Gray

$profileBody = @{
    fullName = "Rose Worker Updated via POST"
    phoneNumber = "0711223344"
    location = "Nairobi"
    experienceYears = 5
    bio = "Certified Plumber specialized in residential maintenance."
} | ConvertTo-Json

$headers = @{
    Authorization = "Bearer $token"
}

try {
    $profileResp = Invoke-RestMethod -Method Post -Uri "$baseUrl/workers/profile?email=$testEmail" -ContentType "application/json" -Headers $headers -Body $profileBody
    Write-Host "POST Successful (200 OK). Existing profile returned as expected." -ForegroundColor Green
    Write-Host "Returned Profile ID: $($profileResp.id)"
} catch {
    Write-Host "POST Failed! Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "`n--- 4. Testing PUT /api/workers/profile/{id} (Standard Update) ---" -ForegroundColor Cyan
$profileId = $profileResp.id
$updateBody = @{
    fullName = "Rose Worker Finalized"
    location = "Mombasa"
} | ConvertTo-Json

$updateResp = Invoke-RestMethod -Method Put -Uri "$baseUrl/workers/profile/$profileId" -ContentType "application/json" -Headers $headers -Body $updateBody
Write-Host "PUT Successful. New location: $($updateResp.location)" -ForegroundColor Green

Write-Host "`n--- VERIFICATION COMPLETE: ALL SYSTEMS OPERATIONAL ---" -ForegroundColor Green
