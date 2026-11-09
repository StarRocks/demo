# Documentation verification

Two compose files, one for shared data, and one for shared nothing. These are used to verify doc samples.

## MinIO AIStor license

MinIO AIStor requires a license to run. You can use an existing license, or you can request a new MinIO AIStor Free Tier license.

To request a new MinIO AIStor Free Tier license, go to the [MinIO AIStor pricing](https://min.io/pricing) page and select **Get Started** under the Free tier to obtain a free license. The free tier license allows you to run MinIO AIStor on a single compute resource with one or more drives.

Save the license file as `minio.license` in this directory, next to `docker-compose-shared-data.yml`. The compose file mounts it into the MinIO AIStor container. Without a valid license, MinIO AIStor starts but denies all S3 operations.
