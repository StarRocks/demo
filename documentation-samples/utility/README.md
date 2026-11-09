# Documentation utility environment

This is not associated with any particular quickstart or tutorial, but it is 
useful when you need a default configuration including:

- One FE
- One BE
- MinIO for object storage

Some StarRocks features, for example the FILES() table function, are meant to 
be used with S3 or S3 compatible object storage. The MinIO service in this 
configuration is good for that.

## MinIO AIStor license

MinIO AIStor requires a license to run. You can use an existing license, or you can request a new MinIO AIStor Free Tier license.

To request a new MinIO AIStor Free Tier license, go to the [MinIO AIStor pricing](https://min.io/pricing) page and select **Get Started** under the Free tier to obtain a free license. The free tier license allows you to run MinIO AIStor on a single compute resource with one or more drives.

Save the license file as `minio.license` in this directory, next to `docker-compose.yml`. The compose file mounts it into the MinIO AIStor container. Without a valid license, MinIO AIStor starts but denies all S3 operations.

## MinIO credentials

See the compose file, specifically the minio_mc service for the service account
access key and secret.

