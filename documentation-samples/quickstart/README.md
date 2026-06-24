# Documentation quickstart

See https://docs.starrocks.io/docs/quick_start/shared-data/ for documentation on this demo.

This directory contains the `docker-compose.yml` and the datasets
- [NYC Motor Vehicle Collisions - Crashes](https://data.cityofnewyork.us/Public-Safety/Motor-Vehicle-Collisions-Crashes/h9gi-nx95)
- [NOAA Weather Data](https://www.ncdc.noaa.gov/cdo-web/datatools/lcd)

## Connecting from external clients

The `docker-compose.yml` sets `HOST_TYPE=FQDN` so that the FE and CN register their
**hostnames** (`starrocks-fe`, `starrocks-cn`) with the cluster rather than their
internal container IP addresses. This matters for clients that connect from the host
— for example a Stream Load or the Flink-StarRocks connector, which queries the FE
for CN endpoints and then connects to them directly (e.g. on port 8040).

For those hostnames to resolve from outside Docker, add them to your `/etc/hosts`
file pointing at `127.0.0.1`:

```
127.0.0.1  starrocks-fe  starrocks-be  starrocks-cn
```

Without these entries an external client will receive a hostname (or, without
`HOST_TYPE=FQDN`, an unroutable container IP) that it cannot reach.
