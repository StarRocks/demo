# 03_sparkLoad2DorisDB

# 场景描述
演示使用sparkLoad导入数据到DorisDB

# 基础环境准备

## Hadoop环境

在Linux环境配置好Hadoop 集群(YARN, HDFS, SPARK)等环境，

具体版本视实际情况，这里测试使用的版本：
- hadoop 2.7.7
- spark 2.3.3

### 关于JAVA_HOME环境变量

> 可能需要在hadoop/libexec/hadoop-config.sh的行首添加:

```
export JAVA_HOME=xxxxJAVA_HOME路径
```

## DorisDB集群

### fe配置

> 在fe.conf中增加以下配置( 默认是false)

```
enable_spark_load=true
spark_home_default_dir=/usr/local/spark-2.3.3-bin-hadoop2.7/
yarn_client_path=/usr/local/hadoop-2.7.7/bin/yarn
```

### 准备spark-2x.zip文件

```
[root@master1 ~ ]# cd /usr/local/spark-2.3.3-bin-hadoop2.7/jars
[root@master1 jars]# zip -r spark-2x.zip ./*.jar
```

# Case1

导入hdfs上的CSV文件到DorisDB

## 模拟数据
利用[SparkDemo](../SparkDemo)模块的[gen_wide.py](../SparkDemo/src/main/py/gen_wide.py)数据生成脚本，

生成1万行2列的csv文件，并上传到hdfs：

```
[root@master1 data]# python data_wide.py 10000 2 > demo3_data1.csv
[root@master1 data]# grep ^[0-9] demo3_data1.csv | wc -l
10000
[root@master1 data]# head demo3_data1.csv
1        10
9        5
8        8
8        3
9        11
8        4
5        12
2        7
3        3
6        5
[root@master1 ~]# hadoop fs -mkdir -p  /dorisDB-demo/data
[root@master1 data]# hadoop fs -put demo3_data1.csv /dorisDB-demo/data/

```

## 功能测试

DorisDB DDL

```
CREATE TABLE `dorisdb_demo`.`demo3_spark_tb1` (
    `k1`  varchar(50) NULL  COMMENT "",
    `v1`  String      NULL  COMMENT ""
) ENGINE=OLAP
DUPLICATE KEY(`k1` )
COMMENT "OLAP"
DISTRIBUTED BY HASH(`v1` ) BUCKETS 3
PROPERTIES (
    "replication_num" = "1",
    "in_memory" = "false",
    "storage_format" = "DEFAULT"
);
```

在dorisDB创建spark1资源

```
-- add broker1
MySQL [(none)]> ALTER SYSTEM ADD BROKER broker1 "master1:8000";
Query OK, 0 rows affected (0.04 sec)

-- yarn HA cluster 模式
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
  "working_dir" = "hdfs://mycluster/tmp/doris",
  "broker" = "broker1"
);
```

启动spark load作业

```
USE dorisdb_demo;
LOAD LABEL dorisdb_demo.label1
(
    DATA INFILE("hdfs://mycluster/dorisDB-demo/data/demo3_data1.csv")
    INTO TABLE demo3_spark_tb1
    COLUMNS TERMINATED BY "\t"
    (k1,v1)
    SET
    (
        k1=k1,
        v1=v1
    )
)
WITH RESOURCE 'spark1'
(
    "spark.executor.memory" = "500m",
    "spark.shuffle.compress" = "true",
    "spark.driver.memory" = "1g"
)
PROPERTIES
(
    "timeout" = "3600",
    "max_filter_ratio" = "0.2"
);
```

检查导入结果
```
MySQL [dorisdb_demo]> select * from demo3_spark_tb1 limit 5;
+------+------+
| k1   | v1   |
+------+------+
| 1    | 10   |
| 1    | 12   |
| 1    | 10   |
| 1    | 12   |
| 1    | 10   |
+------+------+
5 rows in set (0.18 sec)

MySQL [dorisdb_demo]> select count(1) from demo3_spark_tb1 limit 5;
+----------+
| count(1) |
+----------+
|    10000 |
+----------+
1 row in set (0.07 sec)

MySQL [dorisdb_demo]> select count(distinct v1) v1 from demo3_spark_tb1 limit 5;
+------+
| v1   |
+------+
|   12 |
+------+
1 row in set (0.03 sec)

MySQL [dorisdb_demo]> select count(distinct k1) k1 from demo3_spark_tb1 limit 5;
+------+
| k1   |
+------+
|   10 |
+------+
1 row in set (0.03 sec)
```


# Case2

通过Spark load导入parquet数据到dorisDB

- 需要hive外表

- 需要dorisDB外表

## 模拟数据

使用spark-shell将csv文件转储为parquet

```
scala> sc.setLogLevel("ERROR")

scala> val df = spark.read.option("delimiter","\t").csv("hdfs://mycluster/dorisDB-demo/data/demo3_data1.csv").toDF("k1","v1")
df: org.apache.spark.sql.DataFrame = [k1: string, v1: string]

scala> df.show(5, false)
+---+---+
|k1 |v1 |
+---+---+
|1  |10 |
|9  |5  |
|8  |8  |
|8  |3  |
|9  |11 |
+---+---+
only showing top 5 rows

scala> df.coalesce(1).write.parquet("hdfs://mycluster/dorisDB-demo/data/demo3_data1.parquet")

scala> spark.read.parquet("hdfs://mycluster/dorisDB-demo/data/demo3_data1.parquet").show(5)
+---+---+
| k1| v1|
+---+---+
|  1| 10|
|  9|  5|
|  8|  8|
|  8|  3|
|  9| 11|
+---+---+
only showing top 5 rows     
```  

## 功能测试

### Hive DDL

```
CREATE EXTERNAL TABLE `t1`(
 `k1` string,
 `v1` string)
ROW FORMAT SERDE
 'org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe'
STORED AS INPUTFORMAT
 'org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat'
OUTPUTFORMAT
 'org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat'
LOCATION
 'hdfs://mycluster/dorisDB-demo/data/demo3_data1.parquet'
TBLPROPERTIES ( 'parquet.compression'='snappy');
```

### Doris DDL

```
CREATE EXTERNAL RESOURCE "hive0"
PROPERTIES (
  "type" = "hive", 
  "hive.metastore.uris" = "thrift://master1:9083"
);


MySQL [dorisdb_demo]> create table demo3_spark_tb2 like demo3_spark_tb1;
Query OK, 0 rows affected (0.07 sec)


MySQL [dorisdb_demo]> CREATE EXTERNAL TABLE hive_t1
    ->     (
    ->          k1 string,
    ->          v1 string
    ->      )
    ->  ENGINE=hive
    ->  properties (
    ->     "resource" = "hive0",
    ->     "database" = "default",
    ->     "table" = "t1");
Query OK, 0 rows affected (0.03 sec)

```

### Spark load 

从dorisDB中的Hive外表写入dorisDB内部表

```
USE dorisdb_demo;
LOAD LABEL dorisdb_demo.label2
(
    DATA FROM TABLE hive_t1
    INTO TABLE demo3_spark_tb2
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

show load

```
*************************** 9. row ***************************
         JobId: 14039
         Label: label2
         State: FINISHED
      Progress: ETL:100%; LOAD:100%
          Type: SPARK
       EtlInfo: unselected.rows=0; dpp.abnorm.ALL=0; dpp.norm.ALL=10000
      TaskInfo: cluster:spark1; timeout(s):3600; max_filter_ratio:0.2
      ErrorMsg: NULL
    CreateTime: 2021-05-31 21:05:45
  EtlStartTime: 2021-05-31 21:06:12
 EtlFinishTime: 2021-05-31 21:06:46
 LoadStartTime: 2021-05-31 21:06:46
LoadFinishTime: 2021-05-31 21:06:49
           URL: http://worker1:20888/proxy/application_1622453682723_0025/
    JobDetails: {"Unfinished backends":{"00000000-0000-0000-0000-000000000000":[]},"ScannedRows":9999,"TaskNumber":1,"All backends":{"00000000-0000-0000-0000-000000000000":[-1]},"FileNumber":0,"FileSize":0}
9 rows in set (0.00 sec)
```

### 验证导入数据

```
MySQL [dorisdb_demo]> select * from demo3_spark_tb2 limit 5;
+------+------+
| k1   | v1   |
+------+------+
| 1    | 3    |
| 1    | 2    |
| 1    | 3    |
| 1    | 2    |
| 1    | 6    |
+------+------+
5 rows in set (0.06 sec)

MySQL [dorisdb_demo]> select count(1) from demo3_spark_tb2 limit 5;
+----------+
| count(1) |
+----------+
|    10000 |
+----------+
1 row in set (0.03 sec)

MySQL [dorisdb_demo]> select count(distinct k1) k1 from demo3_spark_tb2 limit 5;
+------+
| k1   |
+------+
|   10 |
+------+
1 row in set (0.02 sec)

MySQL [dorisdb_demo]> select count(distinct v1) v1 from demo3_spark_tb2 limit 5;
+------+
| v1   |
+------+
|   12 |
+------+
1 row in set (0.02 sec)
```

# NOTE
spark-submit的日志在fe-leader节点：
```
log/spark_launcher_log/
```


# License

DorisDB/demo is under the Apache 2.0 license. See the [LICENSE](../LICENSE) file for details.