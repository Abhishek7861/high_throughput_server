#!/bin/bash
# Load tests every endpoint with wrk: 10 s warmup, then a 20 s measured run, with
# docker stats recorded alongside. Requires wrk and python3, and the compose stack running.
# Usage: loadtest/run.sh [base-url]   (default http://localhost:8082)
set -euo pipefail
cd "$(dirname "$0")"
BASE=${1:-http://localhost:8082}
OUT=results/$(date +%Y%m%d-%H%M%S)
mkdir -p "$OUT"

P() { docker exec fastapi_postgres psql -U postgres -d users -Atc "$1"; }
R() { docker exec redis redis-cli "$@"; }
requests() { grep -o '"requests":[0-9]*' "$1" | cut -d: -f2; }

tests=("get_database GET /database/payload"
       "post_database POST /database/payload"
       "post_stream POST /stream/payload"
       "get_local GET /local/payload"
       "post_local POST /local/payload")

stream_before=$(P "select count(*) from payloads where event_id is not null")
stream_sent=0
for t in "${tests[@]}"; do
  read -r name method endpoint <<<"$t"
  script=get.lua; [ "$method" = POST ] && script=post.lua
  echo "Running $method $endpoint ..."
  wrk -t4 -c50 -d10s -s $script "$BASE$endpoint" > "$OUT/$name.warmup.txt"
  ( docker stats --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' 2>/dev/null \
    | while IFS= read -r line; do printf '%s,%s\n' "$(date +%s)" "$line"; done > "$OUT/$name.stats.csv" ) &
  stats_pid=$!
  wrk -t4 -c50 -d20s -s $script "$BASE$endpoint" > "$OUT/$name.wrk.txt"
  pkill -f "docker stats --format" 2>/dev/null || true
  wait $stats_pid 2>/dev/null || true
  if [ "$name" = post_stream ]; then
    stream_sent=$(( $(requests "$OUT/$name.warmup.txt") + $(requests "$OUT/$name.wrk.txt") ))
  fi
done

# Wait for the stream consumer to drain, then compare requests with inserted rows
for _ in $(seq 1 60); do [ "$(R XLEN payload:write-stream)" = "0" ] && break; sleep 1; done
stream_rows=$(( $(P "select count(*) from payloads where event_id is not null") - stream_before ))

python3 - "$OUT" <<'EOF'
import json, sys, glob, os
out = sys.argv[1]
print(f"\n{'test':<14}{'req/s':>9}{'p50':>9}{'p90':>9}{'p99':>9}{'p99.9':>9}{'errors':>8}")
for name in ["get_database", "post_database", "post_stream", "get_local", "post_local"]:
    raw = open(f"{out}/{name}.wrk.txt").read()
    r = json.loads(raw[raw.index("JSON") + 4:].splitlines()[0])
    p = {k: v / 1000 for k, v in r["percentiles"]}
    rps = r["requests"] / (r["duration_us"] / 1e6)
    print(f"{name:<14}{rps:>9,.0f}{p[50]:>8.2f}ms{p[90]:>7.2f}ms{p[99]:>7.2f}ms{p[99.9]:>7.1f}ms{r['non2xx'] + r['socket_errors']:>8}")
EOF
echo
echo "Stream: $stream_sent requests -> $stream_rows rows, backlog $(R XLEN payload:write-stream)," \
     "pending $(R XPENDING payload:write-stream payload-writers | head -1), dead-lettered $(R XLEN payload:write-stream:dlq)"
echo "Raw output in loadtest/$OUT"
