# Retail eCommerce Funnel Analysis Demo with 1 million members and 87 million record dataset using StarRocks | Demo of StarRocks using Hudi, Iceberg, Delta Lake External Catalog on MinIO + HMS + Apache XTable

![StarRocks Technical Overview](https://github.com/StarRocks/demo/assets/749093/cf5aa363-2b31-4e3b-b9cd-fe94020d3071)

![Screenshot 2024-03-01 at 1 05 20 PM](https://github.com/StarRocks/demo/assets/749093/e810ae65-59b9-4edd-a8dd-87fe2edd4124)

> [!IMPORTANT]  
>  Set the memory in Docker Desktop. Setting -> Resources -> Memory Limit should we set at least 16GB.

## Which connectors does this demo use? (You do **not** need Debezium JDBC)

This demo runs out of the box without any extra configuration, and **you do not need
the Debezium JDBC connector**. It is worth understanding the two halves of the pipeline:

- **Source (always Debezium):** the Debezium PostgreSQL *source* connector — bundled in
  the `debezium/connect:2.6` image — captures changes from PostgreSQL and writes them to
  Kafka topics. This is always used.
- **Sink (StarRocks by default):** the **StarRocks Kafka sink connector** consumes those
  topics and loads the data into StarRocks. You download it during setup (see
  [Download the StarRocks Kafka sink connector](#download-the-starrocks-kafka-sink-connector-required))
  and it is used by the registration steps below. This is the recommended sink for StarRocks.

The `DEBEZIUM_JDBC_CONNECTOR_PATH` environment variable only affects the **sink** side. If
you leave it unset (the default), you use the StarRocks Kafka sink connector and the demo
works as written. The Debezium JDBC sink is an **optional alternative** — see
[Configure the Debezium JDBC connector path](#optional-configure-the-debezium-jdbc-connector-path)
for how to enable it and where to get the files.

### StarRocks Kafka sink vs. Debezium JDBC sink

|  | StarRocks Kafka sink (default) | Debezium JDBC sink (optional) |
|---|---|---|
| **Write path** | Native [Stream Load](https://docs.starrocks.io/docs/loading/StreamLoad/) HTTP bulk API | Row-by-row JDBC `INSERT`/`UPSERT` statements |
| **Throughput** | High — micro-batches optimized for StarRocks | Lower — JDBC statement execution |
| **Target** | StarRocks only | Any JDBC database (MySQL, PostgreSQL, StarRocks via MySQL protocol, …) |
| **Schema handling** | You pre-create the table | Can auto-create tables and evolve schema (DDL) |
| **CDC ops** | Needs `addfield` + `unwrap` transforms | Native upsert/delete handling |

**For loading into StarRocks, the StarRocks Kafka sink is the recommended choice** —
Stream Load is far more efficient than row-wise JDBC inserts. You might still prefer the
Debezium JDBC sink when:

1. **You sink to heterogeneous targets** (e.g. MySQL, PostgreSQL, *and* StarRocks) and
   want one connector type instead of a different sink per target.
2. **You want automatic table creation / schema evolution** at the destination, which the
   StarRocks connector does not do.
3. **You want native CDC upsert/delete semantics** without StarRocks-specific transforms.

## Environment Setup

### Download the StarRocks Kafka sink connector (required)

The StarRocks Kafka sink connector JAR is **not** checked into this repo, so it never
drifts out of sync with upstream releases. Download the latest release into
`kafka-connect-connectors-jar/` before starting the stack — that directory is bind-mounted
into the `connect` container's plugin path (`/opt/connectors`):

```
mkdir -p kafka-connect-connectors-jar
curl -s https://api.github.com/repos/StarRocks/starrocks-connector-for-kafka/releases/latest \
  | grep browser_download_url \
  | grep with-dependencies \
  | cut -d '"' -f 4 \
  | xargs -n1 curl -L --output-dir kafka-connect-connectors-jar -O
```

This downloads the single `starrocks-connector-for-kafka-<version>-with-dependencies.jar`
fat JAR (connector plus all dependencies). To pin a specific version instead of the
latest, grab the matching `…-with-dependencies.jar` from the
[releases page](https://github.com/StarRocks/starrocks-connector-for-kafka/releases) into
the same directory.

### (Optional) Configure the Debezium JDBC connector path

The `connect` (Debezium Kafka Connect) service bind-mounts a host directory into
`/kafka/connect/debezium-connector-jdbc` so the Debezium JDBC connector JARs are on
the Connect plugin path. The host directory is controlled by the
`DEBEZIUM_JDBC_CONNECTOR_PATH` environment variable:

```yaml
- ${DEBEZIUM_JDBC_CONNECTOR_PATH:-./kafka-connect-connectors-jar}:/kafka/connect/debezium-connector-jdbc
```

- If `DEBEZIUM_JDBC_CONNECTOR_PATH` is **set**, that directory is mounted.
- If it is **unset**, it falls back to the in-repo `./kafka-connect-connectors-jar`
  directory (relative to this `docker-compose.yml`), so `docker compose up` still
  starts without any extra configuration.

If you have your own build of the [Debezium JDBC connector](https://debezium.io/documentation/reference/stable/connectors/jdbc.html),
point the variable at the directory containing its JARs before starting the stack —
either by exporting it in your shell:

```
export DEBEZIUM_JDBC_CONNECTOR_PATH=/path/to/debezium-connector-jdbc
```

or by adding it to a `.env` file next to `docker-compose.yml`:

```
DEBEZIUM_JDBC_CONNECTOR_PATH=/path/to/debezium-connector-jdbc
```

> [!NOTE]
> This replaces a previously hardcoded, machine-specific path so the demo runs on any
> machine (see issue #86). The default value is just a placeholder that exists in the
> repo so Compose has a valid directory to mount; supply the real connector path if you
> need the Debezium JDBC connector.

### Start the environment

1. Start the environment

`docker compose up --detach --wait --wait-timeout 60`

Once the `starrocks-fe` service is healthy, bootstrap the StarRocks cluster for the
demo by creating the `demo` database and setting the default replication factor to 1
(a single-BE local cluster cannot satisfy the out-of-the-box default of 3 replicas):

```
docker compose exec starrocks-fe mysql -h starrocks-fe -P 9030 -u root -e "
  CREATE DATABASE IF NOT EXISTS demo;
  ADMIN SET FRONTEND CONFIG ('default_replication_num' = '1');"
```

> These two commands were previously run automatically by a `starrocks-toolkit`
> helper container. That image was removed, so run them yourself here. The rest of the
> guide assumes the `demo` database already exists.

2. Upload the sample data

The `warehouse` and `huditest` buckets are created automatically by the `mc`
init container when the stack starts, so there is nothing to create by hand.

Open the MinIO console at http://localhost:9000/ (login `admin` / `M!nIOR0cks`)
and upload the 2 parquet files to the `warehouse` bucket:
* https://cdn.starrocks.io/dataset/user_behavior_sample_data.parquet
* https://cdn.starrocks.io/dataset/item_sample_data.parquet

3. Run the Spark Scala code to insert data

Open a `spark-shell` in the `spark-hudi` container:

```
docker compose exec -it spark-hudi /opt/spark/bin/spark-shell --driver-memory 8G
```

The required Spark settings (Kryo serializer, Hive metastore URI, and the
S3A/MinIO credentials) are already provided via `spark-conf/spark-defaults.conf`.
On the first launch Spark resolves the Hudi and `hadoop-aws` packages from Maven
Central, which takes a minute or two.

The shell runs in local mode, so `--driver-memory 8G` is the memory available for the
whole job. Keep it well under your Docker Desktop memory limit (set to at least 16 GB
above), since StarRocks and the other services need their share too.

Once the `scala>` prompt appears, the default log level is `WARN`, which prints a lot of
runtime noise (including a harmless `MetricsConfig: Cannot locate configuration` warning)
during the writes and queries. To quiet that down to errors only, run:

```
sc.setLogLevel("ERROR")
```

(Set it back to `"WARN"` or `"INFO"` if you ever need that output to debug.)

> [!NOTE]
> `setLogLevel` controls only the Spark/Hadoop runtime logging. A few warnings printed by
> the JVM and the Scala REPL itself are *not* affected by it and are harmless — you will
> still see things like `Unable to attach Serviceability Agent`, `Unable to get
> Instrumentation`, and `warning: one deprecation` (from a deprecated Hudi import). These
> are expected; ignore them.

> [!IMPORTANT]
> Enter each block below using the Scala REPL's **`:paste` mode**, not a plain paste.
> The `df.write…save(basePath)` call is a single multi-line statement; if you paste it
> line-by-line at the `scala>` prompt the REPL gets stuck in continuation mode (showing
> `|` prompts) and the write never runs. With `:paste` the whole block compiles as one
> unit. For each block:
>
> 1. Type `:paste` and press Enter.
> 2. Paste the entire block.
> 3. After pasting the commands, press `Return` and then `Ctrl-D` to compile and run it.
> 4. Wait for the write to finish and the `scala>` prompt to return before starting the
>    next block.

> [!TIP]
> The `user_behavior` write processes the full ~1.1 GB / 87-million-row parquet file as a
> Hudi `bulk_insert`. The job appears to stall on a late Spark stage (around "stage 9")
> for several minutes — this is normal, it is doing the work, not hung. The `item` table
> is tiny by comparison and finishes quickly. Watch progress at the Spark UI on
> http://localhost:4041.

Load the first table (`user_behavior`):

```
import org.apache.hudi.QuickstartUtils._
import scala.collection.JavaConversions._
import org.apache.spark.sql.SaveMode._
import org.apache.hudi.DataSourceReadOptions._
import org.apache.hudi.DataSourceWriteOptions._
import org.apache.hudi.config.HoodieWriteConfig._
import org.apache.hudi.common.model.HoodieRecord

val df = spark.read.parquet("s3a://warehouse/user_behavior_sample_data.parquet")

val databaseName = "hudi_ecommerce"
val tableName = "user_behavior"
val basePath = "s3a://huditest/hudi_ecommerce_user_behavior"

df.write.format("hudi").
  options(getQuickstartWriteConfigs).
  option(org.apache.hudi.config.HoodieWriteConfig.TABLE_NAME, tableName).
  option("hoodie.datasource.write.operation", "bulk_insert").
  option("hoodie.datasource.hive_sync.enable", "true").
  option("hoodie.datasource.hive_sync.mode", "hms").
  option("hoodie.datasource.hive_sync.database", databaseName).
  option("hoodie.datasource.hive_sync.table", tableName).
  option("hoodie.datasource.hive_sync.metastore.uris", "thrift://hive-metastore:9083").
  mode(Overwrite).
  save(basePath)
```

Then load the second table (`item`) the same way — `:paste`, paste the block, `Ctrl-D`:

```
import org.apache.hudi.QuickstartUtils._
import scala.collection.JavaConversions._
import org.apache.spark.sql.SaveMode._
import org.apache.hudi.DataSourceReadOptions._
import org.apache.hudi.DataSourceWriteOptions._
import org.apache.hudi.config.HoodieWriteConfig._
import org.apache.hudi.common.model.HoodieRecord

val df = spark.read.parquet("s3a://warehouse/item_sample_data.parquet")

val databaseName = "hudi_ecommerce"
val tableName = "item"
val basePath = "s3a://huditest/hudi_ecommerce_item"

df.write.format("hudi").
  options(getQuickstartWriteConfigs).
  option(org.apache.hudi.config.HoodieWriteConfig.TABLE_NAME, tableName).
  option("hoodie.datasource.write.operation", "bulk_insert").
  option("hoodie.datasource.hive_sync.enable", "true").
  option("hoodie.datasource.hive_sync.mode", "hms").
  option("hoodie.datasource.hive_sync.database", databaseName).
  option("hoodie.datasource.hive_sync.table", tableName).
  option("hoodie.datasource.hive_sync.metastore.uris", "thrift://hive-metastore:9083").
  mode(Overwrite).
  save(basePath)
```

Both writes sync to the Hive metastore, so before moving on, confirm both Hudi tables
were created and actually contain data (a common failure is the second block not running
— see the `:paste` note above). List inside each table directory from the host:

```
docker compose exec mc mc ls minio/huditest/hudi_ecommerce_user_behavior/
docker compose exec mc mc ls minio/huditest/hudi_ecommerce_item/
```

Each should list a `.hoodie/` metadata directory and one or more data files (the Hudi
base/parquet files), confirming the table has data.

> [!NOTE]
> A top-level `docker compose exec mc mc ls minio/huditest` shows the table folders with a
> size of `0B`. That is expected — those are object-store *prefixes*, not real objects, so
> they always report `0B`. Listing *inside* a table directory (as above) shows the actual
> data files and their sizes. If `hudi_ecommerce_item/` is missing or empty, re-run the
> `item` block with `:paste`.

4. Have StarRocks connect to Hudi on S3

```
docker compose exec -it starrocks-fe mysql -P 9030 -h starrocks-fe -u root --prompt="StarRocks > "
```
```
CREATE EXTERNAL CATALOG hudi_catalog_hms
PROPERTIES
(
    "type" = "hudi",
    "hive.metastore.type" = "hive",
    "hive.metastore.uris" = "thrift://hive-metastore:9083",
    "aws.s3.use_instance_profile" = "false",
    "aws.s3.access_key" = "admin",
    "aws.s3.secret_key" = "M!nIOR0cks",
    "aws.s3.region" = "us-east-1",
    "aws.s3.enable_ssl" = "false",
    "aws.s3.enable_path_style_access" = "true",
    "aws.s3.endpoint" = "http://minio:9000"
);
set catalog hudi_catalog_hms;
show databases;
use hudi_ecommerce;
show tables;
select count(*) from user_behavior;
select count(*) from item;
```
Validate the number of entries in user_behavior and item tables.

> [!TIP]
> `user_behavior` has more than 87 million rows, and this first query is a cold read of
> the Hudi table from object storage on a single backend — give it a few minutes to
> return. It is not hung. Subsequent queries are faster as the StarRocks data cache warms up.

5. [Optional] Use Apache XTable to generate Iceberg and Delta Lake metadata

OneTable is now [Apache XTable (incubating)](https://github.com/apache/incubator-xtable).
It converts the Hudi table *in place* by writing Iceberg and Delta Lake metadata
next to the existing Hudi data, so the same files can be read as all three formats.

> [!IMPORTANT]
> XTable's bundled CLI jar is **not published** (it bundles dependencies the ASF
> cannot redistribute), so you build it from source with JDK 11. **Build it inside the
> `spark-hudi` container** — that image already has JDK 11 (with `javac`), so you do not
> need Java, Maven, or git on your host. The XTable source tarball ships no Maven wrapper
> and the container has no system Maven, so download a standalone Maven into the container
> first (a source tarball is used so no `git` is needed). The build then takes two steps
> because the `xtable-utilities` module is excluded from the default Maven reactor:
> ```
> docker compose exec -it spark-hudi bash
> cd /tmp
> # The spark user's passwd home is /nonexistent. The JVM derives user.home from
> # /etc/passwd (NOT $HOME), so Maven would try to write ~/.m2 under /nonexistent.
> # Override user.home for the JVM (Maven's local repo) AND set HOME so the shell's
> # ~ (used in the cp step below) matches.
> export MAVEN_OPTS="-Duser.home=/tmp"
> export HOME=/tmp
> # standalone Maven (the repo has no mvnw wrapper, and the image has no mvn)
> curl -L https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz | tar xz
> export PATH=/tmp/apache-maven-3.9.16/bin:$PATH
> # XTable source (tarball, so no git needed)
> curl -L https://github.com/apache/incubator-xtable/archive/refs/tags/0.3.0-incubating.tar.gz | tar xz
> cd incubator-xtable-0.3.0-incubating
> # build the utilities module's dependencies, then the utilities module itself:
> mvn -DskipTests install -pl xtable-core,xtable-aws,xtable-hive-metastore -am
> (cd xtable-utilities && mvn -DskipTests package)
> # -> xtable-utilities/target/xtable-utilities_2.12-0.3.0-incubating-bundled.jar (~170 MB)
> ```
> The first run downloads a lot of Maven dependencies, so it takes several minutes.

Still inside the container, copy the bundled jar into `/opt/spark/auxjars` — that path is
the mount of this demo's `spark-jars/` directory, so the jar also appears on your host. The
conversion also needs `jol-core`, which 0.3.0-incubating leaves test-scoped so it is missing
from the bundled jar — copy it from the Maven cache the build just populated:
```
cp xtable-utilities/target/xtable-utilities_2.12-0.3.0-incubating-bundled.jar /opt/spark/auxjars/
cp ~/.m2/repository/org/openjdk/jol/jol-core/0.16/jol-core-0.16.jar /opt/spark/auxjars/
```

Run the conversion from inside the same `spark-hudi` container (you are already there from
the build above; otherwise re-enter with `docker compose exec -it spark-hudi bash`). Because
`jol-core` has to be on the classpath, run the main class with `-cp` rather than `java -jar`.
`xtable-hadoop-config.xml` (shipped in `spark-jars/`) points XTable at MinIO and
sets `fs.s3a.aws.credentials.provider` — XTable's bundled hadoop-aws otherwise
ignores the access keys and fails with a `NoAuthWithAWSException`. The
`-Dlog4j2.configurationFile=...` quiets XTable's very verbose Log4j2 output (also shipped
in `spark-jars/`) so only XTable's own progress and any real errors print:
```
cd /opt/spark/auxjars
java -Dlog4j2.configurationFile=/opt/spark/auxjars/xtable-log4j2.properties \
  -cp xtable-utilities_2.12-0.3.0-incubating-bundled.jar:jol-core-0.16.jar \
  org.apache.xtable.utilities.RunSync \
  --datasetConfig onetable.yaml \
  --hadoopConfig xtable-hadoop-config.xml
```
On success XTable logs `Sync is successful for the following formats ICEBERG,DELTA` once per
table (twice here, for `item` and `user_behavior`) and writes a `_delta_log/` directory and
`metadata/*.metadata.json` files next to the Hudi data in each table's path.

> [!NOTE]
> Check the `Sync is successful` lines, **not** the exit code. `RunSync` catches per-table
> failures, logs `ERROR ... Error running sync for <path>`, and continues — so the process
> can exit `0` even when a table failed. To verify, confirm you got one `Sync is successful`
> line per table and no `Error running sync` lines.

Run spark-sql with Iceberg configs
```
/opt/spark/bin/spark-sql --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.6.1,org.apache.hadoop:hadoop-aws:3.3.4 \
--conf "spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions" \
--conf "spark.sql.catalog.spark_catalog=org.apache.iceberg.spark.SparkSessionCatalog" \
--conf "spark.sql.catalog.spark_catalog.type=hive" \
--conf "spark.sql.catalog.hive_prod=org.apache.iceberg.spark.SparkCatalog" \
--conf "spark.sql.catalog.hive_prod.type=hive"
```

Register the Iceberg files into HMS
```
CREATE SCHEMA iceberg_db LOCATION 's3a://warehouse/';

CALL hive_prod.system.register_table(
   table => 'hive_prod.iceberg_db.user_behavior',
   metadata_file => 's3a://huditest/hudi_ecommerce_user_behavior/metadata/v2.metadata.json'
);

CALL hive_prod.system.register_table(
   table => 'hive_prod.iceberg_db.item',
   metadata_file => 's3a://huditest/hudi_ecommerce_item/metadata/v2.metadata.json'
);
```

Run spark-sql with Delta Lake configs
```
/opt/spark/bin/spark-sql --packages io.delta:delta-spark_2.12:3.2.0,org.apache.hadoop:hadoop-aws:3.3.4 \
--conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
--conf "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog" \
--conf "spark.sql.catalogImplementation=hive"
```

Register the Delta Lake files into HMS
```
CREATE SCHEMA delta_db LOCATION 's3a://warehouse/';

CREATE TABLE delta_db.user_behavior USING DELTA LOCATION 's3a://huditest/hudi_ecommerce_user_behavior';

CREATE TABLE delta_db.item USING DELTA LOCATION 's3a://huditest/hudi_ecommerce_item';
```

6. [Optional] Connect to Iceberg

Add the Iceberg External Catalog
```
CREATE EXTERNAL CATALOG iceberg_catalog_hms
PROPERTIES
(
    "type" = "iceberg",
    "iceberg.catalog.type" = "hive",
    "hive.metastore.uris" = "thrift://hive-metastore:9083",
    "aws.s3.use_instance_profile" = "false",
    "aws.s3.access_key" = "admin",
    "aws.s3.secret_key" = "M!nIOR0cks",
    "aws.s3.region" = "us-east-1",
    "aws.s3.enable_ssl" = "false",
    "aws.s3.enable_path_style_access" = "true",
    "aws.s3.endpoint" = "http://minio:9000"
);
set catalog iceberg_catalog_hms;
show databases;
use iceberg_db;
show tables;
```

7. [Optional] Connect to Delta Lake

Add the Delta Lake External Catalog
```
CREATE EXTERNAL CATALOG deltalake_catalog_hms
PROPERTIES
(
    "type" = "deltalake",
    "hive.metastore.type" = "hive",
    "hive.metastore.uris" = "thrift://hive-metastore:9083",
    "aws.s3.use_instance_profile" = "false",
    "aws.s3.access_key" = "admin",
    "aws.s3.secret_key" = "M!nIOR0cks",
    "aws.s3.region" = "us-east-1",
    "aws.s3.enable_ssl" = "false",
    "aws.s3.enable_path_style_access" = "true",
    "aws.s3.endpoint" = "http://minio:9000"
);
set catalog deltalake_catalog_hms;
show databases;
use delta_db;
show tables;
```

X. Clean up

Do not run this until you're done with the tutorial.
```
drop catalog hudi_catalog_hms;
drop catalog iceberg_catalog_hms
drop catalog deltalake_catalog_hms;
```

# Real-time CDC from PostgreSQL into StarRocks (Debezium + Kafka)

In addition to the data lakehouse services, this `docker-compose.yml` also brings up a
complete Change Data Capture (CDC) stack. It is independent of the Hudi/Iceberg/Delta
flow above and lets you stream row-level changes from PostgreSQL into StarRocks in real
time.

## Pipeline architecture

```
PostgreSQL ──▶ Debezium PostgreSQL source connector ──▶ Kafka topic ──▶ StarRocks sink connector ──▶ StarRocks
 (postgresql)            (runs in `connect`)           (kafka)        (runs in `connect`)        (starrocks-fe/be)
```

| Service      | Image                          | Role                                                        | Host access |
|--------------|--------------------------------|-------------------------------------------------------------|-------------|
| `postgresql` | `quay.io/debezium/postgres:17` | Source database, pre-configured for logical replication/CDC | `localhost:5432` (user/pass `postgres`/`postgres`) |
| `adminer`    | `adminer`                      | Web UI for browsing/editing PostgreSQL                      | http://localhost:8080 |
| `zookeeper`  | `quay.io/debezium/zookeeper:2.6` | Kafka coordination                                        | `localhost:2181` |
| `kafka`      | `quay.io/debezium/kafka:2.6`   | Message broker that carries the change events               | `localhost:9092` (internal `kafka:29092`) |
| `connect`    | `quay.io/debezium/connect:2.6` | Kafka Connect runtime hosting the source and sink connectors | REST API http://localhost:8083 |

## How the connectors get into the `connect` container

The `connect` service loads connector plugins from two directories on its plugin path
(`CONNECT_PLUGIN_PATH=/kafka/connect,/opt/connectors`), both supplied by bind-mounts:

```yaml
volumes:
  - ./kafka-connect-connectors-jar:/opt/connectors
  - ${DEBEZIUM_JDBC_CONNECTOR_PATH:-./kafka-connect-connectors-jar}:/kafka/connect/debezium-connector-jdbc
```

- **Debezium PostgreSQL *source* connector** — already bundled inside the
  `debezium/connect:2.6` image, so nothing to download.
- **StarRocks Kafka *sink* connector** — downloaded by you into
  `./kafka-connect-connectors-jar/` (see
  [Download the StarRocks Kafka sink connector](#download-the-starrocks-kafka-sink-connector-required))
  and mounted at `/opt/connectors`. This is the recommended sink for loading into
  StarRocks. It is deliberately **not** checked into this repo so it stays in sync with the
  [StarRocks Kafka connector releases](https://github.com/StarRocks/starrocks-connector-for-kafka/releases)
  (the `starrocks-connector-for-kafka-<version>-with-dependencies.jar` fat JAR).
- **Debezium JDBC *sink* connector** — *optional* alternative sink, mounted from the
  `DEBEZIUM_JDBC_CONNECTOR_PATH` directory described in
  [Configure the Debezium JDBC connector path](#optional-configure-the-debezium-jdbc-connector-path).
  It is **not** included in this repo — see below for where to get it.

### Where to get the Debezium JDBC connector files

The connector version must match the `debezium/connect:2.6` runtime, so download a
`2.6.x` plugin archive from Maven Central. Extract it, then point
`DEBEZIUM_JDBC_CONNECTOR_PATH` at the extracted directory before `docker compose up`:

```
# Download the 2.6 plugin bundle (matches the debezium/connect:2.6 image)
curl -LO https://repo1.maven.org/maven2/io/debezium/debezium-connector-jdbc/2.6.2.Final/debezium-connector-jdbc-2.6.2.Final-plugin.tar.gz

# Extract it
mkdir -p debezium-connector-jdbc
tar -xzf debezium-connector-jdbc-2.6.2.Final-plugin.tar.gz -C debezium-connector-jdbc --strip-components=1

# Point the env var at the directory containing the JARs
export DEBEZIUM_JDBC_CONNECTOR_PATH="$(pwd)/debezium-connector-jdbc"
```

You can also browse available versions on
[Maven Central](https://central.sonatype.com/artifact/io.debezium/debezium-connector-jdbc)
or follow the
[Debezium JDBC connector docs](https://debezium.io/documentation/reference/stable/connectors/jdbc.html)
and the [Debezium install guide](https://debezium.io/documentation/reference/stable/install.html).

## Registering the connectors

Connectors are registered by POSTing their JSON config to the Kafka Connect REST API on
`localhost:8083`. The configs below are reference templates from the official docs —
adjust database/table/topic names to match your data. The example uses a `customers`
table; the following steps were validated end-to-end against StarRocks 4.1.

First, create a source table in PostgreSQL (the `quay.io/debezium/postgres` image is
already configured with `wal_level=logical`). `REPLICA IDENTITY FULL` makes the `before`
image of updates/deletes available to Debezium:

```
docker compose exec postgresql psql -U postgres -d postgres -c "
  CREATE TABLE customers (id int PRIMARY KEY, name varchar(100), city varchar(100));
  ALTER TABLE customers REPLICA IDENTITY FULL;
  INSERT INTO customers VALUES (1,'Ada','London'),(2,'Linus','Helsinki');"
```

Then create a matching **Primary Key** table in the StarRocks `demo` database (created in
[step 1 of Start the environment](#start-the-environment)):

```
docker compose exec starrocks-fe mysql -h starrocks-fe -P 9030 -u root -e "
  CREATE TABLE demo.customers (
    id INT NOT NULL,
    name VARCHAR(100),
    city VARCHAR(100)
  )
  PRIMARY KEY (id)
  DISTRIBUTED BY HASH(id) BUCKETS 1
  PROPERTIES ('replication_num'='1');"
```

1. **Register the Debezium PostgreSQL source connector** so changes in PostgreSQL are
   published to Kafka topics. (See the
   [Debezium PostgreSQL connector docs](https://debezium.io/documentation/reference/stable/connectors/postgresql.html).)

   ```
   curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
     localhost:8083/connectors/ -d '{
       "name": "postgres-source",
       "config": {
         "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
         "database.hostname": "postgresql",
         "database.port": "5432",
         "database.user": "postgres",
         "database.password": "postgres",
         "database.dbname": "postgres",
         "topic.prefix": "test",
         "table.include.list": "public.customers",
         "plugin.name": "pgoutput"
       }
     }'
   ```

   This produces change events on the topic `test.public.customers`.

2. **Register the StarRocks sink connector** to consume that topic and load into
   StarRocks. For a StarRocks Primary Key table receiving Debezium CDC data, the
   `addfield` and `unwrap` transforms are required so INSERT/UPDATE/DELETE operations are
   applied correctly (see
   [Load data using Kafka connector](https://docs.starrocks.io/docs/loading/Kafka-connector-starrocks/)).

   ```
   curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
     localhost:8083/connectors/ -d '{
       "name": "starrocks-sink",
       "config": {
         "connector.class": "com.starrocks.connector.kafka.StarRocksSinkConnector",
         "topics": "test.public.customers",
         "starrocks.http.url": "starrocks-fe:8030",
         "starrocks.database.name": "demo",
         "starrocks.username": "root",
         "starrocks.password": "",
         "starrocks.topic2table.map": "test.public.customers:customers",
         "sink.properties.format": "json",
         "sink.properties.strip_outer_array": "true",
         "transforms": "addfield,unwrap",
         "transforms.addfield.type": "com.starrocks.connector.kafka.transforms.AddOpFieldForDebeziumRecord",
         "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
         "transforms.unwrap.drop.tombstones": "true",
         "transforms.unwrap.delete.handling.mode": "rewrite"
       }
     }'
   ```

   > The `demo` database is created in
   > [step 1 of Start the environment](#start-the-environment).
   > Create a matching Primary Key table (e.g. `customers`) in StarRocks before starting
   > the sink. Use the StarRocks Kafka connector for loading into StarRocks; the optional
   > Debezium JDBC sink connector is an alternative for writing to JDBC targets in general.
   >
   > The `sink.properties.format=json` and `sink.properties.strip_outer_array=true`
   > settings are required: the connector batches records into a JSON array, and without
   > them StarRocks Stream Load rejects the batch with a `DATA_QUALITY_ERROR` ("The value
   > is array type in json document stream").

3. **Verify the connectors are running:**

   ```
   curl -s localhost:8083/connectors          # list registered connectors
   curl -s localhost:8083/connectors/postgres-source/status
   curl -s localhost:8083/connectors/starrocks-sink/status
   ```

Insert or update a row in PostgreSQL (via `adminer` at http://localhost:8080 or `psql`)
and it will flow through Kafka into the StarRocks `demo.customers` table within seconds.

# About the Scenario

## The dataset
The data comes from a user behavior dataset. This dataset randomly selected 1 million users, recording their actions on taobao from November 25, 2017 to December 3, 2017.

The dataset contains 86,953,525 records with five dimensions: userID, itemID, categoryID, behaviorType, and timestamp. The data spans eight days from November 25, 2017, to December 2, 2017. It involves a total of 987,982 unique users, 3,962,559 unique products, and 9,377 product categories. The dataset includes four types of user behaviors: page view (PV), purchase (Buy), add to cart (Cart), and favorite (Fav).


## Scenario 1: Understanding the ecommerce conversion funnel
```
with tmp1 as (
  with tmp as (
    select 
      t.level as level, 
      count(UserID) as res 
    from 
      (
        select 
          UserID, 
          window_funnel(
            18000, 
            `Timestamp`, 
            0, 
            [BehaviorType = 'pv' , 
            BehaviorType = 'cart', 
            BehaviorType = 'buy' ]
          ) as level 
        from 
          user_behavior 
        where `Timestamp` >= '2017-12-02 00:00:00' 
            and `Timestamp` <= '2017-12-02 23:59:59'
        group by 
          UserID
      ) as t 
    where 
      t.level > 0 
    group by 
      t.level 
  ) 
  select 
    tmp.level, 
    sum(tmp.res) over (
      order by 
        tmp.level rows between current row 
        and unbounded following
    ) as retention 
  from 
    tmp
) 
select 
  tmp1.level, 
  tmp1.retention, 
  last_value(tmp1.retention) over(
    order by 
      tmp1.level rows between current row 
      and 1 following
  )/ tmp1.retention as retention_ratio 
from 
  tmp1;
```
```
+-------+-----------+---------------------+
| level | retention | retention_ratio     |
+-------+-----------+---------------------+
|     1 |    913314 | 0.34725078122091635 |
|     2 |    317149 | 0.23266981765668504 |
|     3 |     73791 |                   1 |
+-------+-----------+---------------------+
3 rows in set (1.85 sec)
```
This indicates that only 34% of users who viewed the product added it to their cart, and only 23% of users who added it to their cart proceeded to place an order. The conversion rate is not great. Let's explore more.

## Scenario 2: Examine the item IDs of the top ten products with the worst conversion rate from PV (page views) to buy.

```
with tmp1 as (
  with tmp as (
    select 
      ItemID, 
      t.level as level, 
      count(UserID) as res 
    from 
      (
        select 
          ItemID, 
          UserID, 
          window_funnel(
            1800, 
            timestamp, 
            0, 
            [BehaviorType = 'pv', 
            BehaviorType ='buy' ]
          ) as level 
        from 
          user_behavior 
        where timestamp >= '2017-12-02 00:00:00' 
            and timestamp <= '2017-12-02 23:59:59'
        group by 
          ItemID, 
          UserID
      ) as t 
    where 
      t.level > 0 
    group by 
      t.ItemID, 
      t.level 
  ) 
  select 
    tmp.ItemID, 
    tmp.level, 
    sum(tmp.res) over (
      partition by tmp.ItemID 
      order by 
        tmp.level rows between current row 
        and unbounded following
    ) as retention 
  from 
    tmp
) 
select 
  tmp1.ItemID, 
  i.name,
  tmp1.level, 
  tmp1.retention / last_value(tmp1.retention) over(
    partition by tmp1.ItemID 
    order by 
      tmp1.level desc rows between current row 
      and 1 following
  ) as retention_ratio 
from 
  tmp1 
JOIN item i ON tmp1.ItemID = i.ItemID
order by 
  tmp1.level desc, 
  retention_ratio 
limit 
  10;
```
```
+---------+--------------+-------+-----------------------+
| ItemID  | name         | level | retention_ratio       |
+---------+--------------+-------+-----------------------+
|   59883 | item 59883   |     2 | 0.0003616636528028933 |
|  394978 | item 394978  |     2 | 0.0006357279084551812 |
| 1164931 | item 1164931 |     2 | 0.0006648936170212766 |
| 4622270 | item 4622270 |     2 | 0.0007692307692307692 |
|  812879 | item 812879  |     2 | 0.0009121313469139556 |
| 1783990 | item 1783990 |     2 | 0.0009132420091324201 |
| 3847054 | item 3847054 |     2 |  0.000925925925925926 |
| 2742138 | item 2742138 |     2 | 0.0009881422924901185 |
|  530918 | item 530918  |     2 | 0.0010193679918450561 |
|  600756 | item 600756  |     2 | 0.0010319917440660474 |
+---------+--------------+-------+-----------------------+
10 rows in set (5.07 sec)
```
At this point, we have identified items with poor conversion, such as item_id=59883.

## Scenario 3: Would like to see the user paths of those who dropped off?

```
select 
  log.BehaviorType, 
  count(log.BehaviorType) 
from 
  (
    select 
      ItemID, 
      UserID, 
      window_funnel(
        1800, 
        timestamp, 
        0, 
        [BehaviorType = 'pv' , 
        BehaviorType = 'buy' ]
      ) as level 
    from 
      user_behavior 
    where timestamp >= '2017-12-02 00:00:00' 
        and timestamp <= '2017-12-02 23:59:59'
    group by 
      ItemID, 
      UserID
  ) as list 
  left join (
    select 
      UserID, 
      array_agg(BehaviorType) as BehaviorType 
    from 
      user_behavior 
    where 
      ItemID = 59883 
      and timestamp >= '2017-12-02 00:00:00' 
      and timestamp <= '2017-12-02 23:59:59' 
    group by 
      UserID
  ) as log on list.UserID = log.UserID 
where 
  list.ItemID = 59883
  and list.level = 1 
group by 
  log.BehaviorType 
order by 
  count(BehaviorType) desc;
```
```
+------------------------------------------------------+-------------------------+
| BehaviorType                                         | count(log.BehaviorType) |
+------------------------------------------------------+-------------------------+
| ["pv"]                                               |                    2704 |
| ["pv","pv"]                                          |                      43 |
| ["pv","pv","pv"]                                     |                       4 |
| ["cart","pv"]                                        |                       3 |
| ["fav","pv"]                                         |                       3 |
| ["pv","pv","pv","pv"]                                |                       2 |
| ["pv","cart"]                                        |                       1 |
| ["pv","cart","pv"]                                   |                       1 |
| ["cart","pv","pv"]                                   |                       1 |
| ["pv","pv","pv","pv","pv"]                           |                       1 |
| ["fav","pv","pv","pv","pv","pv","pv","pv","pv","pv"] |                       1 |
+------------------------------------------------------+-------------------------+
11 rows in set (3.38 sec)
```
We can see that the majority of people just viewed the products and left without taking any further actions.

If you want to understand how StarRocks is executing the queries just put 'EXPLAIN' and 'EXPLAIN ANALYZE' in front of your query. 

```
+---------------------------------------------------------------------------------------------------------------------------------------------------+
| Explain String                                                                                                                                    |
+---------------------------------------------------------------------------------------------------------------------------------------------------+
| PLAN FRAGMENT 0                                                                                                                                   |
|  OUTPUT EXPRS:23: array_agg | 24: count                                                                                                           |
|   PARTITION: UNPARTITIONED                                                                                                                        |
|                                                                                                                                                   |
|   RESULT SINK                                                                                                                                     |
|                                                                                                                                                   |
|   18:MERGING-EXCHANGE                                                                                                                             |
|                                                                                                                                                   |
| PLAN FRAGMENT 1                                                                                                                                   |
|  OUTPUT EXPRS:                                                                                                                                    |
|   PARTITION: HASH_PARTITIONED: 23: array_agg                                                                                                      |
|                                                                                                                                                   |
|   STREAM DATA SINK                                                                                                                                |
|     EXCHANGE ID: 18                                                                                                                               |
|     UNPARTITIONED                                                                                                                                 |
|                                                                                                                                                   |
|   17:SORT                                                                                                                                         |
|   |  order by: <slot 24> 24: count DESC                                                                                                           |
|   |  offset: 0                                                                                                                                    |
|   |                                                                                                                                               |
|   16:AGGREGATE (merge finalize)                                                                                                                   |
|   |  output: count(24: count)                                                                                                                     |
|   |  group by: 23: array_agg                                                                                                                      |
|   |                                                                                                                                               |
|   15:EXCHANGE                                                                                                                                     |
|                                                                                                                                                   |
| PLAN FRAGMENT 2                                                                                                                                   |
|  OUTPUT EXPRS:                                                                                                                                    |
|   PARTITION: HASH_PARTITIONED: 18: UserID                                                                                                         |
|                                                                                                                                                   |
|   STREAM DATA SINK                                                                                                                                |
|     EXCHANGE ID: 15                                                                                                                               |
|     HASH_PARTITIONED: 23: array_agg                                                                                                               |
|                                                                                                                                                   |
|   14:AGGREGATE (update serialize)                                                                                                                 |
|   |  STREAMING                                                                                                                                    |
|   |  output: count(23: array_agg)                                                                                                                 |
|   |  group by: 23: array_agg                                                                                                                      |
|   |                                                                                                                                               |
|   13:Project                                                                                                                                      |
|   |  <slot 23> : 23: array_agg                                                                                                                    |
|   |                                                                                                                                               |
|   12:HASH JOIN                                                                                                                                    |
|   |  join op: RIGHT OUTER JOIN (BUCKET_SHUFFLE(S))                                                                                                |
|   |  colocate: false, reason:                                                                                                                     |
|   |  equal join conjunct: 18: UserID = 6: UserID                                                                                                  |
|   |                                                                                                                                               |
|   |----11:EXCHANGE                                                                                                                                |
|   |                                                                                                                                               |
|   4:AGGREGATE (merge finalize)                                                                                                                    |
|   |  output: array_agg(23: array_agg)                                                                                                             |
|   |  group by: 18: UserID                                                                                                                         |
|   |                                                                                                                                               |
|   3:EXCHANGE                                                                                                                                      |
|                                                                                                                                                   |
| PLAN FRAGMENT 3                                                                                                                                   |
|  OUTPUT EXPRS:                                                                                                                                    |
|   PARTITION: HASH_PARTITIONED: 7: ItemID, 6: UserID                                                                                               |
|                                                                                                                                                   |
|   STREAM DATA SINK                                                                                                                                |
|     EXCHANGE ID: 11                                                                                                                               |
|     HASH_PARTITIONED: 6: UserID                                                                                                                   |
|                                                                                                                                                   |
|   10:Project                                                                                                                                      |
|   |  <slot 6> : 6: UserID                                                                                                                         |
|   |                                                                                                                                               |
|   9:AGGREGATE (merge finalize)                                                                                                                    |
|   |  output: window_funnel(12: window_funnel, 1800, 0)                                                                                            |
|   |  group by: 7: ItemID, 6: UserID                                                                                                               |
|   |  having: 12: window_funnel = 1                                                                                                                |
|   |                                                                                                                                               |
|   8:EXCHANGE                                                                                                                                      |
|                                                                                                                                                   |
| PLAN FRAGMENT 4                                                                                                                                   |
|  OUTPUT EXPRS:                                                                                                                                    |
|   PARTITION: RANDOM                                                                                                                               |
|                                                                                                                                                   |
|   STREAM DATA SINK                                                                                                                                |
|     EXCHANGE ID: 08                                                                                                                               |
|     HASH_PARTITIONED: 7: ItemID, 6: UserID                                                                                                        |
|                                                                                                                                                   |
|   7:AGGREGATE (update serialize)                                                                                                                  |
|   |  STREAMING                                                                                                                                    |
|   |  output: window_funnel(1800, CAST(10: Timestamp AS DATE), 0, 11: expr)                                                                        |
|   |  group by: 7: ItemID, 6: UserID                                                                                                               |
|   |                                                                                                                                               |
|   6:Project                                                                                                                                       |
|   |  <slot 6> : 6: UserID                                                                                                                         |
|   |  <slot 7> : 7: ItemID                                                                                                                         |
|   |  <slot 10> : 10: Timestamp                                                                                                                    |
|   |  <slot 11> : [9: BehaviorType = 'pv',9: BehaviorType = 'buy']                                                                                 |
|   |                                                                                                                                               |
|   5:HudiScanNode                                                                                                                                  |
|      TABLE: user_behavior                                                                                                                         |
|      NON-PARTITION PREDICATES: 7: ItemID = 59883, 10: Timestamp >= '2017-12-02 00:00:00', 10: Timestamp <= '2017-12-02 23:59:59'                  |
|      MIN/MAX PREDICATES: 7: ItemID <= 59883, 7: ItemID >= 59883, 10: Timestamp >= '2017-12-02 00:00:00', 10: Timestamp <= '2017-12-02 23:59:59'   |
|      partitions=1/1                                                                                                                               |
|      cardinality=1120875                                                                                                                          |
|      avgRowSize=5.0                                                                                                                               |
|                                                                                                                                                   |
| PLAN FRAGMENT 5                                                                                                                                   |
|  OUTPUT EXPRS:                                                                                                                                    |
|   PARTITION: RANDOM                                                                                                                               |
|                                                                                                                                                   |
|   STREAM DATA SINK                                                                                                                                |
|     EXCHANGE ID: 03                                                                                                                               |
|     HASH_PARTITIONED: 18: UserID                                                                                                                  |
|                                                                                                                                                   |
|   2:AGGREGATE (update serialize)                                                                                                                  |
|   |  STREAMING                                                                                                                                    |
|   |  output: array_agg(21: BehaviorType)                                                                                                          |
|   |  group by: 18: UserID                                                                                                                         |
|   |                                                                                                                                               |
|   1:Project                                                                                                                                       |
|   |  <slot 18> : 18: UserID                                                                                                                       |
|   |  <slot 21> : 21: BehaviorType                                                                                                                 |
|   |                                                                                                                                               |
|   0:HudiScanNode                                                                                                                                  |
|      TABLE: user_behavior                                                                                                                         |
|      NON-PARTITION PREDICATES: 19: ItemID = 59883, 22: Timestamp >= '2017-12-02 00:00:00', 22: Timestamp <= '2017-12-02 23:59:59'                 |
|      MIN/MAX PREDICATES: 19: ItemID <= 59883, 19: ItemID >= 59883, 22: Timestamp >= '2017-12-02 00:00:00', 22: Timestamp <= '2017-12-02 23:59:59' |
|      partitions=1/1                                                                                                                               |
|      cardinality=1120875                                                                                                                          |
|      avgRowSize=4.0                                                                                                                               |
+---------------------------------------------------------------------------------------------------------------------------------------------------+
124 rows in set (0.05 sec)
```

```
+----------------------------------------------------------------------------------------------------------------------------------------------------------+
| Explain String                                                                                                                                           |
+----------------------------------------------------------------------------------------------------------------------------------------------------------+
| Summary                                                                                                                                          |
|     QueryId: e5557c55-d114-11ee-9426-0242ac140004                                                                                                |
|     Version: 3.2.2-269e832                                                                                                                       |
|     State: Finished                                                                                                                              |
|     TotalTime: 3s163ms                                                                                                                           |
|         ExecutionTime: 3s57ms [Scan: 6s16ms (196.77%), Network: 1.281ms (0.04%), ResultDeliverTime: 0ns (0.00%), ScheduleTime: 17.479ms (0.57%)] |
|         CollectProfileTime: 4ms                                                                                                                  |
|         FrontendProfileMergeTime: 19.627ms                                                                                                       |
|     QueryPeakMemoryUsage: 2.209 GB, QueryAllocatedMemoryUsage: 9.575 GB                                                                          |
|     Top Most Time-consuming Nodes:                                                                                                               |
|         1. HUDI_SCAN (id=0) : 3s23ms (50.16%)                                                                                               |
|         2. HUDI_SCAN (id=5) : 2s998ms (49.73%)                                                                                              |
|         3. AGGREGATION (id=2) [serialize, update]: 1.238ms (0.02%)                                                                               |
|         4. AGGREGATION (id=4) [finalize, merge]: 781.999us (0.01%)                                                                               |
|         5. EXCHANGE (id=8) [SHUFFLE]: 725.751us (0.01%)                                                                                          |
|         6. AGGREGATION (id=7) [serialize, update]: 710.166us (0.01%)                                                                             |
|         7. AGGREGATION (id=9) [finalize, merge]: 599.417us (0.01%)                                                                               |
|         8. MERGE_EXCHANGE (id=18) [GATHER]: 558.957us (0.01%)                                                                                    |
|         9. EXCHANGE (id=3) [SHUFFLE]: 501.456us (0.01%)                                                                                          |
|         10. EXCHANGE (id=15) [SHUFFLE]: 348.460us (0.01%)                                                                                        |
|     Top Most Memory-consuming Nodes:                                                                                                             |
|         1. HUDI_SCAN (id=0) : 623.732 MB                                                                                                         |
|         2. HUDI_SCAN (id=5) : 597.561 MB                                                                                                         |
|         3. AGGREGATION (id=2) [serialize, update]: 675.703 KB                                                                                    |
|         4. AGGREGATION (id=4) [finalize, merge]: 350.250 KB                                                                                      |
|         5. AGGREGATION (id=7) [serialize, update]: 324.297 KB                                                                                    |
|         6. HASH_JOIN (id=12) [BUCKET_SHUFFLE(S), RIGHT OUTER JOIN]: 170.438 KB                                                                   |
|         7. AGGREGATION (id=9) [finalize, merge]: 124.625 KB                                                                                      |
|         8. EXCHANGE (id=8) [SHUFFLE]: 119.219 KB                                                                                                 |
|         9. AGGREGATION (id=14) [serialize, update]: 84.781 KB                                                                                    |
|         10. AGGREGATION (id=16) [finalize, merge]: 83.781 KB                                                                                     |
|     NonDefaultVariables:                                                                                                                         |
|         enable_adaptive_sink_dop: false -> true                                                                                                  |
|         enable_async_profile: true -> false                                                                                                      |
|         enable_profile: false -> true                                                                                                            |
| Fragment 0                                                                                                                                       |
| │   BackendNum: 1                                                                                                                                |
| │   InstancePeakMemoryUsage: 7.008 KB, InstanceAllocatedMemoryUsage: 23.313 KB                                                                   |
| │   PrepareTime: 304.458us                                                                                                                       |
| └──RESULT_SINK                                                                                                                                   |
|    │   TotalTime: 79.292us (0.00%) [CPUTime: 79.292us]                                                                                           |
|    │   OutputRows: 11                                                                                                                            |
|    │   SinkType: MYSQL_PROTOCAL                                                                                                                  |
|    └──MERGE_EXCHANGE (id=18) [GATHER]                                                                                                            |
|           Estimates: [row: 70054, cpu: ?, memory: ?, network: ?, cost: 2.005319379688281E8]                                                      |
|           TotalTime: 558.957us (0.01%) [CPUTime: 247.333us, NetworkTime: 311.624us]                                                              |
|           OutputRows: 11                                                                                                                         |
|           PeakMemory: 5.781 KB, AllocatedMemory: 13.234 KB                                                                                       |
|           SubordinateOperators:                                                                                                                  |
|               LOCAL_EXCHANGE [Passthrough]                                                                                                       |
|                                                                                                                                                      |
| Fragment 1                                                                                                                                       |
| │   BackendNum: 1                                                                                                                                |
| │   InstancePeakMemoryUsage: 1.488 MB, InstanceAllocatedMemoryUsage: 504.000 KB                                                                  |
| │   PrepareTime: 405.042us                                                                                                                       |
| └──DATA_STREAM_SINK (id=18)                                                                                                                      |
|    │   PartitionType: UNPARTITIONED                                                                                                              |
|    └──SORT (id=17) [ROW_NUMBER, SORT]                                                                                                            |
|       │   Estimates: [row: 70054, cpu: ?, memory: ?, network: ?, cost: 1.938066864688281E8]                                                      |
|       │   TotalTime: 283.626us (0.00%) [CPUTime: 283.626us]                                                                                      |
|       │   OutputRows: 11                                                                                                                         |
|       │   PeakMemory: 13.672 KB, AllocatedMemory: 59.805 KB                                                                                      |
|       │   OrderByExprs: [<slot 24> 24: count]                                                                                                    |
|       │   SubordinateOperators:                                                                                                                  |
|       │       LOCAL_EXCHANGE [Passthrough]                                                                                                       |
|       └──AGGREGATION (id=16) [finalize, merge]                                                                                                   |
|          │   Estimates: [row: 70054, cpu: ?, memory: ?, network: ?, cost: 1.870814349688281E8]                                                   |
|          │   TotalTime: 147.251us (0.00%) [CPUTime: 147.251us]                                                                                   |
|          │   OutputRows: 11                                                                                                                      |
|          │   PeakMemory: 83.781 KB, AllocatedMemory: 344.305 KB                                                                                  |
|          │   AggExprs: [count(24: count)]                                                                                                        |
|          │   GroupingExprs: [23: array_agg]                                                                                                      |
|          └──EXCHANGE (id=15) [SHUFFLE]                                                                                                           |
|                 Estimates: [row: 140109, cpu: ?, memory: ?, network: ?, cost: 1.820374963438281E8]                                               |
|                 TotalTime: 348.460us (0.01%) [CPUTime: 129.918us, NetworkTime: 218.542us]                                                        |
|                 OutputRows: 20                                                                                                                   |
|                 PeakMemory: 7.758 KB, AllocatedMemory: 73.141 KB                                                                                 |
|                                                                                                                                                      |
| Fragment 2                                                                                                                                       |
| │   BackendNum: 1                                                                                                                                |
| │   InstancePeakMemoryUsage: 2.620 MB, InstanceAllocatedMemoryUsage: 3.733 MB                                                                    |
| │   PrepareTime: 524.125us                                                                                                                       |
| └──DATA_STREAM_SINK (id=15)                                                                                                                      |
|    │   PartitionType: HASH_PARTITIONED                                                                                                           |
|    │   PartitionExprs: [23: array_agg]                                                                                                           |
|    └──AGGREGATION (id=14) [serialize, update]                                                                                                    |
|       │   Estimates: [row: 140109, cpu: ?, memory: ?, network: ?, cost: 1.803561834688281E8]                                                     |
|       │   TotalTime: 159.334us (0.00%) [CPUTime: 159.334us]                                                                                      |
|       │   OutputRows: 20                                                                                                                         |
|       │   PeakMemory: 84.781 KB, AllocatedMemory: 345.156 KB                                                                                     |
|       │   AggExprs: [count(23: array_agg)]                                                                                                       |
|       │   GroupingExprs: [23: array_agg]                                                                                                         |
|       └──PROJECT (id=13)                                                                                                                         |
|          │   Estimates: [row: ?, cpu: ?, memory: ?, network: ?, cost: ?]                                                                         |
|          │   TotalTime: 2.626us (0.00%) [CPUTime: 2.626us]                                                                                       |
|          │   OutputRows: 2.764K (2764)                                                                                                           |
|          │   Expression: [23: array_agg]                                                                                                         |
|          └──HASH_JOIN (id=12) [BUCKET_SHUFFLE(S), RIGHT OUTER JOIN]                                                                              |
|             │   Estimates: [row: 280218, cpu: ?, memory: ?, network: ?, cost: 1.691474309688281E8]                                               |
|             │   TotalTime: 281.834us (0.00%) [CPUTime: 281.834us]                                                                                |
|             │   OutputRows: 2.764K (2764)                                                                                                        |
|             │   PeakMemory: 170.438 KB, AllocatedMemory: 784.500 KB                                                                              |
|             │   BuildTime: 223.291us                                                                                                             |
|             │   ProbeTime: 57.667us                                                                                                              |
|             │   EqJoinConjuncts: [18: UserID = 6: UserID]                                                                                        |
|             │   SubordinateOperators:                                                                                                            |
|             │       CHUNK_ACCUMULATE                                                                                                             |
|             ├──<PROBE> AGGREGATION (id=4) [finalize, merge]                                                                                      |
|             │  │   Estimates: [row: 280218, cpu: ?, memory: ?, network: ?, cost: 8.0703018E7]                                                    |
|             │  │   TotalTime: 781.999us (0.01%) [CPUTime: 781.999us]                                                                             |
|             │  │   OutputRows: 2.867K (2867)                                                                                                     |
|             │  │   PeakMemory: 350.250 KB, AllocatedMemory: 2.242 MB                                                                             |
|             │  │   AggExprs: [array_agg(23: array_agg)]                                                                                          |
|             │  │   GroupingExprs: [18: UserID]                                                                                                   |
|             │  └──EXCHANGE (id=3) [SHUFFLE]                                                                                                      |
|             │         Estimates: [row: 560437, cpu: ?, memory: ?, network: ?, cost: 6.05272635E7]                                                |
|             │         TotalTime: 501.456us (0.01%) [CPUTime: 269.790us, NetworkTime: 231.666us]                                                  |
|             │         OutputRows: 2.867K (2867)                                                                                                  |
|             │         PeakMemory: 54.789 KB, AllocatedMemory: 263.320 KB                                                                         |
|             │         RuntimeFilter: 2.867K (2867) -> 2.867K (2867) (0.00%)                                                                      |
|             │         SubordinateOperators:                                                                                                      |
|             │             CHUNK_ACCUMULATE                                                                                                       |
|             └──<BUILD> EXCHANGE (id=11) [SHUFFLE]                                                                                                |
|                    Estimates: [row: 154470, cpu: ?, memory: ?, network: ?, cost: 7.797228567218749E7]                                            |
|                    TotalTime: 290.376us (0.00%) [CPUTime: 98.418us, NetworkTime: 191.958us]                                                      |
|                    OutputRows: 2.764K (2764)                                                                                                     |
|                    PeakMemory: 16.281 KB, AllocatedMemory: 112.898 KB                                                                            |
|                                                                                                                                                      |
| Fragment 3                                                                                                                                       |
| │   BackendNum: 1                                                                                                                                |
| │   InstancePeakMemoryUsage: 2.226 MB, InstanceAllocatedMemoryUsage: 1.159 MB                                                                    |
| │   PrepareTime: 250us                                                                                                                           |
| └──DATA_STREAM_SINK (id=11)                                                                                                                      |
|    │   PartitionType: HASH_PARTITIONED                                                                                                           |
|    │   PartitionExprs: [6: UserID]                                                                                                               |
|    └──PROJECT (id=10)                                                                                                                            |
|       │   Estimates: [row: ?, cpu: ?, memory: ?, network: ?, cost: ?]                                                                            |
|       │   TotalTime: 9.041us (0.00%) [CPUTime: 9.041us]                                                                                          |
|       │   OutputRows: 2.764K (2764)                                                                                                              |
|       │   Expression: [6: UserID]                                                                                                                |
|       └──AGGREGATION (id=9) [finalize, merge]                                                                                                    |
|          │   Estimates: [row: 154470, cpu: ?, memory: ?, network: ?, cost: 7.7354403190625E7]                                                    |
|          │   TotalTime: 599.417us (0.01%) [CPUTime: 599.417us]                                                                                   |
|          │   OutputRows: 2.764K (2764)                                                                                                           |
|          │   PeakMemory: 124.625 KB, AllocatedMemory: 806.922 KB                                                                                 |
|          │   AggExprs: [window_funnel(12: window_funnel, 1800, 0)]                                                                               |
|          │   GroupingExprs: [7: ItemID, 6: UserID]                                                                                               |
|          │   SubordinateOperators:                                                                                                               |
|          │       CHUNK_ACCUMULATE                                                                                                                |
|          └──EXCHANGE (id=8) [SHUFFLE]                                                                                                            |
|                 Estimates: [row: 588459, cpu: ?, memory: ?, network: ?, cost: 6.52909833125E7]                                                   |
|                 TotalTime: 725.751us (0.01%) [CPUTime: 398.168us, NetworkTime: 327.583us]                                                        |
|                 OutputRows: 2.867K (2867)                                                                                                        |
|                 PeakMemory: 119.219 KB, AllocatedMemory: 699.727 KB                                                                              |
|                                                                                                                                                      |
| Fragment 4                                                                                                                                       |
| │   BackendNum: 1                                                                                                                                |
| │   InstancePeakMemoryUsage: 1.127 GB, InstanceAllocatedMemoryUsage: 4.785 GB                                                                    |
| │   PrepareTime: 473.708us                                                                                                                       |
| └──DATA_STREAM_SINK (id=8)                                                                                                                       |
|    │   PartitionType: HASH_PARTITIONED                                                                                                           |
|    │   PartitionExprs: [7: ItemID, 6: UserID]                                                                                                    |
|    └──AGGREGATION (id=7) [serialize, update]                                                                                                     |
|       │   Estimates: [row: 588459, cpu: ?, memory: ?, network: ?, cost: 5.940638825E7]                                                           |
|       │   TotalTime: 710.166us (0.01%) [CPUTime: 710.166us]                                                                                      |
|       │   OutputRows: 2.867K (2867)                                                                                                              |
|       │   PeakMemory: 324.297 KB, AllocatedMemory: 1.231 MB                                                                                      |
|       │   AggExprs: [window_funnel(1800, CAST(10: Timestamp AS DATE), 0, 11: expr)]                                                              |
|       │   GroupingExprs: [7: ItemID, 6: UserID]                                                                                                  |
|       │   SubordinateOperators:                                                                                                                  |
|       │       LOCAL_EXCHANGE [Passthrough]                                                                                                       |
|       └──PROJECT (id=6)                                                                                                                          |
|          │   Estimates: [row: ?, cpu: ?, memory: ?, network: ?, cost: ?]                                                                         |
|          │   TotalTime: 122.334us (0.00%) [CPUTime: 122.334us]                                                                                   |
|          │   OutputRows: 2.949K (2949)                                                                                                           |
|          │   Expression: [6: UserID, 7: ItemID, 10: Timestamp, ...]                                                                              |
|          └──HUDI_SCAN (id=5)                                                                                                                |
|                 Estimates: [row: 1120875, cpu: ?, memory: ?, network: ?, cost: 0.0]                                                         |
|                 TotalTime: 2s998ms (49.73%) [CPUTime: 2.953ms, ScanTime: 2s995ms]                                                           |
|                 OutputRows: 2.949K (2949)                                                                                                   |
|                 PeakMemory: 597.561 MB, AllocatedMemory: 4.783 GB                                                                           |
|                 SubordinateOperators:                                                                                                       |
|                     CHUNK_ACCUMULATE                                                                                                        |
|                 Detail Timers: [ScanTime = IOTaskExecTime + IOTaskWaitTime]                                                                 |
|                     IOTaskExecTime: 2s383ms [min=1s610ms, max=2s994ms]                                                                      |
|                         InputStream:                                                                                                        |
|                             AppIOTime: 742.187ms [min=513.430ms, max=954.801ms]                                                             |
|                             FSIOTime: 742.109ms [min=513.352ms, max=954.749ms]                                                              |
|                         SharedBuffered:                                                                                                     |
|                             SharedIOTime: 719.744ms [min=488.862ms, max=907.380ms]                                                          |
|                     IOTaskWaitTime: 2.589ms [min=131.083us, max=6.045ms]                                                                    |
|                                                                                                                                                      |
| Fragment 5                                                                                                                                       |
| │   BackendNum: 1                                                                                                                                |
| │   InstancePeakMemoryUsage: 1.094 GB, InstanceAllocatedMemoryUsage: 4.785 GB                                                                    |
| │   PrepareTime: 759.125us                                                                                                                       |
| └──DATA_STREAM_SINK (id=3)                                                                                                                       |
|    │   PartitionType: HASH_PARTITIONED                                                                                                           |
|    │   PartitionExprs: [18: UserID]                                                                                                              |
|    └──AGGREGATION (id=2) [serialize, update]                                                                                                     |
|       │   Estimates: [row: 560437, cpu: ?, memory: ?, network: ?, cost: 5.3802012E7]                                                             |
|       │   TotalTime: 1.238ms (0.02%) [CPUTime: 1.238ms]                                                                                          |
|       │   OutputRows: 2.867K (2867)                                                                                                              |
|       │   PeakMemory: 675.703 KB, AllocatedMemory: 1.418 MB                                                                                      |
|       │   AggExprs: [array_agg(21: BehaviorType)]                                                                                                |
|       │   GroupingExprs: [18: UserID]                                                                                                            |
|       │   SubordinateOperators:                                                                                                                  |
|       │       LOCAL_EXCHANGE [Passthrough]                                                                                                       |
|       └──PROJECT (id=1)                                                                                                                          |
|          │   Estimates: [row: ?, cpu: ?, memory: ?, network: ?, cost: ?]                                                                         |
|          │   TotalTime: 6.208us (0.00%) [CPUTime: 6.208us]                                                                                       |
|          │   OutputRows: 2.949K (2949)                                                                                                           |
|          │   Expression: [18: UserID, 21: BehaviorType]                                                                                          |
|          └──HUDI_SCAN (id=0)                                                                                                                |
|                 Estimates: [row: 1120875, cpu: ?, memory: ?, network: ?, cost: 0.0]                                                         |
|                 TotalTime: 3s23ms (50.16%) [CPUTime: 2.157ms, ScanTime: 3s21ms]                                                             |
|                 OutputRows: 2.949K (2949)                                                                                                   |
|                 PeakMemory: 623.732 MB, AllocatedMemory: 4.783 GB                                                                           |
|                 RuntimeFilter: 2.949K (2949) -> 2.949K (2949) (0.00%)                                                                       |
|                 SubordinateOperators:                                                                                                       |
|                     CHUNK_ACCUMULATE                                                                                                        |
|                 Detail Timers: [ScanTime = IOTaskExecTime + IOTaskWaitTime]                                                                 |
|                     IOTaskExecTime: 2s625ms [min=979.384ms, max=3s16ms]                                                                     |
|                         InputStream:                                                                                                        |
|                             AppIOTime: 816.591ms [min=407.213ms, max=1s7ms]                                                                 |
|                             FSIOTime: 814.997ms [min=407.070ms, max=1s7ms]                                                                  |
|                         SharedBuffered:                                                                                                     |
|                             SharedIOTime: 787.532ms [min=367.469ms, max=976.535ms]                                                          |
|                     IOTaskWaitTime: 2.775ms [min=244.250us, max=10.778ms]                                                                   |
|                                                                                                                                                      |
+----------------------------------------------------------------------------------------------------------------------------------------------------------+
230 rows in set (3.21 sec)
```
