-- Please edit the S3 path,
-- Replace <your-bucket>

CREATE DATABASE IF NOT EXISTS ssb_50mb_parquet;

USE ssb_50mb_parquet;

CREATE EXTERNAL TABLE `customer`(
  `c_custkey` int,
  `c_name` varchar(26),
  `c_address` varchar(41),
  `c_city` varchar(11),
  `c_nation` varchar(16),
  `c_region` varchar(13),
  `c_phone` varchar(16),
  `c_mktsegment` varchar(11))
stored as parquet
location 's3://<your-bucket>/ssb_50mb_parquet/customer/';
  
  
  
  CREATE EXTERNAL TABLE `dates`(
  `d_datekey` int,
  `d_date` varchar(20),
  `d_dayofweek` varchar(10),
  `d_month` varchar(11),
  `d_year` int,
  `d_yearmonthnum` int,
  `d_yearmonth` varchar(9),
  `d_daynuminweek` int,
  `d_daynuminmonth` int,
  `d_daynuminyear` int,
  `d_monthnuminyear` int,
  `d_weeknuminyear` int,
  `d_sellingseason` varchar(14),
  `d_lastdayinweekfl` int,
  `d_lastdayinmonthfl` int,
  `d_holidayfl` int,
  `d_weekdayfl` int)
stored as parquet
location 's3://<your-bucket>/ssb_50mb_parquet/dates/';
  
  CREATE EXTERNAL TABLE `lineorder`(
  `lo_orderkey` bigint,
  `lo_linenumber` int,
  `lo_custkey` int,
  `lo_partkey` int,
  `lo_suppkey` int,
  `lo_orderdate` int,
  `lo_orderpriority` varchar(16),
  `lo_shippriority` int,
  `lo_quantity` int,
  `lo_extendedprice` int,
  `lo_ordtotalprice` int,
  `lo_discount` int,
  `lo_revenue` int,
  `lo_supplycost` int,
  `lo_tax` int,
  `lo_commitdate` int,
  `lo_shipmode` varchar(11))
stored as parquet
location 's3://<your-bucket>/ssb_50mb_parquet/lineorder/';


  CREATE EXTERNAL TABLE `part`(
  `p_partkey` int,
  `p_name` varchar(23),
  `p_mfgr` varchar(7),
  `p_category` varchar(8),
  `p_brand` varchar(10),
  `p_color` varchar(12),
  `p_type` varchar(26),
  `p_size` int,
  `p_container` varchar(11))
stored as parquet
location 's3://<your-bucket>/ssb_50mb_parquet/part/';
  
  CREATE EXTERNAL TABLE `supplier`(
  `s_suppkey` int,
  `s_name` varchar(26),
  `s_address` varchar(26),
  `s_city` varchar(11),
  `s_nation` varchar(16),
  `s_region` varchar(13),
  `s_phone` varchar(16))
stored as parquet
location 's3://<your-bucket>/ssb_50mb_parquet/supplier/';