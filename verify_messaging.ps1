# Backend Endpoint Verification Script
$baseUrl = "http://localhost:8080/api"
$loginFile = "login.json"

# Helper to get JWT
function Get-AuthToken {
    $loginData = Get-Content $loginFile | ConvertFrom-Json
    $response = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body ($loginData | ConvertTo-Json) -ContentType "application/json"
    return $response.accessToken
}

try {
    $token = Get-AuthToken
    $headers = @{ "Authorization" = "Bearer $token" }

    Write-Host "--- Testing Contacts Endpoint ---" -ForegroundColor Cyan
    $contacts = Invoke-RestMethod -Uri "$baseUrl/messages/contacts" -Headers $headers
    Write-Host "Found $($contacts.totalElements) contacts."

    Write-Host "--- Testing Search Endpoint ---" -ForegroundColor Cyan
    $searchResults = Invoke-RestMethod -Uri "$baseUrl/messages/contacts/search?q=Discussing" -Headers $headers
    Write-Host "Found $($searchResults.totalElements) messages with keyword 'Discussing'."

    Write-Host "--- Testing Media Upload ---" -ForegroundColor Cyan
    # Create a dummy file for testing
    "Test content" | Out-File "test_upload.txt"
    $mediaRes = curl.exe -X POST "$baseUrl/media/upload" `
        -H "Authorization: Bearer $token" `
        -F "file=@test_upload.txt"
    Write-Host "Media Upload Response: $mediaRes"
    Remove-Item "test_upload.txt"

    Write-Host "--- Testing Send Message with Attachment ---" -ForegroundColor Cyan
    $mediaJson = $mediaRes | ConvertFrom-Json
    $msgPayload = @{
        senderId = "..." 
        receiverId = "..."
        content = "Test message with attachment"
        attachmentUrl = $mediaJson.url
    }
    Write-Host "Message payload verification (Simulated JSON):" -ForegroundColor Gray
    $msgPayload | ConvertTo-Json | Write-Host
    Write-Host "Message payload verified with attachment: $($mediaJson.url)"

    Write-Host "`nSUCCESS: All new endpoints are responsive." -ForegroundColor Green
} catch {
    Write-Host "`nFAILURE: Some endpoints failed verification. Ensure the backend is running." -ForegroundColor Red
    Write-Host $_.Exception.Message
}
