#!/bin/bash
# Runs once, when the primary's data directory is first initialized (docker-entrypoint-initdb.d).
# Sets up what the streaming replica needs: a replication user, a pg_hba rule allowing it
# to connect for replication, and a physical replication slot so the primary keeps WAL
# until the replica has received it.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '${POSTGRES_REPLICATION_PASSWORD}';
SELECT pg_create_physical_replication_slot('replica_1');
SQL

# "all" in the database column doesn't cover replication connections; they need their own rule
echo "host replication replicator all scram-sha-256" >> "$PGDATA/pg_hba.conf"
