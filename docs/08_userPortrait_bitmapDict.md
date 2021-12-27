# 08_userPortrait_bitmapDict

> Building Bitmap data in StarRocks uses Roaring Bitmap, which needs INTEGER as INPUT.
> StarRocks provides a function named bitmap_dict, that can be called in spark-load job, 
> to map String values to Integer values when loading data to StarRocks. 
> 
> Note:
> When calling function bitmap_dict, some intermediate temp hive tables will be generated automatically.
> Submitting this job, needs write-privilege for hive tables.

# Configurations

- Configure soft links pointing to hadoop xml files

- Bouncing your fe and be services to enable these configs

```bash
[root@master1 conf]# pwd
/data/app/StarRocks-1.18.3/be/conf
[root@master1 conf]# ll
-rw-r--r-- 1 1007 1007 2187 9  27 14:33 be.conf
-rw-r--r-- 1 1007 1007  952 9  26 10:15 hadoop_env.sh
lrwxrwxrwx 1 root root   48 9  27 14:28 hdfs-site.xml -> /usr/local/hadoop-2.7.7/etc/hadoop/hdfs-site.xml
lrwxrwxrwx 1 root root   51 9  27 16:10 hive-site.xml -> /usr/local/apache-hive-3.1.1-bin/conf/hive-site.xml
lrwxrwxrwx 1 root root   48 9  27 14:28 yarn-site.xml -> /usr/local/hadoop-2.7.7/etc/hadoop/yarn-site.xml
[root@master1 conf]# ll ../../fe/conf/
-rw-rw-r-- 1 1007 1007 2530 9  27 14:21 fe.conf
-rw-rw-r-- 1 1007 1007  952 9  26 10:15 hadoop_env.sh
lrwxrwxrwx 1 root root   48 9  27 14:28 hdfs-site.xml -> /usr/local/hadoop-2.7.7/etc/hadoop/hdfs-site.xml
lrwxrwxrwx 1 root root   51 9  27 16:09 hive-site.xml -> /usr/local/apache-hive-3.1.1-bin/conf/hive-site.xml
lrwxrwxrwx 1 root root   48 9  27 14:28 yarn-site.xml -> /usr/local/hadoop-2.7.7/etc/hadoop/yarn-site.xml
```


# Data preparation

## Hive CLI

```SQL
-- Hive DDL
CREATE EXTERNAL TABLE `hive_dict_t1`(
    `k1` string,
    `uuid` string
    )
ROW FORMAT SERDE
 'org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe'
STORED AS INPUTFORMAT
 'org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat'
OUTPUTFORMAT
 'org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat'
TBLPROPERTIES ( 'parquet.compression'='snappy');

insert into hive_dict_t1 values ('k1','u1'),('k2','u2'),('k3','u3'),('k4','u4'),('k5','u5');
```


# StarRocks Directives

## StarRocks DDL

```
-- Hive resource
CREATE EXTERNAL RESOURCE "hive0"
PROPERTIES (
    "type" = "hive", 
    "hive.metastore.uris" = "thrift://master1:9083"
);

-- StarRocks internal table
USE starrocks_demo;
CREATE TABLE `starrocks_demo`.`dict_t1` (
    `k1`  varchar(50) NULL  COMMENT "",
    `uuid`  bitmap  bitmap_union    NULL  COMMENT ""
) ENGINE=OLAP
AGGREGATE KEY(`k1` )
COMMENT "OLAP"
DISTRIBUTED BY HASH(`k1` ) BUCKETS 3
PROPERTIES (
    "replication_num" = "1",
    "in_memory" = "false",
    "storage_format" = "DEFAULT"
);

-- StarRocks External Hive table
CREATE EXTERNAL TABLE hive_dict_t1
(
    k1 string,
    uuid string
)
ENGINE=hive
properties 
(
    "resource" = "hive0",
    "database" = "default",
    "table" = "hive_dict_t1"
);
```

## Spark resource

```
-- add broker1
MySQL [(none)]> ALTER SYSTEM ADD BROKER broker1 "master1:8000";
Query OK, 0 rows affected (0.04 sec)

-- yarn HA cluster mode
CREATE EXTERNAL RESOURCE "spark1"
PROPERTIES
(
    "type" = "spark",
    "spark.master" = "yarn",
    "spark.submit.deployMode" = "cluster",
    "spark.hadoop.yarn.resourcemanager.ha.enabled" = "true",
    "spark.hadoop.yarn.resourcemanager.ha.rm-ids" = "rm1,rm2",
    "spark.hadoop.yarn.resourcemanager.hostname.rm1" = "master1",
    "spark.hadoop.yarn.resourcemanager.hostname.rm2" = "worker1",
    "spark.hadoop.fs.defaultFS" = "hdfs://mycluster/",
    "working_dir" = "hdfs://mycluster/tmp/starrocks",
    "broker" = "broker1"
);
```

## Submit spark  job

```
USE starrocks_demo;
LOAD LABEL starrocks_demo.dict_t1
(
    DATA FROM TABLE hive_dict_t1
    INTO TABLE dict_t1
    SET
    (
       uuid=bitmap_dict(uuid)
    )
)
WITH RESOURCE 'spark1'
(
    "spark.executor.memory" = "2g",
    "spark.shuffle.compress" = "true",
    "spark.driver.memory" = "1g"
)
PROPERTIES
(
    "timeout" = "3600",
    "max_filter_ratio" = "0.2"
);
```

## Show load

```
MySQL [starrocks_demo]> show load\G
*************************** 1. row ***************************
         JobId: 13003
         Label: dict_t1
         State: ETL
      Progress: ETL:10%; LOAD:0%
          Type: SPARK
       EtlInfo: NULL
      TaskInfo: cluster:spark1; timeout(s):3600; max_filter_ratio:0.2
      ErrorMsg: NULL
    CreateTime: 2021-09-27 16:22:01
  EtlStartTime: 2021-09-27 16:22:14
 EtlFinishTime: NULL
 LoadStartTime: NULL
LoadFinishTime: NULL
           URL: http://worker1:20888/proxy/application_1632723836409_0007/
    JobDetails: {"Unfinished backends":{},"ScannedRows":0,"TaskNumber":0,"All backends":{},"FileNumber":0,"FileSize":0}
1 rows in set (0.01 sec)

MySQL [starrocks_demo]> show load\G
*************************** 1. row ***************************
         JobId: 13003
         Label: dict_t1
         State: FINISHED
      Progress: ETL:100%; LOAD:100%
          Type: SPARK
       EtlInfo: unselected.rows=0; dpp.abnorm.ALL=0; dpp.norm.ALL=5
      TaskInfo: cluster:spark1; timeout(s):3600; max_filter_ratio:0.2
      ErrorMsg: NULL
    CreateTime: 2021-09-27 16:22:01
  EtlStartTime: 2021-09-27 16:22:14
 EtlFinishTime: 2021-09-27 16:23:41
 LoadStartTime: 2021-09-27 16:23:41
LoadFinishTime: 2021-09-27 16:23:42
           URL: http://worker1:20888/proxy/application_1632723836409_0007/
    JobDetails: {"Unfinished backends":{"00000000-0000-0000-0000-000000000000":[]},"ScannedRows":5,"TaskNumber":1,"All backends":{"00000000-0000-0000-0000-000000000000":[-1]},"FileNumber":0,"FileSize":0}
1 rows in set (0.01 sec)
```


## Verification

- Notes that uuid values  u1, u2, u3 ... were mapped to global unique integer values 1,2,3...

```
MySQL [starrocks_demo]> desc dict_t1;
+-------+-------------+------+-------+---------+--------------+
| Field | Type        | Null | Key   | Default | Extra        |
+-------+-------------+------+-------+---------+--------------+
| k1    | VARCHAR(50) | Yes  | true  | NULL    |              |
| uuid  | BITMAP      | Yes  | false |         | BITMAP_UNION |
+-------+-------------+------+-------+---------+--------------+
2 rows in set (0.02 sec)

MySQL [starrocks_demo]> select k1, bitmap_to_string(uuid) from dict_t1;
+------+--------------------------+
| k1   | bitmap_to_string(`uuid`) |
+------+--------------------------+
| k2   | 2                        |
| k3   | 3                        |
| k4   | 4                        |
| k1   | 1                        |
| k5   | 5                        |
+------+--------------------------+
5 rows in set (0.02 sec)
```

# FYI 

hive temp tables with below keywords will be generated automatically;

- the one with `_global_dict_table_` keyword holds uuid globally which shouldn't be dropped;

- other ones are intermediate, with `_distinct_key_table_` and `_intermediate_hive_table_`, can be cleaned manually 


## License

StarRocks/demo is under the Apache 2.0 license. See the [LICENSE](../LICENSE) file for details.