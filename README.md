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
| GET    | `/database/payload/{id}` | Read a payload, from Redis if cached, else PostgreSQL     |
| POST   | `/database/payload`      | Create a payload: `{"hash": "..."}` (1–255 characters)   |
| GET    | `/api/users`             | Returns a fixed in-memory payload (no DB or cache)       |
| POST   | `/api/users`             | Echoes an in-memory payload (no DB or cache)             |

Swagger UI is available at `/swagger-ui.html`.

On every startup the `payload` table is recreated and seeded with 100 rows (ids 1–100).

## Caching

- `GET /database/payload/{id}` is cached in Redis under `payloads::{id}` for 10 minutes.
  Missing rows are not cached.
- A new row is cached on its first GET. Changes made directly in PostgreSQL can take
  up to 10 minutes to show for a cached row.
- **Redis is optional.** If Redis is down or unreachable, cache errors are logged as
  warnings and reads fall through to PostgreSQL (see `config/CacheConfig.java`).
  Redis timeouts are 200 ms, so an outage adds at most that much per request.

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
