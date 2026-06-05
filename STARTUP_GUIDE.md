# Backend Startup Guide

## Problem
The backend fails to connect to the cloud Neon PostgreSQL database. This is likely due to:
- Expired credentials in `.env` file
- Neon service being down
- Network/authentication timeout

## 🚀 Solution: Use Local PostgreSQL with Docker

### Prerequisites
- Docker Desktop installed (https://www.docker.com/products/docker-desktop)
- Docker Compose support enabled

### Quick Start (Recommended)

**Step 1: Start PostgreSQL container**
```bash
cd backend
docker-compose up -d
```

**Step 2: Start the backend server**
```bash
.\start-local.bat
```

The server will start on `http://localhost:8080`

### Detailed Setup

1. **Verify Docker is running:**
   ```bash
   docker --version
   docker-compose --version
   ```

2. **Start the database:**
   ```bash
   cd "c:\Users\User\Desktop\Web app\backend"
   docker-compose up -d
   ```
   
   Expected output:
   ```
   Creating kazi-konnect-db ... done
   ```

3. **Verify database is healthy:**
   ```bash
   docker-compose ps
   ```
   
   You should see:
   ```
   kazi-konnect-db      postgres:16-alpine    ...    Up (healthy)
   ```

4. **Start the backend:**
   ```bash
   .\start-local.bat
   ```

5. **Verify it's running:**
   - Open http://localhost:8080 in browser
   - Backend API will respond with documentation
   - H2 Console: http://localhost:8080/h2-console (if H2 profile used)

### Database Details

| Configuration | Local | Neon (Cloud) |
|---|---|---|
| **Host** | localhost:5432 | AWS endpoint |
| **Database** | kazi_konnect_db | neondb |
| **Username** | kazi_user | neondb_owner |
| **Password** | KaziKonnect2024! | (in .env) |
| **Persistent** | Volume mapped (docker_postgres_data) | Cloud service |

### Switching Back to Neon (Cloud)

If you fix the Neon credentials:

1. Update `.env` with valid Neon credentials
2. Stop local database:
   ```bash
   docker-compose down
   ```
3. Start with the original script:
   ```bash
   .\start.bat
   ```

### Troubleshooting

**Error: "Cannot connect to Docker daemon"**
- Ensure Docker Desktop is running

**Error: "Port 5432 already in use"**
```bash
docker-compose down
# OR kill the existing container
docker stop kazi-konnect-db
docker rm kazi-konnect-db
```

**Error: "Connection refused"**
- Wait 10-15 seconds for database to fully start
- Check database health: `docker-compose ps`

**Backend won't start after database starts**
- Check database logs: `docker-compose logs postgres`
- Verify credentials in `.env.local` match docker-compose.yml

### Next Steps

1. **Frontend Development**: The backend is now ready
   ```bash
   cd "Web-App"
   npm start
   ```

2. **Access the application**:
   - Frontend: http://localhost:4200
   - Backend API: http://localhost:8080
   - API Docs: http://localhost:8080/swagger-ui.html

3. **When ready to use cloud Neon database**:
   - Contact Neon support to verify credentials
   - Update `.env` file
   - Switch back to `.\start.bat`
