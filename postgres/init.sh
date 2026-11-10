#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v app_password="$DB_PASSWORD" <<'SQL'
CREATE ROLE inventory_app LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE inventory_db TO inventory_app;
GRANT CREATE ON DATABASE inventory_db TO inventory_app;
GRANT USAGE, CREATE ON SCHEMA public TO inventory_app;
SQL