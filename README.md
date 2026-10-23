# Load test application

Start PostgreSQL and Redis with `docker compose up -d`, then start the app with
`./mvnw spring-boot:run` (Java 21 or later).

`GET /database/payload/{id}` checks Redis before querying PostgreSQL. Successful
reads are cached for 10 minutes under `load_test::payloads::{id}`. Missing rows
are not cached. `POST /database/payload` creates a new row; its first GET populates
the cache. Changes made directly in PostgreSQL can take up to 10 minutes to
appear for a cached row.

Redis defaults to `localhost:6379`; override `REDIS_HOST` and `REDIS_PORT` as
needed. Redis must be available for cached reads. The local `/api/users`
endpoints continue to use their existing in-memory data.

Run the cache behavior and serialization tests with `./mvnw test`.
# high_throughput_server
