# Claude + StarRocks + Superset MCP — Olist dashboard demo (live environment)

Stand up a small StarRocks cluster on object storage, load a real public dataset, and ask
**Claude** plain-English questions about it — then have Claude turn the answers into a real
**Apache Superset** dashboard. Claude discovers the schema, writes the SQL (including
multi-table joins), saves it as a Superset dataset, builds charts on it, and assembles them
into a dashboard you can open in a browser. Data lives in object storage on **MinIO**.

This builds on the [`MCP`](../MCP) demo — same cluster, same dataset, same StarRocks and MinIO
MCP servers — and adds Superset and the **Superset MCP service** that ships with Superset 6.1.

The dataset is the **Olist Brazilian E-Commerce** set — 8 related tables, good for genuinely
complex joins. Everything runs on **1 FE + 1 CN**, on a laptop or a free cloud tier.

> **This directory contains just enough to get you going.** It deliberately contains *no*
> canned queries, no expected answers, no prebuilt charts, and no walkthrough — so that
> everything Claude shows is reasoned from the live schema, not recalled from material in the
> checkout.

## What's in here

| File | What it is |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Instructions to Claude Code to show its work, rely on the live schema, and build in Superset rather than writing chart code. You should read this file. |
| [`settings.json`](.claude/settings.json) | Content is `"autoMemoryEnabled": false` to prevent Claude from remembering the questions (and answers) asked in previous sessions. You can remove this if you like, it is in here to prevent the author's questions from biasing your experimentation. |
| [`docker-compose.yml`](docker-compose.yml) | StarRocks shared-data quick start (1 FE + 1 CN + MinIO), plus Superset, its Postgres metadata database, and the Superset MCP service. |
| [`Dockerfile.superset`](Dockerfile.superset) | Superset image plus the three packages the released image doesn't ship: the StarRocks dialect, `fastmcp`, and the Postgres driver. |
| [`superset_config.py`](superset_config.py) | Superset settings for the demo, including the two StarRocks interop fixes described in [Notes on the Superset side](#notes-on-the-superset-side). |
| [`bootstrap_superset.sh`](bootstrap_superset.sh) | One-shot setup: migrate the metadata DB, create the admin user, register StarRocks as a Superset database connection. |
| [`storage_volume.sql`](storage_volume.sql) | Creates the S3 (MinIO) storage volume and sets it as default. |
| [`olist_schema.sql`](olist_schema.sql) | DDL for the 8 (+1 optional) Olist tables. |
| [`load_olist.sh`](load_olist.sh) | Stream Load for the Olist CSVs + a row-count check. |
| [`.mcp.json`](.mcp.json) | Wires the StarRocks, MinIO (AIStor), and Superset MCP servers to Claude. |
| [`.env.example`](.env.example) | Credentials/endpoints template — `cp` to `.env` and edit if needed. |

## What comes up

| Service | URL / port | Login |
|---|---|---|
| Superset | http://localhost:8088 | `admin` / `SuperSetR0cks!` |
| Superset MCP service | http://localhost:5008/mcp | none — see [the security note](#a-note-on-the-mcp-services-auth) |
| MinIO console | http://localhost:9001 | `miniouser` / `M!n10R0cks` |
| StarRocks FE (MySQL protocol) | `localhost:9030` | `root`, no password |

## Prerequisites

- **Docker** (Docker Desktop or engine + compose). This stack needs ~6 GB — more than the
  `MCP` demo, because of Superset and its metadata database.
- **A MySQL client** (e.g. `mysql`) to run the SQL files — this is provided by the StarRocks
  FE server, so all of the SQL commands below run with `docker compose exec`.
- **[`uv`](https://docs.astral.sh/uv/)** — runs the StarRocks MCP server and downloads the dataset.
- **Claude Code** or **Claude Desktop** — to connect the MCP servers.
- The **Olist dataset** — downloaded automatically in step 5 via `kagglehub` (no Kaggle
  account needed): https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce
- *(Apple Silicon: StarRocks leans on AVX2 (x86); use an ARM build or expect slower emulation.)*

> **Don't run this demo and the [`MCP`](../MCP) demo at the same time.** They use the same
> container names and the same ports (9030, 9000/9001, 8040). Bring the other one down first:
> `(cd ../MCP && docker compose down)`.

## Setup

### 0. Install prerequisites

#### `uv`

```bash
# macOS / Linux
curl -LsSf https://astral.sh/uv/install.sh | sh

# Windows (PowerShell)
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"

 # Or via Homebrew / pipx / pip
brew install uv
# pipx install uv
# pip install uv
```

See the [official `uv` installation guide](https://docs.astral.sh/uv/getting-started/installation/)
for other options. After installing, verify it is on your `PATH`:

```bash
uv --version
```

#### Docker

[https://www.docker.com/get-started/](https://www.docker.com/get-started/)

#### Claude Code

> Tip:
>
> This demo includes steps using Claude Code. Other LLMs work with MCP servers, but this demo
> has only been tested with Claude Code. Please open an issue and let us know what you
> experience with other LLMs.

[https://claude.com/product/claude-code](https://claude.com/product/claude-code)

### 1. Clone the demo repo

[https://github.com/StarRocks/demo](https://github.com/StarRocks/demo)

```bash
gh repo clone StarRocks/demo
```

or

```bash
git clone git@github.com:StarRocks/demo.git
```

### 2. Create your `.env`

Every service reads it, so this comes before `docker compose up`:

```bash
cd demo/documentation-samples/MCP-superset
cp .env.example .env
```

The defaults work as-is. Change `SUPERSET_SECRET_KEY` and `SUPERSET_ADMIN_PASSWORD` if this
will be reachable by anyone but you.

### 3. Bring up StarRocks, MinIO, and Superset

```bash
docker compose up --detach --wait --wait-timeout 600 superset-mcp
```

Specifying `superset-mcp` at the end of the `compose up` starts everything: it sits at the end of the dependency chain, so
Compose pulls the whole stack up behind it and `--wait` returns only once it is healthy.
(Waiting on *every* service instead would exit non-zero, because two of them are one-shot
bootstrap containers that finish and exit.)

The first run builds the Superset image and downloads several GB, so give it time. It brings up:

- MinIO, and a short-lived `minio_mc` container that creates the access key and the
  `my-starrocks-bucket` bucket
- a StarRocks frontend (FE) and compute node (CN)
- Postgres for Superset's metadata
- `superset-init`, which migrates that metadata database, creates the `admin` user, and
  registers StarRocks as a Superset database connection — then exits
- Superset itself, and the Superset MCP service

Check the results — `minio_mc` and `superset-init` are one-shot and should read `Exited (0)`;
everything else should be `healthy`:

```bash
docker compose ps -a --format "table {{.Service}}\t{{.Status}}"
```

> Tip
>
> If the CN is not reporting healthy just wait a few seconds and check again, it is the last
> StarRocks service to start.

Open Superset at **http://localhost:8088** and log in with `admin` / `SuperSetR0cks!`. Under
**Settings → Database Connections** there is one connection, **StarRocks**. It has nothing to
show yet — the next three steps create and fill the database it points at.

### 4. Create the storage volume (storage-compute separation)

Examine the file `storage_volume.sql`. This creates a StarRocks storage volume using the
bucket that `minio_mc` created:

- increases the timeout for tablet creation
- creates the storage volume

```sql
-- default is 10 s, too tight for object-store tablet creation
ADMIN SET FRONTEND CONFIG ('tablet_create_timeout_second'='60');

CREATE STORAGE VOLUME s3_volume
    TYPE = S3
    LOCATIONS = ("s3://my-starrocks-bucket/")
    PROPERTIES (
        "enabled" = "true",
        "aws.s3.endpoint" = "minio:9000",
        "aws.s3.access_key" = "AAAAAAAAAAAAAAAAAAAA",
        "aws.s3.secret_key" = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        "aws.s3.use_instance_profile" = "false",
        "aws.s3.use_aws_sdk_default_behavior" = "false"
    );
```

- sets the new storage volume as the default
- shows the details

```sql
SET s3_volume AS DEFAULT STORAGE VOLUME;   -- REQUIRED before CREATE DATABASE/TABLE
DESC STORAGE VOLUME s3_volume\G            -- verify: enabled=true, IsDefault=true
```

```bash
docker compose exec -T starrocks-fe \
  mysql -P9030 -h127.0.0.1 -uroot < storage_volume.sql
```

This must succeed (and the volume must be the default) before any `CREATE TABLE`.

```sql
*************************** 1. row ***************************
     Name: s3_volume
     Type: S3
IsDefault: true
 Location: s3://my-starrocks-bucket/
   Params: {"aws.s3.access_key":"******","aws.s3.secret_key":"******","aws.s3.endpoint":"minio:9000","aws.s3.region":"us-east-1","aws.s3.use_instance_profile":"false","aws.s3.use_web_identity_token_file":"false","aws.s3.use_aws_sdk_default_behavior":"false"}
  Enabled: true
  Comment:
```

### 5. Create the tables

```bash
docker compose exec -T starrocks-fe \
  mysql -h127.0.0.1 -P9030 -uroot < olist_schema.sql
```

> Tip:
>
> If you see an error "The specified bucket does not exist", the `minio_mc` bootstrap did not
> finish. Open the MinIO console at http://localhost:9001 and check for a bucket named
> `my-starrocks-bucket`; create it if it is missing, then re-run the previous step.

### 6. Load the data

Download the Olist CSVs from Kaggle with **kagglehub** — anonymous, no Kaggle account or API
token required. It caches the files locally and prints the folder they landed in.

Download the data and capture the folder it landed in as `CSV_DIR`. kagglehub prints a version
warning to stdout, so take the last line (the path) with `tail -n1`:

```bash
export CSV_DIR="$(uv run --with "kagglehub==0.3.12" python \
  -c "import kagglehub; print(kagglehub.dataset_download('olistbr/brazilian-ecommerce'))" \
  | tail -n1)"
echo "$CSV_DIR"   # sanity check: should be a .../brazilian-ecommerce/versions/N path
```

> The `kagglehub==0.3.12` pin is deliberate: newer releases (1.0.x) pull a `kagglesdk` build
> that fails to import (`ModuleNotFoundError: kagglesdk.competitions.legacy`). 0.3.12 downloads
> public datasets anonymously and works fine. kagglehub caches the files, so re-running is cheap.

Then run the loader (`CSV_DIR` is already exported):

```bash
FE_HTTP_PORT=8040 bash load_olist.sh
```

`FE_HTTP_PORT=8040` posts Stream Load straight to the CN, avoiding a `starrocks-cn` hostname
redirect (no `/etc/hosts` edit / no sudo needed). The script prints a row-count check at the end;
run it to confirm ~99,441 orders and ~1,000,163 geolocation rows:

```bash
docker compose exec -T starrocks-fe mysql -h127.0.0.1 -P9030 -uroot \
  -e "SELECT 'orders' t, count(*) c FROM olist.orders
      UNION ALL SELECT 'order_items', count(*) FROM olist.order_items;"
```

### 7. Wire the MCP servers to Claude

`.mcp.json` defines three MCP servers, all reading `.env`:

| Server | How it runs | What it's for |
|---|---|---|
| `mcp-server-starrocks` | from GitHub via `uv` — **nothing to install by hand** | schema discovery, SQL, quick Plotly charts |
| `aistor` | MinIO's server, via Docker | what's actually in the object store |
| `superset` | HTTP to the `superset-mcp` container on port 5008 | datasets, charts, dashboards |

Launch Claude Code from this directory (or add the same block to Claude Desktop's config).

> **Approve the servers on first launch.** Project-scoped servers from `.mcp.json` are *not*
> trusted automatically — Claude Code prompts *"New MCP servers found — approve?"* the first
> time you launch here. Accept it. (The pre-approval lives in `.claude/settings.local.json`,
> which is gitignored, so it is **absent on a fresh clone** — that's expected; approving via
> the prompt recreates it.)
>
> **Confirm the connections with `/mcp`.** Run `/mcp` inside Claude Code and check that
> `mcp-server-starrocks`, `aistor`, and `superset` all show **connected**. This is the
> authoritative status check — if a server isn't connected, its tools won't load no matter how
> you ask.
>
> **First launch is slow.** The first `uv run` downloads the StarRocks server's dependencies
> (pyarrow, kaleido, …, ~115 MB), so it can take a couple of minutes to come up the first time.
> Subsequent launches are fast.

Once `/mcp` shows all three connected, confirm the tools are available:

> 1. *What tools does the Superset MCP server provide?*

You should hear about `create_virtual_dataset`, `generate_chart`, `generate_dashboard`,
`execute_sql`, and friends.

> **Only four Superset tools in `/mcp`? That's expected.** The Superset service publishes a
> compact front door — `health_check`, `get_instance_info`, `search_tools`, and `call_tool` —
> and Claude reaches the other two dozen tools by searching for them and calling them through
> `call_tool`. Asking the question above is the reliable way to see the real inventory.

The StarRocks cluster is reachable over the MySQL protocol as `root` (no password) at
`localhost:9030`, database `olist`.

### 8. Ask questions, then build the dashboard

Four prompts take you from an empty Superset to a dashboard. Continuing the numbering from
step 7:

> 2. *What tables are in this database, and how do they relate to each other?*

Claude inspects the live schema and works out the relationships itself. This is where the
[`MCP`](../MCP) demo starts too — nothing in this checkout tells it the answer.

> 3. *Let's find some interesting business insights in the data, and then chart some of them
>    in Superset. Plot orders by customer state.*

Deliberately open-ended, with one concrete ask at the end. Claude explores, then — for the
chart — writes the SQL, verifies it, saves it with `create_virtual_dataset`, and builds the
chart with `generate_chart`. Watch which joins it picks: orders carry a `customer_id`, so
"by customer state" is a join away, not a column.

> 4. *Add more charts and put them on a dashboard.*

`generate_dashboard` assembles them and hands back a URL like
`http://localhost:8088/superset/dashboard/1/`. Open it — the charts query StarRocks live, so
they refresh when the data does.

Then keep going — this is where it gets interesting:

- Ask for a specific chart type ("a big number with a trendline", "a pie chart of payment
  types") and see how it maps the request onto Superset's chart types.
- Add to the dashboard you already have, rather than making a second one.
- Ask *why* it chose a chart type, then ask it to change it.
- Push on a surprising number — ask it to show the rows behind the aggregate.

> **Two chart paths, on purpose.** The StarRocks MCP server also draws charts — interactive
> Plotly HTML written to `DEMO_Output/` (set by `STARROCKS_CHART_OUTPUT_DIR` in `.env`). Those
> are throwaway, good for exploring. The Superset ones are saved objects with a URL, an owner,
> and a dashboard. `CLAUDE.md` tells Claude to use the first while thinking and the second when
> the answer is worth keeping.

## Notes on the Superset side

Three things in this demo exist because the released Superset image doesn't cover them, and
they're worth knowing about if you adapt this for your own cluster.

**1. Three packages are added to the image** ([`Dockerfile.superset`](Dockerfile.superset)).
The `starrocks` SQLAlchemy dialect (without it: *"Could not load database driver:
StarRocksEngineSpec"*), `fastmcp` (the MCP service *code* ships in the image, its dependency
does not, so `superset mcp run` exits with *"MCP service dependencies not installed"*), and
`psycopg2-binary` for the metadata database. They are installed with `uv pip install --python
/app/.venv/bin/python` — plain `pip` on the image's `PATH` belongs to the *system* Python and
installs where Superset never looks.

**2. PyMySQL is registered as `MySQLdb`** ([`superset_config.py`](superset_config.py)).
StarRocks speaks the MySQL wire protocol, so Superset's StarRocks engine spec extends the
MySQL one — including a `get_datatype()` that does `import MySQLdb` to read type constants.
`MySQLdb` comes from `mysqlclient`, a C extension with no Linux wheels; the `starrocks` package
brings pure-Python PyMySQL instead. `pymysql.install_as_MySQLdb()` satisfies the import.
Without it, *every* query through Superset fails with `starrocks error: No module named
'MySQLdb'`. The alternative is compiling `mysqlclient` into the image (`apt-get install
default-libmysqlclient-dev build-essential pkg-config` first), which works but is a much
heavier build.

**3. Result column types are translated from wire names to SQL names**
([`superset_config.py`](superset_config.py)). Superset types the columns of a *virtual* dataset
from the type codes the driver returns, and those carry MySQL's internal names: a `BIGINT`
arrives as `LONGLONG`, a `DECIMAL` as `NEWDECIMAL`. Superset doesn't recognize either as
numeric, so the MCP chart tool rejects `SUM`/`AVG` over them before the query runs —
*"Cannot apply SUM to non-numeric column 'revenue' (type: NEWDECIMAL)"*. Since every dataset
built from a join or an aggregate hits this, the demo maps the wire names to the SQL names they
stand for, via the `FLASK_APP_MUTATOR` hook. Without the mapping you'd have to write
`CAST(... AS DOUBLE)` around every measure.

### A note on the MCP service's auth

The MCP service runs in its **single-user development auth mode**: `MCP_DEV_USERNAME = "admin"`
in `superset_config.py`, no token. Anything that can reach port 5008 acts as a Superset admin.
That's the right trade for a laptop demo and the wrong one for anything shared — for a real
deployment set `MCP_AUTH_ENABLED = True` with the `MCP_JWT_*` settings and leave
`MCP_DEV_USERNAME` unset. See Superset's
[`PRODUCTION.md`](https://github.com/apache/superset/blob/6.1.0/superset/mcp_service/PRODUCTION.md).

## Troubleshooting

- **`superset` MCP server not connected / tools missing:** check the service is up and
  answering — `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:5008/mcp` should print
  `405` (the endpoint only accepts POST; 405 means it's alive). Then check
  `docker compose logs superset-mcp`. Also confirm the project servers were approved: run
  `/mcp` in Claude Code, and if they aren't **connected**, relaunch from this directory and
  accept the *"New MCP servers found — approve?"* prompt (or add
  `"enabledMcpjsonServers": ["mcp-server-starrocks", "aistor", "superset"]` to
  `.claude/settings.local.json`).
- **StarRocks MCP tools never load / server "still connecting":** same approval story as above.
  You do **not** need to install that server separately — `.mcp.json` runs it from GitHub via
  `uv`. The first launch downloads ~115 MB of dependencies; that's expected once, not a failure.
- **`starrocks error: No module named 'MySQLdb'` in Superset:** `superset_config.py` isn't
  being loaded, or its `pymysql.install_as_MySQLdb()` line was removed. Confirm the file is
  mounted: `docker compose exec superset ls -l /app/pythonpath/superset_config.py`.
- **`Cannot apply SUM to non-numeric column ... (type: NEWDECIMAL)`:** the type mapping in
  `superset_config.py` isn't in effect — restart Superset and the MCP service after editing it
  (`docker compose restart superset superset-mcp`). Datasets created *before* the fix keep
  their old column types; recreate them, or fix the types in **Edit dataset → Columns**.
- **Superset container exits with "Refusing to start due to insecure SECRET_KEY":**
  `SUPERSET_SECRET_KEY` is missing from `.env`. Copy `.env.example` again.
- **Port already in use (8088, 5008, 9030, 9000/9001, 8040):** the [`MCP`](../MCP) demo (or
  another Superset) is still running. `docker compose ps -a` in the other directory, then
  `docker compose down`.
- **`mysql` client fails with `Authentication plugin 'mysql_native_password' cannot be loaded`:**
  a Homebrew `mysql`-client quirk, not a StarRocks problem. Query through the container instead:
  `docker compose exec -T starrocks-fe mysql -h127.0.0.1 -P9030 -uroot -e "SHOW DATABASES;"`.
- **`CREATE TABLE` hangs / times out:** the CN's AWS SDK is probing the EC2 metadata service.
  The compose file already sets `AWS_EC2_METADATA_DISABLED=true` on `starrocks-cn`; if you use
  your own compose file, add it.
- **Stream Load redirect errors:** use `FE_HTTP_PORT=8040` (as above) to post to the CN
  directly, or add `127.0.0.1 starrocks-cn` to `/etc/hosts`.
- **`order_reviews` rejects rows:** the free-text comment columns contain embedded newlines;
  use the LEAN variant noted in `load_olist.sh`.
- **Start over:** `docker compose down --volumes` drops the Superset metadata (dashboards,
  charts, datasets) and the StarRocks data along with it. Re-run from step 3.

## Setup kit

The build/setup material — narrated walkthrough and the video script — is maintained
separately so this environment stays free of canned answers:

- **StarRocks MCP server:** https://github.com/StarRocks/mcp-server-starrocks
- **Setup kit:** *(add the StarRocks-org setup repo URL here once published)*

## Links

- StarRocks shared-data quick start — https://docs.starrocks.io/docs/quick_start/shared-data/
- StarRocks MCP server — https://github.com/StarRocks/mcp-server-starrocks
- MinIO MCP server (AIStor) — https://github.com/minio/mcp-server-aistor
- Superset MCP service — https://github.com/apache/superset/tree/6.1.0/superset/mcp_service
- Superset + StarRocks connection docs — https://docs.starrocks.io/docs/integrations/BI_integrations/Superset/
- Olist dataset — https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce

## Credits & license

- **Olist Brazilian E-Commerce** dataset via Kaggle, licensed **CC BY-NC-SA 4.0
  (non-commercial)** — used here for educational demonstration; credit retained to Olist.
  Confirm terms before any commercial reuse.
- StarRocks and the StarRocks MCP server are open source (Apache-2.0 / project license).
- Apache Superset is an Apache Software Foundation project (Apache-2.0).
- MinIO and `mcp-server-aistor` © MinIO, Inc.
