#!/bin/bash
# ---------------------------------------------------------------------------
# One-shot Superset bootstrap, run by the `superset-init` service in
# docker-compose.yml. Safe to re-run: every step is idempotent.
#
#   1. migrate Superset's metadata database
#   2. create the admin user
#   3. load Superset's default roles/permissions
#   4. register the StarRocks cluster as a Superset database connection
#   5. turn on the connection flags the MCP tools need
# ---------------------------------------------------------------------------
set -euo pipefail

echo "==> Migrating Superset metadata database"
superset db upgrade

echo "==> Creating admin user (${SUPERSET_ADMIN_USERNAME})"
superset fab create-admin \
  --username "${SUPERSET_ADMIN_USERNAME}" \
  --firstname Superset \
  --lastname Admin \
  --email admin@localhost \
  --password "${SUPERSET_ADMIN_PASSWORD}" \
  || echo "    (admin already exists -- continuing)"

echo "==> superset init (roles and permissions)"
superset init

echo "==> Registering StarRocks as the '${SUPERSET_STARROCKS_DB_NAME}' database connection"
superset set-database-uri \
  --database_name "${SUPERSET_STARROCKS_DB_NAME}" \
  --uri "${SUPERSET_STARROCKS_URI}"

# set-database-uri creates the connection but leaves its capability flags at the
# model defaults. Explicitly allow what this demo needs: SQL Lab access, so the
# MCP `execute_sql` tool and virtual datasets work. DML stays off -- Claude reads
# from StarRocks, it does not write to it.
echo "==> Enabling SQL Lab on the connection"
python <<'PY'
from superset.app import create_app

app = create_app()
with app.app_context():
    import os
    from superset import db
    from superset.models.core import Database

    name = os.environ["SUPERSET_STARROCKS_DB_NAME"]
    database = db.session.query(Database).filter_by(database_name=name).one()
    database.expose_in_sqllab = True
    database.allow_dml = False
    database.allow_ctas = False
    database.allow_cvas = False
    db.session.commit()
    print(f"    {database.database_name}: {database.sqlalchemy_uri}")
PY

# The web and MCP containers run as the unprivileged `superset` user; this
# bootstrap runs as root, so hand back anything it created in the shared volume.
chown -R superset:superset /app/superset_home

echo "==> Bootstrap complete"
