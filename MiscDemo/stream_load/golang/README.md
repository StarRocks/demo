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

# StarRocks stream load — Go demo

A Go counterpart to the Java and Python stream load examples in the parent
directory. It loads a few rows into a StarRocks table over HTTP using only the
standard library (`net/http`).

## Prerequisites

1. StarRocks running and reachable on the FE HTTP port (default `8030`).

2. A target table. Create it with any MySQL client:
   
   ```sql
   CREATE DATABASE IF NOT EXISTS test;

   USE test;
   ```

   ```sql
   CREATE TABLE `stream_test` (
     `id`       bigint(20)  COMMENT "",
     `id2`      bigint(20)  COMMENT "",
     `username` varchar(32) COMMENT ""
   ) ENGINE=OLAP
   DUPLICATE KEY(`id`)
   DISTRIBUTED BY HASH(`id`) BUCKETS 20;
   ```

3. Edit the config constants at the top of `stream_load.go` (host, port, db,
   table, user, password) to match your cluster.

## Run

```bash
go run stream_load.go
```

On success you'll see the JSON load result with `"Status": "Success"`.

> Note: an HTTP 200 only means the BE service answered — it does **not** mean
> the load succeeded. Always check the `"Status"` field in the response body.

## Go toolchain (`mise.toml`)

This directory includes a `mise.toml` that pins the Go toolchain with
[mise](https://mise.jdx.dev/):

```toml
[tools]
go = "1.24"
```

With `mise` installed, `cd` into this directory and the right Go is selected
automatically (`mise install` fetches it the first time). The `go.mod` `go`
directive is only a *minimum* — anything 1.20+ compiles the demo — but the pin
matters on recent macOS:

- On **macOS Darwin 25.5**, building this demo with **Go 1.22.x** compiles fine,
  but the resulting binary fails to launch under the system loader:

  ```
  dyld: missing LC_UUID load command
  signal: abort trap
  ```

  Newer `dyld` rejects Mach-O binaries that lack an `LC_UUID`, which Go 1.22's
  internal linker omits. (Workaround on 1.22:
  `go test -ldflags=-linkmode=external`.)

- **Go 1.24.x** emits `LC_UUID` with the default internal linker, so the pin to
  `go = "1.24"` avoids the problem entirely. Verified with `go1.24.13`.

If you manage Go some other way (Homebrew, `goenv`, the official installer, or
Go 1.21+'s built-in `GOTOOLCHAIN=auto`), just use Go 1.24 or newer on Darwin
25.5 and you can ignore `mise.toml`.

## Notes

- The FE answers a stream load with a redirect to a BE. Go strips the
  `Authorization` header when redirecting to a different host, so the demo
  re-attaches it in a `CheckRedirect` hook (the Java and Python demos do the
  equivalent).

- The demo sends `Expect: 100-continue`. The StarRocks FE **requires** this
  header and rejects a load without it (`There is no 100-continue header`). Go's
  `net/http` drives the handshake across the FE→BE redirect because
  `http.NewRequest` with a `bytes.Reader` populates `req.GetBody` (so the body is
  replayable) and `http.DefaultTransport` uses a non-zero `ExpectContinueTimeout`.

- Supplying a unique `label` header gives at-most-once semantics: re-running
  with the same label returns `Label Already Exists`.
