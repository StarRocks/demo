# StarRocks with an Iceberg REST catalog

This directory contains a `docker-compose.yml` that runs StarRocks alongside an
Iceberg REST catalog backed by MinIO.

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
