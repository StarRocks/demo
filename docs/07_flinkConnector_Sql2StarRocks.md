# 07_flinkConnector_Sql2StarRocks

## DDL

```
MySQL [starrocks_demo]> CREATE TABLE `starrocks_demo`.`demo2_flink_tb1` (
    ->   `name` VARCHAR(100) NOT NULL COMMENT "姓名",
    ->   `score` INT(2) NOT NULL COMMENT "得分"
    -> ) ENGINE=OLAP
    -> DUPLICATE KEY(`name`)
    -> COMMENT "OLAP"
    -> DISTRIBUTED BY HASH(`name`) BUCKETS 3
    -> PROPERTIES (
    -> "replication_num" = "1",
    -> "in_memory" = "false",
    -> "storage_format" = "V2"
    -> );
Query OK, 0 rows affected (0.11 sec)

```

## Performing

Run [Sql2StarRocks](../FlinkDemo/src/main/scala/com/starrocks/flink/Sql2StarRocks.scala) directly in IDEA

## Verification

```
MySQL [starrocks_demo]> select * from demo2_flink_tb1 limit 5;
+--------+-------+
| name   | score |
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
|      231 |
+----------+
1 row in set (0.02 sec)

MySQL [starrocks_demo]> select sum(score) sc , name from demo2_flink_tb1 group by name;
+------+---------+
| sc   | name    |
+------+---------+
| 3922 | lebron  |
| 3538 | kobe    |
| 3496 | stephen |
+------+---------+
3 rows in set (0.02 sec)

MySQL [starrocks_demo]> select sum(score) sc , name from demo2_flink_tb1 group by name;
+------+---------+
| sc   | name    |
+------+---------+
| 3627 | kobe    |
| 3592 | stephen |
| 3946 | lebron  |
+------+---------+
3 rows in set (0.02 sec)
```