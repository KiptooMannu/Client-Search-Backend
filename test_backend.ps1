# Kazi Konnect BACKEND TEST SUITE
# -------------------------

# 1. ADMIN LOGIN
$adminLogin = curl -X POST http://localhost:8080/api/auth/login `
    -H "Content-Type: application/json" `
    -d '{"email": "admin@kazikonnect.com", "password": "admin123"}'
Write-Host "Admin Login Response: $adminLogin"

# 2. WORKER LOGIN
$workerLogin = curl -X POST http://localhost:8080/api/auth/login `
    -H "Content-Type: application/json" `
    -d '{"email": "worker@pro.com", "password": "password123"}'
Write-Host "Worker Login Response: $workerLogin"

# 3. GET PENDING WORKERS (Requires Admin Token)
# Note: You need to extract the token from $adminLogin manually for curl in terminal
# curl -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/admin/workers/pending

# 4. MARKETPLACE SEARCH (Public)
$search = curl -X GET http://localhost:8080/api/marketplace/search
Write-Host "Marketplace Search (Verified Only): $search"
