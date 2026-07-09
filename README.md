# Photo AI Search

An internal photo search platform for BEST Cluj-Napoca that indexes Google Drive photo albums and enables semantic search using AI (CLIP) and face recognition (InsightFace).

## Architecture

```
Browser
  │
Traefik :80  (reverse proxy)
  ├── /api/auth/**         → auth-service        :8081
  ├── /api/admin/users/**  → auth-service        :8081
  ├── /api/drive/**        → indexing-service    :8082
  ├── /api/admin/ai/**     → indexing-service    :8082
  ├── /api/search/**       → search-service      :8083
  └── /                    → frontend (nginx)    :80

Internal only:
  ├── postgres             :5432
  └── ai-service           :8000  (Python FastAPI — CLIP, InsightFace)
```

## Prerequisites

- Docker Engine (or Docker Desktop) with the Compose plugin
- NVIDIA driver + [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) — the AI service reserves a GPU (≥4 GB VRAM). To run without a GPU, remove the `deploy.resources` block from `ai-service` in `docker-compose.yml` and set `AI_DEVICE=cpu` (much slower indexing)
- JDK 17 (to build the Java services)
- ~25 GB free disk (images ≈16 GB + model weights ≈2 GB + build cache)
- Google Cloud project with OAuth2 credentials (see Setup below)
- Google Drive OAuth refresh token with access to the photo Drive

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/your-org/photo-ai-search.git
cd photo-ai-search
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` and fill in:

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_SECRET` | OAuth2 client secret from Google Cloud Console |
| `DRIVE_REFRESH_TOKEN` | OAuth2 refresh token with Drive access |
| `DB_PASSWORD` | PostgreSQL password (choose any strong password) |
| `AI_SERVICE_INTERNAL_API_KEY` | Internal API key between services (choose any strong value) |
| `APP_SECURITY_OWNER_EMAIL` | Email auto-promoted to OWNER on first sign-in (bootstraps the role hierarchy) |

### 3. Google Cloud Console setup

1. Create an OAuth2 client (Web application) at [console.cloud.google.com](https://console.cloud.google.com)
2. Add authorized redirect URI: `http://<your-host>/login/oauth2/code/google`
3. Add authorized JavaScript origin: `http://<your-host>`
4. Enable the Google Drive API
5. Put the Client Secret in `.env`; put the Client ID in `auth-service/src/main/resources/application.properties`
6. Set the Drive root folder id (`app.drive.root-folder-id`) in `indexing-service/.../application.properties` and the allowed sign-in domain (`app.security.allowed-domain`) in `auth-service/.../application.properties`. Albums are the root's sub-folders, optionally grouped in four-digit year folders (e.g. `2026/JobShop2026`)

### 4. Build the Java services

```bash
cd auth-service     && ./mvnw package -DskipTests && cd ..
cd indexing-service && ./mvnw package -DskipTests && cd ..
cd search-service   && ./mvnw package -DskipTests && cd ..
```

(On Windows use `.\mvnw.cmd` instead of `./mvnw`.)

### 5. Start everything

```bash
docker compose up --build
```

The app will be available at **http://localhost**.

> **Note:** The first startup downloads ~2 GB of AI models (CLIP, InsightFace). Subsequent starts are fast because models are cached in a Docker volume.

## Usage

1. Log in with your `@bestcj.ro` Google account
2. Go to **Admin** → click **Refresh statuses** to sync albums from Google Drive
3. Index an album using the **Index new** button (and **Index faces** to enable face search for it)
4. Use the **Gallery** page to browse, search by text description, or search by person from reference photos

## User Roles

| Role | Access |
|------|--------|
| `USER` | Gallery + search only |
| `ADMIN` | + Admin panel (indexing) + Users panel (can manage USERs) |
| `SUPER_ADMIN` | + Index all albums; can manage ADMINs and USERs |
| `OWNER` | Full access, can manage all roles |

New users default to `USER`. Roles are assigned in the **Users** page (requires ADMIN or above).

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_CLIENT_SECRET` | ✅ | Google OAuth2 client secret |
| `DRIVE_REFRESH_TOKEN` | ✅ | Google Drive API refresh token |
| `DB_PASSWORD` | ✅ | PostgreSQL database password |
| `AI_SERVICE_INTERNAL_API_KEY` | ✅ | Internal API key for service-to-service calls |
| `APP_SECURITY_OWNER_EMAIL` | ✅ | Email auto-promoted to OWNER on first sign-in |
| `SPRING_DATASOURCE_URL` | auto | Set by docker-compose, override for external DB |
| `APP_AI_SERVICE_BASE_URL` | auto | Set by docker-compose, override for external AI service |

Optional tuning knobs (device, precision, batch sizes, pool sizes) are documented in [`.env.example`](.env.example).

## Tech Stack

- **Frontend:** React + TypeScript + Mantine UI + Vite
- **Auth service:** Spring Boot 4, Spring Security, OAuth2/OIDC, Spring Session JDBC
- **Indexing service:** Spring Boot 4, Spring Data JPA, Google Drive API
- **Search service:** Spring Boot 4, Spring Data JPA
- **AI service:** Python FastAPI, CLIP (open_clip), InsightFace, FAISS (per-album exact vector indexes)
- **Database:** PostgreSQL 16
- **Reverse proxy:** Traefik v3
- **Container orchestration:** Docker Compose
