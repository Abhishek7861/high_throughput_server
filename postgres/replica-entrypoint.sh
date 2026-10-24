#!/bin/bash
# Starts a hot-standby replica of the primary. On first start (empty data directory) it
# copies the primary with pg_basebackup; -R writes standby.signal and the connection
# settings (including the replication slot) so Postgres starts as a streaming replica.
set -euo pipefail

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "Copying the primary with pg_basebackup..."
  until pg_basebackup --host="$PRIMARY_HOST" --username=replicator --pgdata="$PGDATA" \
      --wal-method=stream --slot=replica_1 --write-recovery-conf --checkpoint=fast; do
    echo "pg_basebackup failed, retrying in 2 s"
    rm -rf "${PGDATA:?}"/*
    sleep 2
  done
  chmod 700 "$PGDATA"
fi

exec postgres -c hot_standby=on
