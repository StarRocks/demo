# Retail eCommerce Funnel Analysis Demo with 1 million members and 87 million record dataset using StarRocks | Demo of StarRocks using Hudi, Iceberg, Delta Lake External Catalog on MinIO + HMS + OneTable.dev

![StarRocks Technical Overview](https://github.com/StarRocks/demo/assets/749093/cf5aa363-2b31-4e3b-b9cd-fe94020d3071)

![Screenshot 2024-03-01 at 1 05 20 PM](https://github.com/StarRocks/demo/assets/749093/e810ae65-59b9-4edd-a8dd-87fe2edd4124)

> [!IMPORTANT]  
>  Ensure that "Use Rosetta for x86/amd64 emulation on Apple Silicon" is enabled on your Docker Desktop.  You can find this setting in Setting -> General. 

> [!IMPORTANT]  
>  Set the memory in Docker Desktop. Setting -> Resources -> Memory Limit should we set at least 16GB.

## Environment Setup

1. Start the environment

`docker compose up --detach --wait --wait-timeout 60`

2. Create the bucket for Apache Hudi files and upload files

Go to http://localhost:9000/ and login with admin:password and create the bucket `huditest`.

Upload the 2 parquet files to the bucket `warehouse`.  
* https://cdn.starrocks.io/dataset/user_behavior_sample_data.parquet
* https://cdn.starrocks.io/dataset/item_sample_data.parquet

3. Run the Spark Scala code to insert data

Log into the spark-hudi container.   

Run `bash` to remove the older Hudi 0.11 library and use the Hudi 0.14.1.  Please note that there are spark defaults already set via conf files and run the following to set additional spark configs.
```
rm -f /spark-3.2.1-bin-hadoop3.2/jars/hudi-spark3-bundle_2.12-0.11.1.jar
export SPARK_VERSION=3.2
spark-shell --packages org.apache.hudi:hudi-spark$SPARK_VERSION-bundle_2.12:0.14.1 --driver-memory 16G
```

Run `/spark-3.2.1-bin-hadoop3.2/bin/spark-shell`.  Execute to load in 2 tables.

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
  option(TABLE_NAME, tableName).
  option("hoodie.datasource.write.operation", "bulk_insert").
  option("hoodie.datasource.hive_sync.enable", "true").
  option("hoodie.datasource.hive_sync.mode", "hms").
  option("hoodie.datasource.hive_sync.database", databaseName).
  option("hoodie.datasource.hive_sync.table", tableName).
  option("hoodie.datasource.hive_sync.metastore.uris", "thrift://hive-metastore:9083").
  mode(Overwrite).
  save(basePath)
```

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
  option(TABLE_NAME, tableName).
  option("hoodie.datasource.write.operation", "bulk_insert").
  option("hoodie.datasource.hive_sync.enable", "true").
  option("hoodie.datasource.hive_sync.mode", "hms").
  option("hoodie.datasource.hive_sync.database", databaseName).
  option("hoodie.datasource.hive_sync.table", tableName).
  option("hoodie.datasource.hive_sync.metastore.uris", "thrift://hive-metastore:9083").
  mode(Overwrite).
  save(basePath)
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
use hudi_ecommerce;
show tables;
select count(*) from user_behavior;
select count(*) from item;
```
Validate the number of entries in user_behavior and item tables.

5. [Optional] Use Onetable.dev to generate Iceberg and Delta Lake metadata

Follow the instruction at https://onetable.dev/docs/setup/.   After running the maven `mvn install -DskipTests` command, it'll generate the utilities-0.1.0-SNAPSHOT-bundled.jar in `onetable/utilities/target/`.   Copy that jar file into the spark container.  To help with the copy, I've already mapped jars to <spark_container>/spark-3.2.1-bin-hadoop3.2/auxjars in the docker-compose yml. 

 > [!IMPORTANT]  
>  You have to compile the onetable code right now to get the 600+ meg utilities-0.1.0-SNAPSHOT-bundled.jar file.   They're working on making it smaller but right now, there is no other option.

Run the onetable utility to generate the open table format metadata.  Details are in the onetable.yaml.
```
cd /spark-3.2.1-bin-hadoop3.2/auxjars
java -jar utilities-0.1.0-SNAPSHOT-bundled.jar --datasetConfig onetable.yaml -p ../conf/core-site.xml
```

Run spark-sql with Iceberg configs
```
yum install -y python3
spark-sql --packages org.apache.iceberg:iceberg-spark-runtime-3.2_2.12:1.2.1 \
--conf "spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions" \
--conf "spark.sql.catalog.spark_catalog=org.apache.iceberg.spark.SparkSessionCatalog" \
--conf "spark.sql.catalog.spark_catalog.type=hive" \
--conf "spark.sql.catalog.hive_prod=org.apache.iceberg.spark.SparkCatalog" \
--conf "spark.sql.catalog.hive_prod.type=hive"
```

Register the Iceberg files into HMS
```
CREATE SCHEMA iceberg_db LOCATION 's3://warehouse/';

CALL hive_prod.system.register_table(
   table => 'hive_prod.iceberg_db.user_behavior',
   metadata_file => 's3://huditest/hudi_ecommerce_user_behavior/metadata/v2.metadata.json'
);

CALL hive_prod.system.register_table(
   table => 'hive_prod.iceberg_db.item',
   metadata_file => 's3://huditest/hudi_ecommerce_item/metadata/v2.metadata.json'
);
```

Run spark-sql with Delta Lake configs
```
yum install -y python3
spark-sql --packages io.delta:delta-core_2.12:2.0.0 \
--conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
--conf "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog" \
--conf "spark.sql.catalogImplementation=hive"
```

Register the Delta Lake files into HMS
```
CREATE SCHEMA delta_db LOCATION 's3://warehouse/';

CREATE TABLE delta_db.user_behavior USING DELTA LOCATION 's3://huditest/hudi_ecommerce_user_behavior';

CREATE TABLE delta_db.item USING DELTA LOCATION 's3://huditest/hudi_ecommerce_item';
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
    "aws.s3.secret_key" = "password",
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

X. Clean up

Do not run this until you're done with the tutorial.
```
drop catalog hudi_catalog_hms;
drop catalog iceberg_catalog_hms
drop catalog deltalake_catalog_hms;
```

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
