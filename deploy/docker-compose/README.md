# Docker Compose for StarRocks

This directory contains the Docker Compose YAML files for the StarRocks deployment.

You can deploy a StarRocks cluster with one BE node using [**docker-compose.yml**](./docker-compose.yml), or simulate a
distributed StarRocks cluster with multiple BEs using [docker-compose-3BE.yml](./docker-compose-3BE.yml).

Note that deploying with docker compose is only recommended in a testing environment, as high availability cannot be
guaranteed with a single instance deployment.

## Deploy StarRocks using Docker Compose

Run the following command to deploy StarRocks using Docker Compose:

```shell
docker-compose -f docker-compose.yml up -d
```

The commented-out sections in the above example YAML file define the mount paths and volumes that used to persist data
by FE or BE.

Note that root privilege is required to deploy StarRocks with Docker with persistent volume.

## Check cluster status

After StarRocks is deployed, check the cluster status:

1. In [docker-compose.yml](./docker-compose.yml) file, FE container port 9030 is exposed to the host. You can connect to
   the FE instance from the host.
   ```shell
   mysql -h 127.0.0.1 -P9030 -uroot
   ```

2. Check the status of the BE node.

   ```shell
   show backends;
   ```

If the field `Alive` is true, this BE node is properly started and added to the cluster.

## Connecting from external clients

The compose files set `HOST_TYPE=FQDN` so that the FE, BE, and CN register their
**hostnames** with the cluster rather than their internal container IP addresses.
This matters for clients that connect from the host — for example the
Flink-StarRocks connector, which queries the FE for BE/CN endpoints and then
connects to them directly (e.g. on port 8040).

For those hostnames to resolve from outside Docker, add them to your `/etc/hosts`
file pointing at `127.0.0.1`. The compose files in this directory use these
hostnames:

```
# docker-compose.yml / docker-compose-3BE.yml
127.0.0.1  starrocks-fe-0  starrocks-be-0  starrocks-be-1  starrocks-be-2
# docker-compose-shared-data.yml
127.0.0.1  starrocks-fe  starrocks-cn
```

Without these entries an external client will receive a hostname (or, without
`HOST_TYPE=FQDN`, an unroutable container IP) that it cannot reach.

## Troubleshooting

When you connect to the cluster, StarRocks may return the following error:

```shell
 ERROR 2003 (HY000): Can't connect to MySQL server on 'starrocks-fe:9030' (111)
```

The reason may be that the BE node was started before the FE node is ready. To solve this problem, re-run the docker
compose up command, or manually add the BE node to the cluster using the following command:

```sql
# in docker-compose.yml
ADD BACKEND "starrocks-be-0:9050";
```
