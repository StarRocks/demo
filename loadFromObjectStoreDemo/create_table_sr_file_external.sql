-- these SQL statements creates external file tables for ssb_50mb_parquet dataset.
-- This script uses instance profile authentication method. View this documentation for more supported authentication
-- methods: https://docs.starrocks.io/en-us/latest/integrations/authenticate_to_aws_resources

-- Please edit the S3 path, table name and s3 endpoint. Replace <your-bucket> and <your-region>
-- See file external table here: https://docs.starrocks.io/en-us/latest/data_source/file_external_table

CREATE DATABASE file_external_tables;

USE file_external_tables;

CREATE EXTERNAL TABLE lineorder (
    `lo_orderkey` bigint(20) NOT NULL COMMENT "",
    `lo_linenumber` int(11) NOT NULL COMMENT "",
    `lo_custkey` int(11) NOT NULL COMMENT "",
    `lo_partkey` int(11) NOT NULL COMMENT "",
    `lo_suppkey` int(11) NOT NULL COMMENT "",
    `lo_orderdate` int(11) NOT NULL COMMENT "",
    `lo_orderpriority` varchar(16) NOT NULL COMMENT "",
    `lo_shippriority` int(11) NOT NULL COMMENT "",
    `lo_quantity` int(11) NOT NULL COMMENT "",
    `lo_extendedprice` int(11) NOT NULL COMMENT "",
    `lo_ordtotalprice` int(11) NOT NULL COMMENT "",
    `lo_discount` int(11) NOT NULL COMMENT "",
    `lo_revenue` int(11) NOT NULL COMMENT "",
    `lo_supplycost` int(11) NOT NULL COMMENT "",
    `lo_tax` int(11) NOT NULL COMMENT "",
    `lo_commitdate` int(11) NOT NULL COMMENT "",
    `lo_shipmode` varchar(11) NOT NULL COMMENT ""
)
ENGINE = FILE
PROPERTIES(
    "path" = "s3://<your-bucket>/ssb_50mb_parquet/lineorder/",
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com", -- change to your region
    "format" = "parquet"
);


CREATE EXTERNAL TABLE IF NOT EXISTS `customer` (
 `c_custkey` int(11) NOT NULL COMMENT "",
 `c_name` varchar(26) NOT NULL COMMENT "",
 `c_address` varchar(41) NOT NULL COMMENT "",
 `c_city` varchar(11) NOT NULL COMMENT "",
 `c_nation` varchar(16) NOT NULL COMMENT "",
 `c_region` varchar(13) NOT NULL COMMENT "",
 `c_phone` varchar(16) NOT NULL COMMENT "",
 `c_mktsegment` varchar(11) NOT NULL COMMENT ""
) ENGINE = FILE
PROPERTIES(
    "path" = "s3://<your-bucket>/ssb_50mb_parquet/customer/",
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com",
    "format" = "parquet"
);
;


CREATE EXTERNAL TABLE IF NOT EXISTS `dates` (
 `d_datekey` int(11) NOT NULL COMMENT "",
 `d_date` varchar(20) NOT NULL COMMENT "",
 `d_dayofweek` varchar(10) NOT NULL COMMENT "",
 `d_month` varchar(11) NOT NULL COMMENT "",
 `d_year` int(11) NOT NULL COMMENT "",
 `d_yearmonthnum` int(11) NOT NULL COMMENT "",
 `d_yearmonth` varchar(9) NOT NULL COMMENT "",
 `d_daynuminweek` int(11) NOT NULL COMMENT "",
 `d_daynuminmonth` int(11) NOT NULL COMMENT "",
 `d_daynuminyear` int(11) NOT NULL COMMENT "",
 `d_monthnuminyear` int(11) NOT NULL COMMENT "",
 `d_weeknuminyear` int(11) NOT NULL COMMENT "",
 `d_sellingseason` varchar(14) NOT NULL COMMENT "",
 `d_lastdayinweekfl` int(11) NOT NULL COMMENT "",
 `d_lastdayinmonthfl` int(11) NOT NULL COMMENT "",
 `d_holidayfl` int(11) NOT NULL COMMENT "",
 `d_weekdayfl` int(11) NOT NULL COMMENT ""
) ENGINE = FILE
PROPERTIES(
    "path" = "s3://<your-bucket>/ssb_50mb_parquet/dates/",
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com",
    "format" = "parquet"
);

 CREATE EXTERNAL TABLE IF NOT EXISTS `supplier` (
 `s_suppkey` int(11) NOT NULL COMMENT "",
 `s_name` varchar(26) NOT NULL COMMENT "",
 `s_address` varchar(26) NOT NULL COMMENT "",
 `s_city` varchar(11) NOT NULL COMMENT "",
 `s_nation` varchar(16) NOT NULL COMMENT "",
 `s_region` varchar(13) NOT NULL COMMENT "",
 `s_phone` varchar(16) NOT NULL COMMENT ""
) ENGINE = FILE
PROPERTIES(
    "path" = "s3://<your-bucket>/ssb_50mb_parquet/supplier/",
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com",
    "format" = "parquet"
);

CREATE EXTERNAL TABLE IF NOT EXISTS `part` (
 `p_partkey` int(11) NOT NULL COMMENT "",
 `p_name` varchar(23) NOT NULL COMMENT "",
 `p_mfgr` varchar(7) NOT NULL COMMENT "",
 `p_category` varchar(8) NOT NULL COMMENT "",
 `p_brand` varchar(10) NOT NULL COMMENT "",
 `p_color` varchar(12) NOT NULL COMMENT "",
 `p_type` varchar(26) NOT NULL COMMENT "",
 `p_size` int(11) NOT NULL COMMENT "",
 `p_container` varchar(11) NOT NULL COMMENT ""
) ENGINE = FILE
PROPERTIES(
    "path" = "s3://<your-bucket>/ssb_50mb_parquet/part/",
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com",
    "format" = "parquet"
);