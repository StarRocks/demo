CREATE TABLE `demo1_agg_tb2` (
  `siteid` int(11) NULL DEFAULT "10" COMMENT "",
  `username` varchar(32) NULL DEFAULT "" COMMENT "",
  `visit` bigint(20) SUM NULL DEFAULT "0" COMMENT ""
) ENGINE=OLAP
AGGREGATE KEY(`siteid`,  `username`)
COMMENT "OLAP"
DISTRIBUTED BY HASH(`siteid`) BUCKETS 10
PROPERTIES (
"replication_num" = "1",
"in_memory" = "false",
"storage_format" = "DEFAULT"
);