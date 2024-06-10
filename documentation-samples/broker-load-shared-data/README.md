# Loading shared-data Starrocks with Redpanda

See https://docs.starrocks.io/docs/quick_start/redpanda-shared-data/ for documentation on this demo.

This directory contains the `docker-compose.yml` and a script to generate data in a Redpanda topic

CREATE DATABASE sr_hub;

USE sr_hub;

CREATE TABLE example_tbl2 (
    `uid` bigint NOT NULL COMMENT "uid",
    `site` string NOT NULL COMMENT "site url",
    `vtime` bigint NOT NULL COMMENT "vtime"
)
DISTRIBUTED BY HASH(`uid`)
PROPERTIES("replication_num"="1");

USE sr_hub;

-- STOP ROUTINE LOAD FOR example_tbl2_test2;

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

select * from example_tbl2;
