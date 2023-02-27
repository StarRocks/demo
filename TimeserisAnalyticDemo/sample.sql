-- Create table to store vehicle metrics
CREATE TABLE mydb.vehicle_metrics (
  vin VARCHAR(32),                     
  ts DATETIME, 
  current int,
  voltage int,
  speed double,
  power double                  
)
DUPLICATE KEY(`vin`, `ts`)       
PARTITION BY RANGE(`ts`)
(
    PARTITION p20230220 VALUES LESS THAN ("2023-02-21"),
    PARTITION p20230221 VALUES LESS THAN ("2023-02-22"),
    PARTITION p20230222 VALUES LESS THAN ("2023-02-23")
)       
DISTRIBUTED BY HASH(`vin`)  
PROPERTIES (
    "replication_num" = "1",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-3",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "dynamic_partition.buckets" = "16"
);

-- Create table to store vehicle dimensions 
CREATE TABLE mydb.vehicle_dimensions (
  vin VARCHAR(32),    
  brand VARCHAR(16),
  size VARCHAR(16),
  color VARCHAR(16)               
)
DUPLICATE KEY(`vin`)       
DISTRIBUTED BY HASH(`vin`)        
PROPERTIES (
    "replication_num"="1"
);

-- Aggregate metrics
SELECT max(speed), min(speed), avg(speed) FROM vehicle_metrics WHERE
      vin="0G7SLVW1HCM8NTEYXB" AND
      ts >= "2023-02-20" AND 
      ts < "2023-02-21";

SELECT max(speed), min(speed), avg(speed) FROM vehicle_metrics WHERE
      ts >= "2023-02-20" AND 
      ts < "2023-02-21";

SELECT vin, time_slice(ts, interval 10 minute) as hour, round(avg(speed), 2) as avg_speed 
FROM vehicle_metrics WHERE
      vin = "0G7SLVW1HCM8NTEYXB" AND 
      ts >= "2023-02-20" AND 
      ts < "2023-02-21"
      GROUP BY vin, hour
      ORDER BY avg_speed DESC
      limit 10;

-- Join with dimensions
SELECT avg(speed), avg(power) FROM vehicle_metrics, vehicle_dimensions 
WHERE  vehicle_metrics.vin = vehicle_dimensions.vin AND 
       color = "red" AND
       brand = "TESLA" AND
       ts >= "2023-02-20" AND 
       ts < "2023-02-21";

-- Use MV to accelerate
CREATE MATERIALIZED VIEW vehicle_metrics_by_hour      
DISTRIBUTED BY HASH(`vin`)    
REFRESH ASYNC EVERY (interval 1 day)
PROPERTIES (
    "replication_num" = "1"
)
AS
SELECT vin, time_slice(ts, interval 1 hour) as ts_hour, sum(speed) as speed_sum, count(speed) as speed_count FROM vehicle_metrics 
GROUP BY vin, ts_hour;



