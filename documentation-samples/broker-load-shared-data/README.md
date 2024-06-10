# Loading shared-data Starrocks with Redpanda

See https://docs.starrocks.io/docs/quick_start/redpanda-shared-data/ for documentation on this demo.

This directory contains the `docker-compose.yml` and a script to generate data in a Redpanda topic

```SQL
CREATE DATABASE sr_hub;
```

```SQL
USE sr_hub;
```

```SQL
CREATE TABLE example_tbl2 (
    `uid` bigint NOT NULL COMMENT "uid",
    `site` string NOT NULL COMMENT "site url",
    `vtime` bigint NOT NULL COMMENT "vtime"
)
DISTRIBUTED BY HASH(`uid`)
PROPERTIES("replication_num"="1");
```

```SQL
USE sr_hub;
```

```SQL
-- STOP ROUTINE LOAD FOR example_tbl2_test2;
```

```SQL
CREATE ROUTINE LOAD example_tbl2_test2 ON example_tbl2
PROPERTIES
(
    "format" = "json",
    "jsonpaths" ="[\"$.uid\",\"$.site\",\"$.vtime\"]"
)
FROM KAFKA
(
    "kafka_broker_list" = "redpanda:29092",
    "kafka_topic" = "test2"
);
```

```SQL
SHOW ROUTINE LOAD\G
```

```SQL
*************************** 1. row ***************************
                  Id: 10075
                Name: example_tbl2_test2
          CreateTime: 2024-06-10 15:53:30
           PauseTime: NULL
             EndTime: NULL
              DbName: sr_hub
           TableName: example_tbl2
               State: RUNNING
      DataSourceType: KAFKA
      CurrentTaskNum: 1
       JobProperties: {"partitions":"*","partial_update":"false","columnToColumnExpr":"*","maxBatchIntervalS":"10","partial_update_mode":"null","whereExpr":"*","dataFormat":"json","timezone":"Etc/UTC","format":"json","log_rejected_record_num":"0","taskTimeoutSecond":"60","json_root":"","maxFilterRatio":"1.0","strict_mode":"false","jsonpaths":"[\"$.uid\",\"$.site\",\"$.vtime\"]","taskConsumeSecond":"15","desireTaskConcurrentNum":"5","maxErrorNum":"0","strip_outer_array":"false","currentTaskConcurrentNum":"1","maxBatchRows":"200000"}
DataSourceProperties: {"topic":"test2","currentKafkaPartitions":"0","brokerList":"redpanda:29092"}
    CustomProperties: {"group.id":"example_tbl2_test2_9ce0e896-a17f-43ff-86e1-5e413c7baabf"}
           Statistic: {"receivedBytes":0,"errorRows":0,"committedTaskNum":0,"loadedRows":0,"loadRowsRate":0,"abortedTaskNum":0,"totalRows":0,"unselectedRows":0,"receivedBytesRate":0,"taskExecuteTimeMs":1}
            Progress: {"0":"4"}
   TimestampProgress: {}
ReasonOfStateChanged:
        ErrorLogUrls:
         TrackingSQL:
            OtherMsg:
LatestSourcePosition: {}
1 row in set (0.03 sec)
```

## Generate data

From a command shell in the `demo/documentation-samples/broker-load-shared-data/` folder run this command to generate data:

```python
python gen.py 5
```

```plaintext
b'{ "uid": 6926, "site": "https://docs.starrocks.io/", "vtime": 1718034793 } '
b'{ "uid": 3303, "site": "https://www.starrocks.io/product/community", "vtime": 1718034793 } '
b'{ "uid": 227, "site": "https://docs.starrocks.io/", "vtime": 1718034243 } '
b'{ "uid": 7273, "site": "https://docs.starrocks.io/", "vtime": 1718034794 } '
b'{ "uid": 4666, "site": "https://www.starrocks.io/", "vtime": 1718034794 } '
➜  broker-load-shared-data git:(update-kafka) ✗ python gen.py 5

b'{ "uid": 3741, "site": "https://www.starrocks.io/product/community", "vtime": 1718030845 } '
b'{ "uid": 4792, "site": "https://www.starrocks.io/", "vtime": 1718033413 } '
b'{ "uid": 4607, "site": "https://www.starrocks.io/blog", "vtime": 1718031441 } '
b'{ "uid": 1575, "site": "https://www.starrocks.io/", "vtime": 1718031523 } '
b'{ "uid": 2398, "site": "https://docs.starrocks.io/", "vtime": 1718033630 } '
```

```SQL
select * from example_tbl2;
```

```SQL
+------+--------------------------------------------+------------+
| uid  | site                                       | vtime      |
+------+--------------------------------------------+------------+
| 4607 | https://www.starrocks.io/blog              | 1718031441 |
| 1575 | https://www.starrocks.io/                  | 1718031523 |
| 2398 | https://docs.starrocks.io/                 | 1718033630 |
| 3741 | https://www.starrocks.io/product/community | 1718030845 |
| 4792 | https://www.starrocks.io/                  | 1718033413 |
+------+--------------------------------------------+------------+
5 rows in set (0.07 sec)
```
