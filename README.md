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
4. Live `POST /webhooks/app` with Bearer auth (dedupe, out-of-order, anon→login merge)
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
# optional env
set UI_PASSWORD=pour-over
set WEBHOOK_SECRET=dev-webhook-secret
set PII_HASH_SALT=dev-pii-salt
set SPRING_AI_OPENAI_API_KEY=sk-...   # required for Assistant

mvn spring-boot:run
```

API: `http://localhost:8080`

**Swagger UI:** `http://localhost:8080/swagger-ui.html`  
Use **Authorize → WebhookBearer** with `dev-webhook-secret` (or your `WEBHOOK_SECRET`), then **Try it out** on `POST /webhooks/app` to add live app events. For `/api/**` calls, first run `POST /api/auth/login` with the UI password.

### 2) Frontend (dev)

```bash
cd frontend
npm install
npm run dev
```

UI: `http://localhost:5173` (proxies `/api` and `/webhooks` to :8080)

Default UI password: `pour-over`

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
  -H "Authorization: Bearer dev-webhook-secret" ^
  -H "Content-Type: application/json" ^
  -d "{\"event_id\":\"evt_demo_1\",\"event\":\"app_open\",\"user_id\":null,\"device_id\":\"d_demo\",\"timestamp\":\"2026-09-28T13:00:00Z\",\"properties\":{}}"

curl -s -X POST http://localhost:8080/webhooks/app ^
  -H "Authorization: Bearer dev-webhook-secret" ^
  -H "Content-Type: application/json" ^
  -d "{\"event_id\":\"evt_demo_2\",\"event\":\"login\",\"user_id\":\"u_00000000\",\"device_id\":\"d_demo\",\"timestamp\":\"2026-09-28T14:00:00Z\",\"properties\":{}}"
```

Then **Lookup** `reader00000@example.com` (synthetic fixture linked to `u_00000000`).

## Auth model

| Door | Credential | Protects |
|------|------------|----------|
| React UI / `/api/**` | Shared `UI_PASSWORD` → Spring session cookie (`JSESSIONID`) | Browsing data |
| `POST /webhooks/app` | `Authorization: Bearer WEBHOOK_SECRET` | Event ingest |

No OAuth / Auth server / JWT issuer. Two independent secrets on purpose.

## PII boundary (assistant)

- Tools in `backend/.../ai/CdpTools.java` call the same analytics/snapshot services as the UI
- Tool return types use `subscriberRef` (HMAC-SHA256 with `PII_HASH_SALT`) — no email fields
- Chat user text is email-redacted before the model call (`PiiService.redactEmailsInText`)
- Human Lookup may show full email; that API is **not** registered as an AI tool

## Tests

```bash
cd backend
mvn test
```

Covers email normalization, subscriber merge rules, webhook dedupe/merge/unknown user, PII asserts.

## CSV fixtures

Synthetic files live in `backend/src/main/resources/data/`. Replace with the official assessment CSVs (same headers) and:

```bash
# while logged into the UI session
curl -X POST http://localhost:8080/api/admin/reseed -b cookies.txt
```

Or delete `backend/data/` H2 files and restart.

## Deploy (Railway / Render)

1. Build frontend into `backend/src/main/resources/static` (see above)
2. Deploy the Spring Boot jar
3. Set env: `UI_PASSWORD`, `WEBHOOK_SECRET`, `PII_HASH_SALT`, `SPRING_AI_OPENAI_API_KEY`
4. Persist disk for `./data` (H2 file) so webhook events survive restarts

## Loom outline (5–8 min)

1. Snapshot + source mix  
2. Lookup a subscriber  
3. Instagram cold analytics query + CSV export  
4. Live curl webhook (anon → login) + ticker / lookup  
5. Assistant + show `CdpTools` / `PiiService`  
6. Where AI helped / was wrong; security concern (e.g. webhook rate limits); what’s next  

## Project layout

```text
backend/     Spring Boot API, H2, webhook, Spring AI
frontend/    Vite React SPA
```
