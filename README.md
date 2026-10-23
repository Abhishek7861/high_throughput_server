# high_throughput_server

Spring Boot service for load testing a PostgreSQL read path with a Redis cache in front.

## Running

### Everything in Docker

```bash
docker compose up -d --build
```

This starts PostgreSQL, Redis and the app (`load_test_app`) on `http://localhost:8082`.
After code changes, rebuild just the app with `docker compose up -d --build app`.
Follow its logs with `docker compose logs -f app`.

### App locally, dependencies in Docker

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run
```

Requires Java 21 or later. Stop the container first (`docker compose stop app`),
since both use port 8082.

## Endpoints

| Method | Path                     | Description                                              |
|--------|--------------------------|----------------------------------------------------------|
| GET    | `/database/payload`      | Read a random seeded payload (id 1–100), from Redis if cached, else PostgreSQL; 404 if missing |
| POST   | `/database/payload`      | Create a payload: `{"hash": "..."}` (1–255 characters)   |
| GET    | `/api/users`             | Returns a fixed in-memory payload (no DB or cache)       |
| POST   | `/api/users`             | Echoes an in-memory payload (no DB or cache)             |

Swagger UI is available at `/swagger-ui.html`.

On every startup the `payload` table is recreated and seeded with 100 rows (ids 1–100).

## Caching

- `GET /database/payload` reads are cached in Redis under `payloads::{id}` for 10 minutes.
  Missing rows are not cached.
- A new row is cached on its first GET. Changes made directly in PostgreSQL can take
  up to 10 minutes to show for a cached row.
- **Redis is optional.** If Redis is down or unreachable, cache errors are logged as
  warnings and reads fall through to PostgreSQL (see `config/CacheConfig.java`).
  Redis timeouts are 200 ms, so an outage adds at most that much per request.

## CPU layout

Each container is pinned to its own cores in `docker-compose.yaml`:

| Cores | Used by                       |
|-------|-------------------------------|
| 0–3   | Free for the load generator   |
| 4–6   | App (`load_test_app`)         |
| 7–8   | PostgreSQL                    |
| 9     | Redis (single-threaded)       |

To see how throughput scales with the app's core count, override `APP_CPUSET` and
recreate only the app:

```bash
APP_CPUSET=4 docker compose up -d app      # 1 core
APP_CPUSET=4-5 docker compose up -d app    # 2 cores
APP_CPUSET=4-6 docker compose up -d app    # 3 cores (default)
```

If the load generator runs in Docker, keep it off these cores with `--cpuset-cpus=0-3`.

On Docker Desktop these are the Linux VM's virtual CPUs, not dedicated physical cores,
so use the results to compare runs rather than as absolute capacity numbers.

## Load testing

Start the stack and warm up the JVM for ~15 s before measuring; runs on a freshly
started app understate throughput.

**Writes** with [autocannon](https://github.com/mcollina/autocannon):

```bash
autocannon -c 50 -d 20 -m POST -H "Content-Type: application/json" \
  -b '{"hash":"df2463d4-b6b5-4fec-8690-f9d9e318c3e7"}' http://localhost:8082/database/payload
```

**Reads:**

```bash
autocannon -c 50 -d 20 http://localhost:8082/database/payload
```

Add `--renderStatusCodes` to confirm every response is 201/200. Watch which container
saturates with `docker stats` during a run.

autocannon's throughput is reliable here, but its latency numbers were not (it reported
~100 ms p50 where 50 connections at 18k req/s implies ~3 ms). Use
[wrk](https://github.com/wg/wrk) for latency:

```bash
cat > post.lua <<'EOF'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"hash":"df2463d4-b6b5-4fec-8690-f9d9e318c3e7"}'
EOF
wrk -t4 -c50 -d20s --latency -s post.lua http://localhost:8082/database/payload
```

### Results (2026-10-23)

Docker Desktop on macOS with the default CPU layout (app 3 cores, Postgres 2, Redis 1),
`synchronous_commit=off` and virtual threads enabled, 50 connections, after warmup.

| Test   | Throughput      | p50 (wrk) | p99 (wrk) | Status codes |
|--------|-----------------|-----------|-----------|--------------|
| POST   | ~18,200 req/s   | 2.8 ms    | 7.1 ms    | all 201      |
| GET    | ~30,400 req/s   | —         | —         | all 200      |

CPU under load:

| Test | App (of 300%) | Postgres (of 200%) | Redis (of 100%) |
|------|---------------|--------------------|-----------------|
| POST | ~275%         | ~155%              | <1%             |
| GET  | ~260%         | <1%                | ~19%            |

- The app's CPU is the bottleneck for both reads and writes.
- Reads hardly touch Postgres: all 100 seeded ids stay in Redis after warmup, so the
  read test measures the app + Redis path, not the database.
- Write throughput history: 5,319 req/s baseline → 5,522 with `synchronous_commit=off`
  and `open-in-view=false` → 7,813 with virtual threads (all with a short 10 s warmup);
  ~16–18k req/s after a longer warmup.

## Configuration

Defaults in `application.properties` target `localhost`. Override them with environment
variables, as the `app` service in `docker-compose.yaml` does:

| Variable                     | Default                                    |
|------------------------------|--------------------------------------------|
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/users`   |
| `SPRING_DATASOURCE_USERNAME` | `postgres`                                 |
| `SPRING_DATASOURCE_PASSWORD` | `postgres`                                 |
| `SPRING_DATA_REDIS_HOST`     | `localhost`                                |
| `SPRING_DATA_REDIS_PORT`     | `6379`                                     |
