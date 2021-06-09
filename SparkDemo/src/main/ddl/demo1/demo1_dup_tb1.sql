CREATE TABLE `demo1_dup_tb1` (
  `username` varchar(32) NULL DEFAULT "" COMMENT "",
   `date` int(11) NULL DEFAULT "10" COMMENT "",
  `hour` smallint(6) NULL COMMENT "",
  `minute` smallint(6) NULL COMMENT "",
  `visit` bigint(20)  NULL DEFAULT "0" COMMENT ""
) ENGINE=OLAP
DUPLICATE KEY(`username`,`date`,`hour`)
COMMENT "OLAP"
DISTRIBUTED BY HASH(`username`) BUCKETS 3
PROPERTIES (
"replication_num" = "1",
"in_memory" = "false",
"storage_format" = "DEFAULT"
);

