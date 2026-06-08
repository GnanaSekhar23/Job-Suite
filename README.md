# Job Suite — AI-Powered Job Application Automation

An intelligent job application platform that automatically fetches jobs from Indeed, tailors resumes and cover letters using GPT-4, and compiles them to PDF using LaTeX.

## Features

- **Automated Job Fetching** — Scrapes Indeed daily via Apify for matching jobs
- **AI Resume Tailoring** — GPT-4 rewrites resume bullets and skills to match each JD
- **LaTeX PDF Generation** — Compiles perfectly formatted one-page resumes via Tectonic
- **Cover Letter Generation** — Auto-generates job-specific cover letters
- **Dashboard** — View all jobs by date, download PDFs and LaTeX files
- **Bulk Download** — Download all company folders as a single zip
- **Chrome Extension** — Tailor resume for any job while browsing
- **Google OAuth** — Sign in with Google or email/password
- **Daily Scheduler** — Fetches and tailors jobs at 8 AM every day automatically

## Tech Stack

### Backend
- Java 25 + Spring Boot 3.5
- PostgreSQL (Neon hosted)
- GPT-4.1-mini via Azure AI Foundry
- Apify Indeed Scraper
- Cloudinary (PDF storage)
- Tectonic LaTeX compiler (Docker)

### Frontend
- React + Vite + Tailwind CSS
- JSZip + file-saver (downloads)

### Chrome Extension
- Manifest V3
- Works on Indeed, LinkedIn, Glassdoor, Greenhouse, Lever

## Project Structure

```
Job-Suite/
├── backend/
│   ├── src/main/java/com/jobsuite/
│   │   ├── config/          # AppConfig, SecurityConfig
│   │   ├── controller/      # Auth, Dashboard, Preferences, Resume
│   │   ├── dto/             # Request/Response DTOs
│   │   ├── entity/          # User, JobApplication, TailoredResume, BaseResume
│   │   ├── repository/      # JPA repositories
│   │   ├── security/        # JWT filter and service
│   │   └── service/         # AI, Apify, Auth, Dashboard, Tailoring, etc.
│   ├── src/main/resources/
│   │   ├── application.yml                  # Base config
│   │   ├── application-prod.properties      # Production (env vars)
│   │   └── application-local.properties     # Local dev (gitignored)
│   └── latex-service/       # Python FastAPI + Tectonic Docker container
├── frontend/
│   ├── src/
│   │   ├── pages/           # Login, Register, Dashboard, Preferences
│   │   ├── context/         # AuthContext
│   │   └── services/        # api.js
│   ├── .env                 # Local env (gitignored)
│   └── .env.production      # Production env vars
└── extension/               # Chrome Extension MV3
    ├── manifest.json
    ├── popup.html
    ├── popup.js
    └── content.js
```

## Local Development Setup

### Prerequisites
- Java 25
- Node.js 18+
- Docker Desktop
- Maven

### 1. Clone the repository
```bash
git clone https://github.com/yourusername/job-suite.git
cd job-suite
```

### 2. Configure local properties
Create `backend/src/main/resources/application-local.properties`:
```properties
spring.datasource.url=your_neon_db_url
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password

app.jwt.secret=your_jwt_secret_base64

app.azure-open-ai.endpoint=your_azure_openai_endpoint
app.azure-open-ai.api-key=your_azure_openai_key
app.azure-open-ai.deployment-name=gpt-4.1-mini

app.apify.api-token=your_apify_token

app.cloudinary.cloud-name=your_cloud_name
app.cloudinary.api-key=your_cloudinary_key
app.cloudinary.api-secret=your_cloudinary_secret

app.latex.service-url=http://localhost:8090
app.frontend-url=http://localhost:5173
```

### 3. Start LaTeX service
```bash
cd backend/latex-service
docker build -t latex-service .
docker run -d -p 8090:8090 --name latex-service latex-service
```

### 4. Start backend
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Or run `BackendApplication.java` in IntelliJ with profile `local`.

### 5. Start frontend
```bash
cd frontend
npm install
npm run dev
```

### 6. Create frontend .env
```
VITE_GOOGLE_CLIENT_ID=your_google_client_id
```

Open `http://localhost:5173`

## Chrome Extension Setup

1. Open `chrome://extensions`
2. Enable **Developer Mode**
3. Click **Load unpacked** → select `extension/` folder
4. Open `extension/popup.js` and set `GOOGLE_CLIENT_ID`

## Production Deployment (Azure)

See [deploy-steps.txt](deploy-steps.txt) for complete Azure deployment commands.

### Summary
| Component | Service |
|-----------|---------|
| Backend | Azure App Service (Java 21) |
| Frontend | Azure Static Web Apps |
| LaTeX | Azure Container Instance |
| Database | Neon PostgreSQL |
| Files | Cloudinary |

### Environment Variables (Azure App Service)
Set these in Azure Portal → App Service → Configuration:

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | Neon PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | DB username |
| `DATABASE_PASSWORD` | DB password |
| `JWT_SECRET` | Base64 JWT secret |
| `AZURE_OPENAI_ENDPOINT` | Azure OpenAI endpoint |
| `AZURE_OPENAI_KEY` | Azure OpenAI API key |
| `AZURE_OPENAI_DEPLOYMENT` | Model deployment name |
| `APIFY_TOKEN` | Apify API token |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |
| `LATEX_SERVICE_URL` | LaTeX container URL |
| `FRONTEND_URL` | Static Web App URL |

## How It Works

1. **Setup** — Upload your PDF resume + LaTeX template in Setup page
2. **Fetch Jobs** — Clicks "Fetch New Jobs" or runs automatically at 8 AM
3. **AI Tailoring** — For each job, GPT-4 rewrites your resume bullets to match the JD
4. **PDF Compilation** — Tectonic compiles the LaTeX to a PDF
5. **Storage** — PDFs uploaded to Cloudinary
6. **Dashboard** — View all jobs, download resumes/cover letters, click Apply

## Scheduler

- **8:00 AM daily** — Fetches new jobs from Indeed and starts tailoring
- **12:00 AM daily** — Deletes old FAILED/WITHDRAWN jobs and resets daily apply counts

## API Endpoints

### Auth
- `POST /api/auth/register` — Register with email/password
- `POST /api/auth/login` — Login with email/password
- `POST /api/auth/google` — Login with Google ID token
- `POST /api/auth/refresh` — Refresh access token
- `POST /api/auth/logout` — Logout

### Dashboard
- `GET /api/dashboard/jobs` — Get all jobs
- `GET /api/dashboard/jobs/{id}` — Get job details with PDF URLs
- `PATCH /api/dashboard/jobs/{id}/status` — Update job status
- `GET /api/dashboard/stats` — Get dashboard stats
- `POST /api/dashboard/tailor-from-extension` — Submit job from Chrome extension

### Preferences
- `GET /api/preferences` — Get user preferences
- `POST /api/preferences` — Save preferences
- `POST /api/preferences/fetch-jobs` — Manually trigger job fetch
- `POST /api/preferences/tailor-jobs` — Manually trigger tailoring

### Resume
- `POST /api/resumes` — Upload PDF resume
- `POST /api/resumes/latex` — Upload LaTeX template
- `GET /api/resumes/active` — Get active resume

## License

Private — personal use only.
