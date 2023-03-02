-- BrokerLoad scripts to SSB tables from an AWS S3 bucket to StarRocks

-- This script uses instance profile authentication method. View this documentation for more supported authentication
-- methods: https://docs.starrocks.io/en-us/latest/integrations/authenticate_to_aws_resources

-- Please edit the S3 path, table name and s3 endpoint.
-- See broker load here: https://docs.starrocks.io/en-us/latest/loading/BrokerLoad

USE load_broker;

LOAD LABEL load_broker.lineorder
(
    DATA INFILE("s3a://<your-bucket>/ssb_50mb_parquet/lineorder/*")
    INTO TABLE lineorder
    FORMAT AS "parquet"
)
WITH BROKER
(
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com" -- change to your region
);

LOAD LABEL load_broker.customer
(
    DATA INFILE("s3a://<your-bucket>/ssb_50mb_parquet/customer/*")
    INTO TABLE customer
    FORMAT AS "parquet"
)
WITH BROKER
(
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com" -- change to your region
);

LOAD LABEL load_broker.dates
(
    DATA INFILE("s3a://<your-bucket>/ssb_50mb_parquet/dates/*")
    INTO TABLE dates
    FORMAT AS "parquet"
)
WITH BROKER
(
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com" -- change to your region
);

LOAD LABEL load_broker.part
(
    DATA INFILE("s3a://<your-bucket>/ssb_50mb_parquet/part/*")
    INTO TABLE part
    FORMAT AS "parquet"
)
WITH BROKER
(
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com" -- change to your region
);

LOAD LABEL load_broker.supplier
(
    DATA INFILE("s3a://<your-bucket>/ssb_50mb_parquet/supplier/*")
    INTO TABLE supplier
    FORMAT AS "parquet"
)
WITH BROKER
(
    "aws.s3.use_instance_profile" = "True",
    "aws.s3.endpoint" = "s3.<your-region>.amazonaws.com" -- change to your region
);