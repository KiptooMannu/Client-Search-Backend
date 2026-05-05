# Kazi Konnect API Verification Script

# 1. Register a new worker
echo "Registering new worker..."
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tester_worker",
    "email": "test@worker.com",
    "password": "password123",
    "fullName": "Test Worker",
    "role": "WORKER"
  }'
echo -e "\n"

# 2. Login to get token
echo "Logging in..."
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@worker.com",
    "password": "password123"
  }' > login_response.json
echo -e "\n"

TOKEN=$(cat login_response.json | grep -oP '(?<="accessToken":")[^"]*')
USER_ID=$(cat login_response.json | grep -oP '(?<="userId":")[^"]*')

echo "Token: $TOKEN"
echo "User ID: $USER_ID"

# 3. Get the existing profile (created during registration)
echo "Getting profile..."
curl -s -X GET "http://localhost:8080/api/workers/profile/$USER_ID" \
  -H "Authorization: Bearer $TOKEN" > profile_response.json
echo -e "\n"

PROFILE_ID=$(cat profile_response.json | grep -oP '(?<="id":")[^"]*')
echo "Profile ID: $PROFILE_ID"

# 4. Update the profile (PUT)
echo "Updating profile..."
curl -s -X PUT "http://localhost:8080/api/workers/profile/$PROFILE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Test Worker Updated",
    "phoneNumber": "0711223344",
    "location": "Nairobi",
    "experienceYears": 5,
    "bio": "Certified Plumber specialized in residential maintenance."
  }'
echo -e "\n"
