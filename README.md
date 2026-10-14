# The Pour Over — Mini CDP

Espresso-shot customer data platform for The Pour Over developer assessment.

**Stack:** Spring Boot 3 (Java 17) + H2 file DB + React (Vite) SPA + Spring AI tool calling.

## Live URLs

After deploy, fill these in for your submission:

- **App UI:** `https://YOUR_HOST/`
- **Webhook:** `https://YOUR_HOST/webhooks/app`

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
- Node 20+ (for frontend)

### 1) Backend

```bash
cd backend
cp .env.example .env
# edit .env — WEBHOOK_SECRET and PII_HASH_SALT are required

mvn spring-boot:run
```

API: `http://localhost:8080`

**Swagger UI** (requires login session): `http://localhost:8080/swagger-ui.html`  
Authorize → **WebhookBearer** with your `WEBHOOK_SECRET`, then Try it out on `POST /webhooks/app`.

### 2) Frontend (dev)

```bash
cd frontend
npm install
npm run dev
```

UI: `http://localhost:5173` (proxies `/api`, `/webhooks`, and `/oauth2` to :8080)

### Auth

| Method | How |
|---|---|
| Username / password | Always on. Defaults: `growth` / `pour-over`, `recruiter` / `demo` |
| GitHub (optional) | Set `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, **and** `GITHUB_ALLOWED_LOGINS` (comma-separated logins/emails). Callback: `https://YOUR_HOST/login/oauth2/code/github` |

Passwords come from `CDP_PASSWORD` / `RECRUITER_PASSWORD` (or edit `cdp.users` in `application.yml`).

### 3) Production-style single jar (static UI inside Spring)

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

Then **Lookup** `reader00000@example.com` (synthetic fixture linked to `u_00000000`).

## Auth model

| Door | Credential | Protects |
|------|------------|----------|
| React UI / `/api/**` | Username/password (and optional allowlisted GitHub) → session cookie + CSRF | Browsing data |
| `POST /webhooks/app` | `Authorization: Bearer WEBHOOK_SECRET` (CSRF-exempt) | Event ingest |

## Security notes

- CSRF enabled for cookie session APIs (SPA sends `X-XSRF-TOKEN`)
- H2 console off by default (`CDP_H2_CONSOLE=true` + login required to open it)
- Swagger / OpenAPI require an authenticated session
- GitHub OAuth only works with a non-empty `GITHUB_ALLOWED_LOGINS`
- Login is rate-limited per IP
- `WEBHOOK_SECRET` and `PII_HASH_SALT` have no weak built-in defaults

## PII boundary (assistant)

- Tools in `backend/.../ai/CdpTools.java` call the same analytics/snapshot services as the UI
- Tool return types use `subscriberRef` (HMAC-SHA256 with `PII_HASH_SALT`) — no email fields
- Chat user text **and** model answers are email-redacted (`PiiService.redactEmailsInText`)
- Human Lookup may show full email; that API is **not** registered as an AI tool

## Tests

```bash
cd backend
mvn test
```

Covers email normalization, subscriber merge rules, webhook dedupe/merge/unknown user, PII, login rate limit, GitHub allowlist, and security (CSRF / H2 / Swagger / webhook).

## CSV fixtures

Synthetic files live in `backend/src/main/resources/data/`. Replace with the official assessment CSVs (same headers) and:

```bash
# while logged into the UI session (include CSRF cookie/header)
curl -X POST http://localhost:8080/api/admin/reseed -b cookies.txt
```

Or delete `backend/data/` H2 files and restart.

## Docker (recommended for hosting)

Multi-stage image: builds the React SPA, packages it into the Spring Boot jar, runs one container on port 8080. H2 data lives in a named volume.

```bash
cp .env.example .env
# edit .env — set WEBHOOK_SECRET, PII_HASH_SALT, SPRING_AI_OPENAI_API_KEY

docker compose up --build -d
```

- **UI:** `http://localhost:8080` (or `http://YOUR_SERVER:8080`)
- **Webhook:** `http://YOUR_SERVER:8080/webhooks/app`
- **Swagger:** sign in first, then `http://YOUR_SERVER:8080/swagger-ui.html`

Useful commands:

```bash
docker compose logs -f cdp
docker compose down          # keeps the cdp-data volume
docker compose down -v       # wipes H2 (full re-seed on next start)
```

Put a reverse proxy (Caddy/nginx) in front for HTTPS when you expose the host. Share `recruiter` / your `RECRUITER_PASSWORD` privately (or enable allowlisted GitHub OAuth).

## Deploy (Railway / Render)

1. Build frontend into `backend/src/main/resources/static` (see above), or build/push the Docker image
2. Deploy the Spring Boot jar (or container)
3. Set env: `CDP_PASSWORD`, `RECRUITER_PASSWORD`, `WEBHOOK_SECRET`, `PII_HASH_SALT`, `SPRING_AI_OPENAI_API_KEY` (+ optional GitHub + `GITHUB_ALLOWED_LOGINS`)
4. Persist disk for `./data` (H2 file) so webhook events survive restarts
5. Keep `CDP_H2_CONSOLE=false`

## Loom outline (5–8 min)

1. Snapshot + source mix  
2. Lookup a subscriber  
3. Instagram cold analytics query + CSV export  
4. Live curl webhook (anon → login) + ticker / lookup  
5. Assistant + show `CdpTools` / `PiiService`  
6. Where AI helped / was wrong; security concern (e.g. webhook rate limits); what’s next  

## Project layout

```text
backend/            Spring Boot API, H2, webhook, Spring AI
frontend/           Vite React SPA
Dockerfile          Multi-stage image (UI + jar)
docker-compose.yml  Local / in-house host
.env.example        Secrets template for Compose
```
