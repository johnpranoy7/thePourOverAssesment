# The Pour Over — Mini CDP

Espresso-shot customer data platform for The Pour Over developer assessment.

**Stack:** Spring Boot 3 (Java 17) + H2 file DB + React (Vite) SPA + Spring AI tool calling.

## Live URLs

- **App UI:** `https://pourover.johnpranoy7.dev/` (or your host)
- **Webhook:** `https://pourover.johnpranoy7.dev/webhooks/app`
- **Recruiters:** see [RECRUITERS.md](RECRUITERS.md) for a short walkthrough

## What it does

1. Loads `subscribers.csv`, `web_events.csv`, `app_users.csv` once at startup into H2
2. Lookup a subscriber (newsletter + site visits + app activity)
3. Analytics queries (e.g. Instagram cold 30 days; “today” = **2026-09-28**)
4. Live `POST /webhooks/app` with Bearer auth (dedupe, device-link merge, anon→identified attach)
5. Growth assistant via Spring AI tools that never return raw emails

**Surprises:** Growth snapshot home, live webhook ticker, masked analytics CSV export.

## Quick start

### Prerequisites

- Java 17+
- Maven 3.9+
- Node 20+ (frontend dev)
- Docker (optional; recommended for hosting)

### Environment

Required in `backend/.env` (local) and/or root `.env` (Compose):

| Variable | Purpose |
|---|---|
| `RECRUITER_USERNAME` / `RECRUITER_PASSWORD` | **Only** UI login user |
| `WEBHOOK_SECRET` | Bearer token for `POST /webhooks/app` |
| `PII_HASH_SALT` | HMAC key for `subscriber_ref` |
| `DB_USERNAME` / `DB_PASSWORD` | H2 credentials (**no default `sa`** — set your own) |
| `SPRING_AI_OPENAI_API_KEY` | Assistant (optional at boot; chat returns 503 until set) |

Optional:

| Variable | Purpose |
|---|---|
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth app |
| `GITHUB_ALLOWED_LOGINS` | Comma-separated GitHub logins/emails allowed to sign in (required for the GitHub button) |
| `CDP_UI_ORIGIN` | OAuth redirect origin (e.g. `http://localhost:5173` for Vite) |
| `CDP_H2_CONSOLE` | `true` to enable H2 console (still requires UI login) |
| `CORS_ALLOWED_ORIGINS` | Extra CORS origins (comma-separated) |
| `OPENAI_MODEL` | Default `gpt-4o-mini` |

Copy examples:

```bash
cp backend/.env.example backend/.env
cp .env.example .env          # for docker compose
# edit both — set strong WEBHOOK_SECRET / PII_HASH_SALT and your DB_USERNAME
```

Generate secrets (PowerShell):

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

### 1) Backend

```bash
cd backend
# ensure .env has RECRUITER_*, WEBHOOK_SECRET, PII_HASH_SALT, DB_USERNAME, DB_PASSWORD
mvn spring-boot:run
```

API: `http://localhost:8080`

**Swagger UI** (login first): `http://localhost:8080/swagger-ui.html`  
Authorize → **WebhookBearer** with your `WEBHOOK_SECRET` for webhook Try-it-out.

### 2) Frontend (dev)

```bash
cd frontend
npm install
npm run dev
```

UI: `http://localhost:5173` (proxies `/api`, `/webhooks`, `/oauth2` to :8080)

For GitHub OAuth while on Vite, set `CDP_UI_ORIGIN=http://localhost:5173` and restart the backend. Otherwise prefer `http://localhost:8080` after baking the SPA into the jar (below).

### Auth

| Method | How |
|---|---|
| Username / password | Single demo user: `RECRUITER_USERNAME` / `RECRUITER_PASSWORD` |
| GitHub (optional) | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, **and** `GITHUB_ALLOWED_LOGINS`. Callback: `https://YOUR_HOST/login/oauth2/code/github` |

### 3) Single jar (SPA inside Spring)

```bash
cd frontend && npm install && npm run build
# Windows PowerShell:
Remove-Item -Recurse -Force ..\backend\src\main\resources\static -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path ..\backend\src\main\resources\static | Out-Null
Copy-Item -Recurse dist\* ..\backend\src\main\resources\static\

cd ..\backend
mvn -DskipTests package
java -jar target/cdp-0.0.1-SNAPSHOT.jar
```

Open `http://localhost:8080`

## Sample webhook (anon → login → lookup)

```bash
curl -s -X POST http://localhost:8080/webhooks/app ^
  -H "Authorization: Bearer %WEBHOOK_SECRET%" ^
  -H "Content-Type: application/json" ^
  -d "{\"event_id\":\"evt_demo_1\",\"event\":\"app_open\",\"user_id\":null,\"device_id\":\"d_demo\",\"timestamp\":\"2026-09-28T13:00:00Z\",\"properties\":{}}"

curl -s -X POST http://localhost:8080/webhooks/app ^
  -H "Authorization: Bearer %WEBHOOK_SECRET%" ^
  -H "Content-Type: application/json" ^
  -d "{\"event_id\":\"evt_demo_2\",\"event\":\"login\",\"user_id\":\"u_00000000\",\"device_id\":\"d_demo\",\"timestamp\":\"2026-09-28T14:00:00Z\",\"properties\":{}}"
```

Then **Lookup** `matthew.hall204@gmail.com` (newsletter + app user + web visit in the seed CSVs).

## Auth model

| Door | Credential | Protects |
|------|------------|----------|
| React UI / `/api/**` | Recruiter password (and optional allowlisted GitHub) → session cookie + CSRF | Browsing data |
| `POST /webhooks/app` | `Authorization: Bearer WEBHOOK_SECRET` (CSRF-exempt) | Event ingest |

## Security notes

- CSRF enabled for cookie session APIs (SPA sends `X-XSRF-TOKEN`)
- H2 console off by default (`CDP_H2_CONSOLE=true` + login required)
- Swagger / OpenAPI require an authenticated session
- GitHub OAuth requires a non-empty `GITHUB_ALLOWED_LOGINS`
- Login rate-limited per IP
- `WEBHOOK_SECRET` and `PII_HASH_SALT` have no weak built-in defaults
- H2 username/password come only from `DB_USERNAME` / `DB_PASSWORD` (set explicitly)

## PII boundary (assistant)

- Tools in `CdpTools` call the same analytics/snapshot services as the UI
- Tool payloads use `subscriber_ref` (HMAC-SHA256 with `PII_HASH_SALT`) — no email fields
- Chat user text **and** model answers are email-redacted (`PiiService.redactEmailsInText`)
- Named-person questions should be refused (no name field; redirect to Analytics / Lookup)
- Human Lookup may show full email; that API is **not** an AI tool

## Tests

```bash
cd backend
mvn test
```

Covers email normalization (incl. Gmail aliases), subscriber merge rules, webhook dedupe/merge/unknown user/shared device, PII, login rate limit, GitHub allowlist, and security (CSRF / H2 / Swagger / webhook).

## CSV fixtures

Files: `backend/src/main/resources/data/`. Replace with official assessment CSVs (same headers), then reseed while logged in (CSRF required) or delete `backend/data/` and restart.

## Docker

Multi-stage image: React SPA + Spring Boot jar on port 8080. H2 data in volume `cdp-data`.

### Local Compose

```bash
cp .env.example .env
# set RECRUITER_*, WEBHOOK_SECRET, PII_HASH_SALT, DB_USERNAME, DB_PASSWORD, SPRING_AI_OPENAI_API_KEY

docker compose up --build -d
```

- **UI:** `http://localhost:8080`
- **Webhook:** `http://localhost:8080/webhooks/app`
- **Swagger:** sign in, then `/swagger-ui.html`

```bash
docker compose logs -f cdp
docker compose down          # keeps cdp-data
docker compose down -v       # wipe H2 (re-seed next start)
```

### Publish image (GitHub Actions)

On push to `main`/`master`, [`.github/workflows/docker-publish.yml`](.github/workflows/docker-publish.yml) builds and pushes to Docker Hub.

Repo secrets:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN` (Hub access token)

Image: `YOURUSER/the-pour-over-cdp:latest` (plus `sha-…` tags).

Manual local push:

```bash
docker build -t YOURUSER/the-pour-over-cdp:latest .
docker login
docker push YOURUSER/the-pour-over-cdp:latest
```

On the host, pull/recreate (Compose should use `image:` not only `build: .` if you deploy from Hub):

```bash
docker compose pull
docker compose up -d --force-recreate
```

Put Caddy/nginx in front for HTTPS. Share `RECRUITER_USERNAME` / `RECRUITER_PASSWORD` privately (or allowlisted GitHub).

## Deploy checklist

1. Build/push Docker image (CI or manual), or bake SPA into the jar
2. Set env: `RECRUITER_USERNAME`, `RECRUITER_PASSWORD`, `WEBHOOK_SECRET`, `PII_HASH_SALT`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_AI_OPENAI_API_KEY` (+ optional GitHub)
3. Persist `./data` (or Compose volume) for H2
4. Keep `CDP_H2_CONSOLE=false` on public hosts

## Loom outline (5–8 min)

1. Snapshot + source mix  
2. Lookup a subscriber  
3. Instagram cold analytics + CSV export  
4. Live curl webhook (anon → login) + ticker / lookup  
5. Assistant + `CdpTools` / `PiiService`  
6. Where AI helped / was wrong; security note; what’s next  

## Project layout

```text
backend/                 Spring Boot API, H2, webhook, Spring AI
frontend/                Vite React SPA
Dockerfile               Multi-stage image (UI + jar)
docker-compose.yml       Local / host
.env.example             Compose secrets template
.github/workflows/       Docker Hub publish on push
```
