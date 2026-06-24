/**
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
**/

// This is a Go demo for StarRocks stream load.
//
// How to use:
//
// 1. Create a table in StarRocks with any MySQL client:
//
//    CREATE TABLE `stream_test` (
//      `id`       bigint(20)  COMMENT "",
//      `id2`      bigint(20)  COMMENT "",
//      `username` varchar(32) COMMENT ""
//    ) ENGINE=OLAP
//    DUPLICATE KEY(`id`)
//    DISTRIBUTED BY HASH(`id`) BUCKETS 20;
//
// 2. Change the StarRocks cluster, db, table, and user config constants below.
//
// 3. Run the demo:
//
//    go run stream_load.go
//
//    On success you should see output similar to:
//
//    {
//        "TxnId": 27,
//        "Label": "39c25a5c-7000-496e-a98e-348a264c81de",
//        "Status": "Success",
//        "Message": "OK",
//        "NumberTotalRows": 10,
//        "NumberLoadedRows": 10,
//        ...
//    }
//
// Notes:
//
//   - A 200 HTTP status only means the BE service answered; it does NOT mean the
//     stream load succeeded. Always check the "Status" field in the response body.
//
//   - The label header is optional. Supplying a unique label gives you at-most-once
//     semantics: re-running with the same label returns "Label Already Exists".
//
//   - The StarRocks FE requires the "Expect: 100-continue" header on a stream
//     load and rejects the request with "There is no 100-continue header" when it
//     is missing. We set it explicitly. Go's net/http drives the handshake across
//     the FE->BE redirect because http.NewRequest with a bytes.Reader populates
//     req.GetBody (so the body is replayable) and http.DefaultTransport sets a
//     non-zero ExpectContinueTimeout.

package main

import (
	"bytes"
	"crypto/rand"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
)

const (
	starrocksHost     = "127.0.0.1"
	starrocksHTTPPort = 8030
	starrocksDB       = "test"
	starrocksTable    = "stream_test"
	starrocksUser     = "root"
	starrocksPassword = ""
)

func main() {
	// One row per line. The default column separator is a tab, matching the
	// id / id2 / username columns of the stream_test table above.
	var rows []string
	for i := 0; i < 10; i++ {
		rows = append(rows, "1\t10\tSimon")
	}
	payload := []byte(strings.Join(rows, "\n"))

	if err := streamLoad(payload); err != nil {
		fmt.Fprintf(os.Stderr, "stream load failed: %v\n", err)
		os.Exit(1)
	}
}

func streamLoad(content []byte) error {
	loadURL := fmt.Sprintf("http://%s:%d/api/%s/%s/_stream_load",
		starrocksHost, starrocksHTTPPort, starrocksDB, starrocksTable)

	req, err := http.NewRequest(http.MethodPut, loadURL, bytes.NewReader(content))
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}
	req.SetBasicAuth(starrocksUser, starrocksPassword)
	req.Header.Set("label", generateLabel())

	// The FE rejects a stream load that lacks this header with the error
	// "There is no 100-continue header". Go honors it because the request body is
	// replayable (http.NewRequest set req.GetBody from the bytes.Reader) and
	// http.DefaultTransport has a non-zero ExpectContinueTimeout.
	req.Header.Set("Expect", "100-continue")

	// The FE redirects the load to a BE. Go strips the Authorization header on a
	// redirect to a different host, so we re-attach it from the original request,
	// matching the redirect handling in the Java and Python demos.
	client := &http.Client{
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) > 0 {
				req.Header.Set("Authorization", via[0].Header.Get("Authorization"))
			}
			return nil
		},
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("send request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("read response: %w", err)
	}

	// statusCode 200 only means the BE service is reachable, not that the stream
	// load succeeded; inspect the "Status" field in the body to confirm.
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status %d: %s", resp.StatusCode, body)
	}

	fmt.Println(string(body))
	return nil
}

// generateLabel returns a random UUID-v4-style string suitable for a load label.
func generateLabel() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		// crypto/rand should never fail; fall back to a fixed-but-unique-enough label.
		return fmt.Sprintf("stream-load-%x", b)
	}
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // variant 10
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
