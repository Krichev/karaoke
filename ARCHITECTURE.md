# ARCHITECTURE.md - Karaoke Backend

### Section 1: System Overview
"Karaoke Backend — Spring Boot 3.x audio scoring microservice for the Challenger platform"
- This service is called BY Challenger (not the other way around) — it has no knowledge of Challenger's domain model
- Primary job: receive audio URLs or paths, download audio, analyze it, return scores

### Section 2: How Challenger Calls This Service
- Challenger sends presigned MinIO URLs (time-limited) for both user audio and reference audio
- This service downloads audio via HTTP GET from those URLs — it never accesses MinIO directly for Challenger's files
- This keeps Karaoke storage-agnostic: it doesn't need Challenger's S3 credentials or bucket knowledge
- Challenger calls:
  - `POST /api/scoring/analyze` — full scoring (pitch + rhythm + voice)
  - `POST /api/scoring/rhythm-only` — rhythm-only scoring
  - `POST /api/rhythm/score` — rhythm tap pattern scoring (timestamps, not audio)
  - `POST /api/rhythm/score-with-audio` — rhythm scoring from audio file
- Challenger polls for results or waits synchronously (current: synchronous)

### Section 3: Port Conventions
| Service        | Dev Port | Prod Port (host) | Container Port |
|----------------|----------|-------------------|----------------|
| Karaoke API    | 8083     | 8084              | 8083           |
| Challenger API | 8080     | 8081              | 8080           |
| PostgreSQL     | 5432     | 5432              | 5432           |
| MinIO API      | 9000     | 9000              | 9000           |

### Section 4: Own Storage (MinIO)
- Karaoke has its OWN MinIO buckets for its own domain:
  - `karaoke-recordings` — user performance recordings uploaded directly to Karaoke
  - `karaoke-reference-tracks` — reference tracks for songs
- Shared MinIO instance at `http://<VPS_IP>:9000`
- Credentials via env: `S3_ACCESS_KEY`, `S3_SECRET_KEY`
- Key pattern: `{env}/{userId}/{contextType}/{contextId?}/{mediaType}/{hash}.{ext}`
- For Challenger-originated audio: Karaoke receives presigned URLs and downloads via HTTP — no bucket access needed

### Section 5: Database
- Database: `karaoke_db`, user: `karaoke_user`, schema: `karaoke`
- Completely separate from Challenger's database — no cross-DB queries
- Flyway migrations in `src/main/resources/db/migration/`
- Same patterns: `GenerationType.IDENTITY`, Lombok, `@Transactional`

### Section 6: Audio Processing Stack
- TarsosDSP: pitch detection (YIN algorithm), onset detection
- MFCC: voice timbre comparison (Mel-frequency cepstral coefficients)
- FFmpeg/Jaffree: audio format conversion when needed
- Scoring algorithms: pitch accuracy, rhythm timing, voice similarity
- All processing is CPU-bound — keep async pool small on VPS (core: 2, max: 5)

### Section 7: CI/CD
- GitHub Actions → GHCR (`ghcr.io/<owner>/karaoke-backend`)
- Docker image: lowercase name required
- Prod container mapping: `8084:8083` (host:container)
- Docker network: `challenger_network` (shared)
- Deploy script: `infrastructure/scripts/deploy.sh`
