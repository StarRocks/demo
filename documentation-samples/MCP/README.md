# Claude + StarRocks MCP — Olist demo (live environment)

Stand up a small StarRocks cluster on object storage, load a real public dataset, and ask
**Claude** plain-English questions about it through the **StarRocks MCP server** — Claude
discovers the schema, writes the SQL (including multi-table joins), and renders charts. All
data lives in object storage on **MinIO**.

The dataset is the **Olist Brazilian E-Commerce** set — 8 related tables, good for genuinely
complex joins. Everything runs on **1 FE + 1 CN**, on a laptop or a free cloud tier.

> **This repo is the live environment only.** It deliberately contains *no* canned queries,
> no expected answers, and no walkthrough — so that everything Claude shows is reasoned from
> the live schema in front of the audience, not recalled from material in the checkout. The
> build kit (setup notes, video script) lives in a separate repository — see [Setup kit](#setup-kit).

## What's in here

| File | What it is |
|---|---|
| [`docker-compose.yml`](docker-compose.yml) | StarRocks shared-data quick start (1 FE + 1 CN + MinIO), patched for laptop/Docker use. |
| [`storage_volume.sql`](storage_volume.sql) | Creates the S3 (MinIO) storage volume and sets it as default. |
| [`olist_schema.sql`](olist_schema.sql) | DDL for the 8 (+1 optional) Olist tables. |
| [`load_olist.sh`](load_olist.sh) | Stream Load for the Olist CSVs + a row-count check. |
| [`.mcp.json`](.mcp.json) | Wires the StarRocks and MinIO (AIStor) MCP servers to Claude. |
| [`.env.example`](.env.example) | Credentials/endpoints template — `cp` to `.env` and edit if needed. |

## Prerequisites

- **Docker** (Docker Desktop or engine + compose). The stack needs ~4 GB.
- **A MySQL client** (e.g. `mysql`) to run the SQL files.
- **[`uv`](https://docs.astral.sh/uv/)** — runs the StarRocks MCP server.
- **Claude Code** or **Claude Desktop** — to connect the MCP servers.
- The **Olist dataset** — downloaded automatically in step 4 via `kagglehub` (no Kaggle
  account needed): https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce
- *(Apple Silicon: StarRocks leans on AVX2 (x86); use an ARM build or expect slower emulation.)*

## Setup

### 1. Bring up StarRocks + MinIO

```bash
docker compose up --detach --wait --wait-timeout 120
```

A frontend (FE), a compute node (CN), and MinIO come up locally.

Check for healthy status on MinIO, FE, and CN services:

```bash
docker compose ps -a --format "table {{.Service}}\t{{.Status}}"
```

> Tip
>
> If the CN is not reporting healthy just wait a few seconds and check again, it is the last service to start.

### 2. Create a bucket in MinIO

Open the MinIO console at **http://localhost:9001** (login `miniouser` / `M!n10R0cks`),
Click on **Create Bucket**, and
create the bucket **`my-starrocks-bucket`**

### 3. Create the storage volume (storage-compute separation)

Examine the file `storage_volume.sql`. This creates a StarRocks storage volume using the bucket created in the previous step:

- increases the timeout for tablet creation
- creates the storage volume

```sql
-- belt-and-braces: default is 10 s, too tight for object-store tablet creation
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

### 4. Create the tables

```bash
docker compose exec -T starrocks-fe \
  mysql -h127.0.0.1 -P9030 -uroot < olist_schema.sql
```

> Tip:
>
> If you see an error "The specified bucket does not exist" you may have skipped part of the previous step, open the Minio UI and create the bucket specified above.

> Tip:
>
> This is a good time to try out the MCP server. Start Claude Code from the current directory and allow the two MCP servers (aistor and mcp-starrocks), then ask Claude to list the databases and describe the schema in the `olist` DB.

### 5. Load the data

Download the Olist CSVs from Kaggle with **kagglehub** — anonymous, no Kaggle account or API
token required. It caches the files locally and prints the folder they landed in:

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
redirect (no `/etc/hosts` edit / no sudo needed). The script prints a row-count check at the end.

### 6. Wire the MCP servers to Claude

```bash
cp .env.example .env   # edit only if you changed keys or ports
```

`.mcp.json` defines two MCP servers — `mcp-server-starrocks` (run via `uv`) and `aistor`
(MinIO, run via Docker) — both reading `.env`. Launch Claude Code from this directory (or add
the same block to Claude Desktop's config) and confirm the StarRocks tools are available
(e.g. ask *"What tools does the StarRocks MCP server provide?"*).

The StarRocks cluster is reachable over the MySQL protocol as `root` (no password) at
`localhost:9030`, database `olist`.

### 7. Ask questions

Ask Claude plain-English questions about the data — start by having it orient itself, e.g.
*"What tables are in this database, and how do they relate to each other?"*, then explore from
there. Claude inspects the live schema and writes the SQL itself.

> **Charts:** ask for an interactive chart in **HTML** format and the StarRocks MCP server
> writes it to `DEMO_Output/` (set by `STARROCKS_CHART_OUTPUT_DIR` in `.env`), with a static
> preview shown inline — open the HTML file in a browser to hover / zoom / pan.

## Troubleshooting

- **`CREATE TABLE` hangs / times out:** the CN's AWS SDK is probing the EC2 metadata service.
  The compose file already sets `AWS_EC2_METADATA_DISABLED=true` on `starrocks-cn`; if you use
  your own compose file, add it.
- **Stream Load redirect errors:** use `FE_HTTP_PORT=8040` (as above) to post to the CN
  directly, or add `127.0.0.1 starrocks-cn` to `/etc/hosts`.
- **`order_reviews` rejects rows:** the free-text comment columns contain embedded newlines;
  use the LEAN variant noted in `load_olist.sh`.

## Setup kit

The build/setup material — narrated walkthrough and the video script — is maintained
separately so this environment stays free of canned answers:

- **StarRocks MCP server:** https://github.com/StarRocks/mcp-server-starrocks
- **Setup kit:** *(add the StarRocks-org setup repo URL here once published)*

## Links

- StarRocks shared-data quick start — https://docs.starrocks.io/docs/quick_start/shared-data/
- StarRocks MCP server — https://github.com/StarRocks/mcp-server-starrocks
- MinIO MCP server (AIStor) — https://github.com/minio/mcp-server-aistor
- Olist dataset — https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce

## Credits & license

- **Olist Brazilian E-Commerce** dataset via Kaggle, licensed **CC BY-NC-SA 4.0
  (non-commercial)** — used here for educational demonstration; credit retained to Olist.
  Confirm terms before any commercial reuse.
- StarRocks and the StarRocks MCP server are open source (Apache-2.0 / project license).
- MinIO and `mcp-server-aistor` © MinIO, Inc.
</content>
</invoke>
