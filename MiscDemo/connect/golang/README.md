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


# How to use:
> Tip
>
> Make sure that StarRocks is running and available on port 9030 with no root password, or edit `client.go`. This demo creates a database, loads data, queries the data, and drops the database. Take a look at `client.go` before you run it in case there is a conflict with existing data.

## for Go 1.11 or higher
 1. Get the go-sql-driver for MySQL
    
    ```bash
    go mod download github.com/go-sql-driver/mysql
    ```
    
 2. Build
    ```bash
    go build
    ```
    
 3. Run the demo
    
    ```bash
    ./client
    ```
### Expected output:

```plaintext
connect to starrocks successfully
create database successfully
set db context successfully
create table successfully
insert data successfully
1	2	7
4	5	6
query data successfully
drop database successfully
```

## before Go 1.11
	1. copy client.go to your GOPATH/src
	2. go get -u github.com/go-sql-driver/mysql
	3. go build
	4. ./client

## What can this demo do:
	This is a golang demo for starrocks client, you can test basic function such as
	connection, CRUD of your starrocks.

