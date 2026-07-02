-- belt-and-braces: default is 10 s, too tight for object-store tablet creation
ADMIN SET FRONTEND CONFIG ('tablet_create_timeout_second'='60');

CREATE STORAGE VOLUME s3_volume
    TYPE = S3
    LOCATIONS = ("s3://my-starrocks-bucket/")
    PROPERTIES (
        "enabled" = "true",
        "aws.s3.endpoint" = "minio:9000",
        "aws.s3.access_key" = "AAAAAAAAAAAAAAAAAAAA",
        "aws.s3.secret_key" = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        "aws.s3.use_instance_profile" = "false",
        "aws.s3.use_aws_sdk_default_behavior" = "false"
    );

SET s3_volume AS DEFAULT STORAGE VOLUME;   -- REQUIRED before CREATE DATABASE/TABLE
DESC STORAGE VOLUME s3_volume\G            -- verify: enabled=true, IsDefault=true

