# ---------------------------------------------------------------------------
# Superset configuration for the StarRocks + MinIO + Superset MCP demo.
#
# Mounted into every Superset container at /app/pythonpath/superset_config.py
# (PYTHONPATH=/app/pythonpath is already set in the image).
#
# Demo-grade settings -- see the comments on MCP_DEV_USERNAME and SECRET_KEY
# before reusing any of this outside a laptop.
# ---------------------------------------------------------------------------
import os

import pymysql

# StarRocks speaks the MySQL wire protocol, so Superset's StarRocks engine spec
# subclasses the MySQL one -- including get_datatype(), which does `import MySQLdb`
# just to read the FIELD_TYPE constants when naming result columns. MySQLdb comes
# from mysqlclient, a C extension with no Linux wheels; the `starrocks` package
# brings pure-Python PyMySQL instead. Registering PyMySQL under the MySQLdb name
# satisfies that import. Without this, every query through Superset (SQL Lab,
# charts, and the MCP execute_sql tool) fails with:
#   starrocks error: No module named 'MySQLdb'
# The alternative is compiling mysqlclient into the image -- see the README.
pymysql.install_as_MySQLdb()

# Superset refuses to start with the shipped default key.
SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "change-me-in-.env")

# Superset's own metadata (dashboards, charts, datasets) -- Postgres, not StarRocks.
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "SUPERSET_METADATA_URI",
    "postgresql+psycopg2://superset:superset@superset-db:5432/superset",
)

# --- MCP service ----------------------------------------------------------
# The MCP service acts as this Superset user. This is the single-user
# "development" auth mode: anything that can reach port 5008 acts as admin,
# with no token. Fine for a laptop demo, never for a shared deployment -- there,
# set MCP_AUTH_ENABLED = True with the MCP_JWT_* settings instead and leave
# MCP_DEV_USERNAME unset. Reading it from the environment is a convenience of
# this demo; Superset itself only looks at this config value.
MCP_DEV_USERNAME = os.environ.get("MCP_DEV_USERNAME", "admin")

# Where the MCP service tells clients to open charts and dashboards. This ends up
# in the links Claude hands back, so it has to be the URL *you* browse to, not the
# in-container hostname.
WEBDRIVER_BASEURL = os.environ.get("SUPERSET_PUBLIC_URL", "http://localhost:8088/")
WEBDRIVER_BASEURL_USER_FRIENDLY = WEBDRIVER_BASEURL

# --- Demo conveniences ----------------------------------------------------
# No Redis in this stack: keep caching in-process rather than failing over to it.
CACHE_CONFIG = {"CACHE_TYPE": "SimpleCache", "CACHE_DEFAULT_TIMEOUT": 300}
DATA_CACHE_CONFIG = CACHE_CONFIG
FILTER_STATE_CACHE_CONFIG = {"CACHE_TYPE": "SimpleCache", "CACHE_DEFAULT_TIMEOUT": 86400}
EXPLORE_FORM_DATA_CACHE_CONFIG = FILTER_STATE_CACHE_CONFIG

# Plain HTTP on localhost: no HTTPS for Talisman's security headers to enforce.
TALISMAN_ENABLED = False

FEATURE_FLAGS = {
    # Lets a chart's underlying query be edited and saved as a dataset from Explore.
    "ENABLE_TEMPLATE_PROCESSING": True,
}

SQLLAB_CTAS_NO_LIMIT = True


# ---------------------------------------------------------------------------
# StarRocks result columns: report SQL type names, not MySQL wire-protocol names
#
# Superset names the columns of a *virtual* dataset (a saved SQL query) from the
# type codes the driver returns on the wire. Because StarRocks speaks the MySQL
# protocol, those codes carry MySQL's internal names: a BIGINT arrives as
# LONGLONG, a DECIMAL as NEWDECIMAL. Superset does not recognize either as a
# number, so SUM/AVG/MIN/MAX over them is rejected before the query ever runs:
#
#   Cannot apply SUM to non-numeric column 'revenue' (type: NEWDECIMAL)
#
# Any dataset built from a join or an aggregate hits this, which is most of what
# this demo does. Translating the wire names to the SQL names they stand for
# fixes it for good, instead of leaving Claude to CAST(... AS DOUBLE) in every
# query it writes. FLASK_APP_MUTATOR runs after the app is built, which is the
# safe point to import Superset internals from this file.
_MYSQL_WIRE_TYPE_TO_SQL = {
    "TINY": "TINYINT",
    "SHORT": "SMALLINT",
    "LONG": "INT",
    "INT24": "INT",
    "LONGLONG": "BIGINT",
    "YEAR": "INT",
    "FLOAT": "FLOAT",
    "DOUBLE": "DOUBLE",
    "DECIMAL": "DECIMAL",
    "NEWDECIMAL": "DECIMAL",
    "BIT": "BOOLEAN",
    "NEWDATE": "DATE",
    "VAR_STRING": "VARCHAR",
    "STRING": "VARCHAR",
    "ENUM": "VARCHAR",
    "SET": "VARCHAR",
    "TINY_BLOB": "TEXT",
    "MEDIUM_BLOB": "TEXT",
    "LONG_BLOB": "TEXT",
    "BLOB": "TEXT",
}


def FLASK_APP_MUTATOR(app):  # noqa: N802
    from superset.db_engine_specs.starrocks import StarRocksEngineSpec

    inherited = StarRocksEngineSpec.get_datatype.__func__

    @classmethod
    def get_datatype(cls, type_code):
        datatype = inherited(cls, type_code)
        return _MYSQL_WIRE_TYPE_TO_SQL.get(datatype, datatype)

    StarRocksEngineSpec.get_datatype = get_datatype
