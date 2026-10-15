# Recruiter quick start

## Live demo

Open **https://pourover.johnpranoy7.dev** and sign in.

On the login page, click **Recruiters — click here** to fill the demo credentials (`test` / `demo`), then click **Sign in**.

### Try the webhook

1. Open Swagger in another tab:  
   **https://pourover.johnpranoy7.dev/swagger-ui/index.html**  
   (sign in to the app first in the same browser so the session cookie works)
2. Click **Authorize**, choose **WebhookBearer**, and paste the `WEBHOOK_SECRET` value (ask me if you don’t have it)
3. Call **`POST /webhooks/app`** with a sample payload from the docs

## Source code

**GitHub:** https://github.com/johnpranoy7/thePourOverAssesment

## Local setup (short)

Needs: Java 17+, Maven 3.9+, Node 20+ (or just Docker).

### Option A — Docker (easiest)

```bash
git clone https://github.com/johnpranoy7/thePourOverAssesment.git
cd thePourOverAssesment
cp .env.example .env
# edit .env — set RECRUITER_USERNAME, RECRUITER_PASSWORD, WEBHOOK_SECRET,
# PII_HASH_SALT, DB_USERNAME, DB_PASSWORD, and optionally SPRING_AI_OPENAI_API_KEY
docker compose up --build -d
```

Open **http://localhost:8080** (or whatever `CDP_PORT` you set).

### Option B — Backend + Vite

```bash
git clone https://github.com/johnpranoy7/thePourOverAssesment.git
cd thePourOverAssesment

cp backend/.env.example backend/.env
# edit backend/.env with the same required vars as above

cd backend && mvn spring-boot:run
# API: http://localhost:8080

cd ../frontend && npm install && npm run dev
# UI:  http://localhost:5173  (proxies API to :8080)
```

Swagger locally (after UI login): **http://localhost:8080/swagger-ui/index.html**

More detail: [README.md](README.md)

## Questions?

Email **johnpranoy7@gmail.com**
