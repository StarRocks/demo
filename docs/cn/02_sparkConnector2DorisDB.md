# 02_sparkConnector2DorisDB

## 场景描述

### 效果
  - 读取DorisDB源表，经过Spark进行ETL，写回DorisDB目标表。
  - demo2的代码演示效果：用sparkSql进行explode出多行uid结果明细。

### 数据流向

> dorisDB(bitmap表) -> spark connector -> sparkSql ETL -> dorisDB(uid明细表)

## 基础环境准备

### 数据准备
> - demo2复用demo1的数据构建src源表，
> - demo1_spark_tb0（refer to  [01_sparkStreaming2DorisDB](./01_sparkStreaming2DorisDB.md)  ）的uv字段是bitmap型，
> - 使用bitmap_to_string(uv)转为string_list，写进demo1_spark_tb1表

#### 数据源表DDL

```
CREATE TABLE `demo1_spark_tb1` (
    `site`   varchar(50) NULL COMMENT "",
    `date`   DATE     NULL  COMMENT "",
    `hour`   smallint NULL COMMENT "",
    `minute` smallint NULL COMMENT "",
    `uid_list_str`  String NULL  COMMENT ""
) ENGINE=OLAP
DUPLICATE KEY(`site`,`date`,  `hour` , `minute` )
COMMENT "OLAP"
DISTRIBUTED BY HASH(`site`) BUCKETS 10
PROPERTIES (
    "replication_num" = "1",
    "in_memory" = "false",
    "storage_format" = "DEFAULT"
);
```

可以复用demo1模拟出的数据：

```
insert into demo1_spark_tb1(site, date, hour, minute, uid_list_str)
select site,date,hour,minute, bitmap_to_string(uv)
from demo1_spark_tb0;
```

检查写入的数据

```
MySQL [doris_demo]> select * from demo1_spark_tb1 limit 5;
+--------------------------+------------+------+--------+--------------+
| site                     | date       | hour | minute | uid_list_str |
+--------------------------+------------+------+--------+--------------+
| https://www.dorisdb.com/ | 2021-05-29 |   14 |     47 | 5282         |
| https://www.dorisdb.com/ | 2021-05-29 |   14 |     51 | 3157,7582    |
| https://www.dorisdb.com/ | 2021-05-29 |   14 |     55 | 2395,8287    |
| https://www.dorisdb.com/ | 2021-05-29 |   14 |     58 | 7021         |
| https://www.dorisdb.com/ | 2021-05-29 |   14 |     59 | 1041,9393    |
+--------------------------+------------+------+--------+--------------+
5 rows in set (0.01 sec)
```

#### 目标表DDL

```
CREATE TABLE `demo1_spark_tb2` (
    `uid`    INT         NULL  COMMENT "",
    `date`   DATE        NULL  COMMENT "",
    `hour`   smallint    NULL COMMENT "",
    `minute` smallint    NULL COMMENT "",
    `site`   varchar(50) NULL COMMENT ""
) ENGINE=OLAP
DUPLICATE KEY(`uid`, `date`,  `hour` , `minute` )
COMMENT "OLAP"
DISTRIBUTED BY HASH(`uid`) BUCKETS 10
PROPERTIES (
    "replication_num" = "1",
    "in_memory" = "false",
    "storage_format" = "DEFAULT"
);
```

## 执行程序

### 将spark-connector加入工程
![02_spark_idea1](./imgs/02_spark_idea1.png)

### 执行

在IDEA里运行SparkDemo模块的com.dorisdb.spark.SparkConnector2DorisDB

> 读取demo1_spark_tb1经sparkSql炸开list后，写入demo1_spark_tb2：

![02_spark_idea2](./imgs/02_spark_idea2.png)

### 检查目标表结果

```
MySQL [doris_demo]> select * from demo1_spark_tb2 limit 5;
+------+------------+------+--------+---------------------------+
| uid  | date       | hour | minute | site                      |
+------+------------+------+--------+---------------------------+
|   10 | 2021-05-29 |   16 |     52 | https://docs.dorisdb.com/ |
|   17 | 2021-05-29 |   16 |     38 | https://www.dorisdb.com/  |
|   18 | 2021-05-29 |   15 |     30 | https://www.dorisdb.com/  |
|   18 | 2021-05-29 |   16 |     58 | https://www.dorisdb.com/  |
|   20 | 2021-05-29 |   16 |     34 | https://docs.dorisdb.com/ |
+------+------------+------+--------+---------------------------+
5 rows in set (0.02 sec)
```

# License

DorisDB/demo is under the Apache 2.0 license. See the [LICENSE](../LICENSE) file for details.
