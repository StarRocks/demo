# 05_flinkConnector_Bean2StarRocks

## DDL

```
MySQL [starrocks_demo]> CREATE TABLE `starrocks_demo`.`demo2_flink_tb1` (
    ->   `NAME` VARCHAR(100) NOT NULL COMMENT "姓名",
    ->   `SCORE` INT(2) NOT NULL COMMENT "得分"
    -> ) ENGINE=OLAP
    -> DUPLICATE KEY(`NAME`)
    -> COMMENT "OLAP"
    -> DISTRIBUTED BY HASH(`NAME`) BUCKETS 3
    -> PROPERTIES (
    -> "replication_num" = "1",
    -> "in_memory" = "false",
    -> "storage_format" = "V2"
    -> );
Query OK, 0 rows affected (0.11 sec)


```
## 执行程序

1. 可以在IDEA里执行 FlinkDemo模块的[Bean2StarRocks](../FlinkDemo/src/main/scala/com/starrocks/flink/Bean2StarRocks.scala)
2. 也可以打包在server上提交flink作业：

run.sh
```
#!/bin/bash

~/app/flink-1.11.0/bin/flink run \
-m yarn-cluster \
--yarnname Demo \
-c com.starrocks.flink.Demo1 \
-yjm 1048 -ytm 1048 \
-ys 1 -d  \
./demo.jar
```
flink ui
![05_flink_ui_1](../imgs/05_flink_ui_1.png)


## 验证数据持续导入

```
MySQL [starrocks_demo]> select * from demo2_flink_tb1 limit 5;
+--------+-------+
| NAME   | SCORE |
+--------+-------+
| lebron |    43 |
| lebron |    11 |
| lebron |    42 |
| lebron |    96 |
| kobe   |    29 |
+--------+-------+
5 rows in set (0.08 sec)

MySQL [starrocks_demo]> select count(1) from demo2_flink_tb1;
+----------+
| count(1) |
+----------+
|       18 |
+----------+
1 row in set (0.04 sec)

MySQL [starrocks_demo]> select count(1) from demo2_flink_tb1;
+----------+
| count(1) |
+----------+
|       24 |
+----------+
1 row in set (0.02 sec)
```

