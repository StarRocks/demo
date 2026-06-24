<!--
Copyright (c) 2021 Beijing Dingshi Zongheng Technology Co., Ltd. All rights reserved.

Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# StarRocks stream load — Python demo

Python counterparts to the Java and Go stream load examples in the parent
directory. Both scripts load a few rows into a StarRocks table over HTTP using
[StarRocks Stream Load](https://docs.starrocks.io/docs/sql-reference/sql-statements/loading_unloading/STREAM_LOAD/):

| Script              | Approach                                                              |
| ------------------- | -------------------------------------------------------------------- |
| `CurlStreamLoad.py` | Shells out to `curl` via `subprocess`. Loads from a file and stdin.  |
| `ReqStreamLoad.py`  | Uses the [`requests`](https://requests.readthedocs.io/) library.     |

These were originally written for Python 2 and have been ported to **Python 3**.

## Prerequisites

1. **StarRocks** running and reachable on the FE HTTP port (default `8030`).
   The quickest way is the all-in-one Docker image (FE + BE in one container):

   ```bash
   docker pull starrocks/allin1-ubuntu
   docker run -p 9030:9030 -p 8030:8030 -p 8040:8040 -itd \
     --name quickstart starrocks/allin1-ubuntu
   ```

   (See the [Deploy StarRocks with Docker](https://docs.starrocks.io/docs/quick_start/shared-nothing/)
   quick start for details.) Give the container a minute to become healthy:
   `docker ps` should show `(healthy)`.

2. **A target database and table.** Both scripts load into `starrocks_demo.tb1`.
   Create it with any MySQL client — for example the one bundled in the
   container:

   ```bash
   docker exec -it quickstart \
     mysql -P 9030 -h 127.0.0.1 -u root --prompt="StarRocks > "
   ```

   ```sql
   CREATE DATABASE IF NOT EXISTS starrocks_demo;

   CREATE TABLE starrocks_demo.tb1 (
     `k` varchar(65533) NULL COMMENT "",
     `v` varchar(65533) NULL COMMENT ""
   ) ENGINE=OLAP
   DUPLICATE KEY(`k`)
   DISTRIBUTED BY HASH(`k`) BUCKETS 1
   PROPERTIES ("replication_num" = "1");
   ```

3. **Python 3** (tested with 3.9). For `ReqStreamLoad.py` install `requests`:

   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install requests
   ```

   `CurlStreamLoad.py` only needs the standard library plus the `curl`
   command-line tool.

The scripts connect to `localhost:8030` with the default `root` user and an
empty password. Edit the config block at the bottom of each script (or the
`main()` function) if your cluster differs.

## Run

### `CurlStreamLoad.py`

Loads from a local file, then from stdin. Create the sample file first:

```bash
printf 'k1,v1\nk2,v2\nk3,v3\n' > /tmp/test.csv

# loads /tmp/test.csv, then the rows piped in on stdin
echo 'k4,v4
k5,v5' | python3 CurlStreamLoad.py
```

You'll see a JSON result for each load with `"Status": "Success"` followed by
`Load to starrocks success! LABEL is ...`.

### `ReqStreamLoad.py`

Loads three rows from an in-memory string:

```bash
python3 ReqStreamLoad.py
```

On success it prints the JSON load result with `"Status": "Success"`.

### Verify

```bash
docker exec -it quickstart \
  mysql -P 9030 -h 127.0.0.1 -u root \
  -e "SELECT * FROM starrocks_demo.tb1;"
```

## Notes

- **`Expect: 100-continue` is required.** The StarRocks FE rejects a stream load
  that arrives without this header (`There is no 100-continue header`). Both
  scripts set it explicitly:

  - `curl` adds it automatically for chunked uploads (such as stdin) but
    **omits it for small fixed-size files**, so `CurlStreamLoad.py` passes
    `-H "Expect: 100-continue"`.
  - `requests` never sends it on its own, so `ReqStreamLoad.py` adds it to the
    request headers.

  This matches the Go and Java demos in the sibling directories.

- **Auth survives the FE redirect.** A stream load to the FE answers with a
  redirect to a BE. `CurlStreamLoad.py` uses `curl --location-trusted` to keep
  the credentials across the redirect; `ReqStreamLoad.py` subclasses
  `requests.Session` and overrides `rebuild_auth()` so the `Authorization`
  header is preserved.

- **An HTTP 200 does not mean the load succeeded.** Always check the `"Status"`
  field in the JSON response body.

- **Labels give at-most-once semantics.** `CurlStreamLoad.py` generates a unique
  `label` per load; re-running with the same label returns `Label Already
  Exists` instead of double-loading.
