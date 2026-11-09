# StarRocks with an Iceberg REST catalog

This directory contains a `docker-compose.yml` that runs StarRocks alongside an
Iceberg REST catalog backed by MinIO.

## MinIO AIStor license

MinIO AIStor requires a license to run. You can use an existing license, or you can request a new MinIO AIStor Free Tier license.

To request a new MinIO AIStor Free Tier license, go to the [MinIO AIStor pricing](https://min.io/pricing) page and select **Get Started** under the Free tier to obtain a free license. The free tier license allows you to run MinIO AIStor on a single compute resource with one or more drives.

Save the license file as `minio.license` in this directory, next to `docker-compose.yml`. The compose file mounts it into the MinIO AIStor container. Without a valid license, MinIO AIStor starts but denies all S3 operations.

## Connecting from external clients

The `docker-compose.yml` sets `HOST_TYPE=FQDN` so that the FE and BE register their
**hostnames** (`starrocks-fe`, `starrocks-be`) with the cluster rather than their
internal container IP addresses. This matters for clients that connect from the host
— for example a Stream Load or the Flink-StarRocks connector, which queries the FE
for BE endpoints and then connects to them directly (e.g. on port 8040).

For those hostnames to resolve from outside Docker, add them to your `/etc/hosts`
file pointing at `127.0.0.1`:

```
127.0.0.1  starrocks-fe  starrocks-be  starrocks-cn
```

Without these entries an external client will receive a hostname (or, without
`HOST_TYPE=FQDN`, an unroutable container IP) that it cannot reach.
