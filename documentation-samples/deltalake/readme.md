### Demo of StarRocks using Delta Lake External Catalog on MinIO + HMS

![StarRocks Technical Overview](https://github.com/StarRocks/demo/assets/749093/aec4ee69-8b1c-49f4-814d-b90ad4a06f70)
<img width="698" alt="Screenshot 2024-03-06 at 9 50 56 PM" src="https://github.com/StarRocks/demo/assets/749093/885f7258-cf60-4a3c-afc4-52f747eaab4f">


> [!NOTE]  
>  We have more a more complex example/tutorial at https://github.com/StarRocks/demo/tree/master/documentation-samples/datalakehouse that shows you writing Hudi and then using Onetable.dev to convert Hudi into Apache Iceberg and Delta Lake and then querying the all 3 open table format types in StarRocks.

1. Start the environment

`docker compose up --detach --wait --wait-timeout 60`

2. (Optional) Browse the object store

The `warehouse` bucket that Delta Lake writes to is created automatically by the
`mc` init container when the stack starts, so there is nothing to do here. To
browse it, open the MinIO console at http://localhost:9000/ and log in with
`admin` / `password`.

3. Run the Spark SQL code to insert data

Open a `spark-sql` session in the `spark` container. The Hive metastore URI and
the S3A/MinIO credentials are already set via `conf/spark-defaults.conf`; the
Delta Lake and `hadoop-aws` packages (the latter provides the `s3a://`
filesystem, which the official `apache/spark` image does not bundle) are pulled
from Maven Central on first launch, which takes a minute or two:

```
docker compose exec -it spark /opt/spark/bin/spark-sql \
--packages io.delta:delta-spark_2.12:3.2.0,org.apache.hadoop:hadoop-aws:3.3.4 \
--conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
--conf "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog" \
--conf "spark.sql.catalogImplementation=hive"
```

```
CREATE SCHEMA delta_db LOCATION 's3://warehouse/';

create table delta_db.albert (number Int, word String) using delta location 's3://warehouse/dl_albert';

insert into delta_db.albert values (1, "albert");
```

4. Have StarRocks connect to Delta Lake on S3

```
mysql -P 9030 -h 127.0.0.1 -u root --prompt="StarRocks > "
```
```
CREATE EXTERNAL CATALOG deltalake_catalog_hms
PROPERTIES
(
    "type" = "deltalake",
    "hive.metastore.type" = "hive",
    "hive.metastore.uris" = "thrift://hive-metastore:9083",
    "aws.s3.use_instance_profile" = "false",
    "aws.s3.access_key" = "admin",
    "aws.s3.secret_key" = "password",
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
