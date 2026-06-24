# Loading shared-data Starrocks with Redpanda

See [https://docs.starrocks.io/docs/quick_start/routine-load/](https://docs.starrocks.io/docs/quick_start/routine-load/) for documentation on this demo.

This directory contains the `docker-compose.yml` and a script to generate data in a Redpanda topic.

## Connecting from external clients

The `docker-compose.yml` sets `HOST_TYPE=FQDN` so that the FE and CN register their
**hostnames** (`starrocks-fe`, `starrocks-cn`) with the cluster rather than their
internal container IP addresses. This matters for clients that connect from the host
— for example the Flink-StarRocks connector, which queries the FE for CN endpoints
and then connects to them directly (e.g. on port 8040).

For those hostnames to resolve from outside Docker, add them to your `/etc/hosts`
file pointing at `127.0.0.1`:

```
127.0.0.1  starrocks-fe  starrocks-be  starrocks-cn
```

Without these entries an external client will receive a hostname (or, without
`HOST_TYPE=FQDN`, an unroutable container IP) that it cannot reach.
