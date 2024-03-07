### Demo of StarRocks using Hudi External Catalog on MinIO + HMS

![StarRocks Technical Overview](https://github.com/StarRocks/demo/assets/749093/b3262af7-ab7b-4c0a-b69c-1aa89cf1d30a)
<img width="564" alt="Screenshot 2024-03-06 at 9 51 16 PM" src="https://github.com/StarRocks/demo/assets/749093/a6ce8f3c-bd1f-4070-af48-3613a2181933">


> [!NOTE]  
>  We have more a more complex example/tutorial at https://github.com/StarRocks/demo/tree/master/documentation-samples/datalakehouse that shows you writing Hudi and then using Onetable.dev to convert Hudi into Apache Iceberg and Delta Lake and then querying the all 3 open table format types in StarRocks.

> [!IMPORTANT]  
>  Ensure that "Use Rosetta for x86/amd64 emulation on Apple Silicon" is enabled on your Docker Desktop.  You can find this setting in Setting -> General. 

1. Start the environment

`docker compose up --detach --wait --wait-timeout 60`

2. Create the bucket for Apache Hudi files

Go to http://localhost:9000/ and login with admin:password and create the bucket `huditest`

3. Run the Spark Scala code to insert data

Log into the spark-hudi container.   Run `/spark-3.2.1-bin-hadoop3.2/bin/spark-shell`.  Please note that there are spark defaults already set via conf files and run the following to set additional spark configs. Execute

```
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.Row
import org.apache.spark.sql.SaveMode._
import org.apache.hudi.DataSourceReadOptions._
import org.apache.hudi.DataSourceWriteOptions._
import org.apache.hudi.config.HoodieWriteConfig._
import scala.collection.JavaConversions._

val schema = StructType( Array(
                 StructField("language", StringType, true),
                 StructField("users", StringType, true),
                 StructField("id", StringType, true)
             ))

val rowData= Seq(Row("Java", "20000", "a"),
               Row("Python", "100000", "b"),
               Row("Scala", "3000", "c"))


val df = spark.createDataFrame(rowData,schema)

val databaseName = "hudi_sample"
val tableName = "hudi_coders_hive"
val basePath = "s3a://huditest/hudi_coders"

df.write.format("hudi").
  option(org.apache.hudi.config.HoodieWriteConfig.TABLE_NAME, tableName).
  option(RECORDKEY_FIELD_OPT_KEY, "id").
  option(PARTITIONPATH_FIELD_OPT_KEY, "language").
  option(PRECOMBINE_FIELD_OPT_KEY, "users").
  option("hoodie.datasource.write.hive_style_partitioning", "true").
  option("hoodie.datasource.hive_sync.enable", "true").
  option("hoodie.datasource.hive_sync.mode", "hms").
  option("hoodie.datasource.hive_sync.database", databaseName).
  option("hoodie.datasource.hive_sync.table", tableName).
  option("hoodie.datasource.hive_sync.partition_fields", "language").
  option("hoodie.datasource.hive_sync.partition_extractor_class", "org.apache.hudi.hive.MultiPartKeysValueExtractor").
  option("hoodie.datasource.hive_sync.metastore.uris", "thrift://hive-metastore:9083").
  mode(Overwrite).
  save(basePath)
System.exit(0)
```

4. Have StarRocks connect to Hudi on S3

```
mysql -P 9030 -h 127.0.0.1 -u root --prompt="StarRocks > "
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
    "aws.s3.secret_key" = "password",
    "aws.s3.region" = "us-east-1",
    "aws.s3.enable_ssl" = "false",
    "aws.s3.enable_path_style_access" = "true",
    "aws.s3.endpoint" = "http://minio:9000"
);


set catalog hudi_catalog_hms;
show databases;
use hudi_sample;
show tables;
select * from hudi_coders_hive;
```

Output
```
StarRocks > show tables;
+-----------------------+
| Tables_in_hudi_sample |
+-----------------------+
| hudi_coders_hive      |
+-----------------------+
1 row in set (0.00 sec)

StarRocks > select * from hudi_coders_hive;
+---------------------+-----------------------+--------------------+------------------------+----------------------------------------------------------------------------+----------+--------+------+
| _hoodie_commit_time | _hoodie_commit_seqno  | _hoodie_record_key | _hoodie_partition_path | _hoodie_file_name                                                          | language | users  | id   |
+---------------------+-----------------------+--------------------+------------------------+----------------------------------------------------------------------------+----------+--------+------+
| 20240203023423658   | 20240203023423658_1_0 | b                  | language=Python        | f97a9753-c031-4a26-8946-96e54ac13046-0_1-27-1222_20240203023423658.parquet | Python   | 100000 | b    |
| 20240203023423658   | 20240203023423658_0_0 | c                  | language=Scala         | fdfdd5c5-f216-4394-b40d-ba6e133e34ad-0_0-27-1221_20240203023423658.parquet | Scala    | 3000   | c    |
| 20240203023423658   | 20240203023423658_2_0 | a                  | language=Java          | 5e277692-c6e7-45c8-930d-c426d477533d-0_2-27-1223_20240203023423658.parquet | Java     | 20000  | a    |
+---------------------+-----------------------+--------------------+------------------------+----------------------------------------------------------------------------+----------+--------+------+
3 rows in set (5.31 sec)
```
