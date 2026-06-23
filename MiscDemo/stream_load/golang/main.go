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

package main

import (
	"bytes"
	"crypto/rand"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	defaultHost     = "127.0.0.1"
	defaultHTTPPort = "8030"
	defaultDB       = "db"
	defaultTable    = "test"
	defaultUser     = "root"
)

type streamLoadConfig struct {
	Host     string
	Port     string
	Database string
	Table    string
	User     string
	Password string
}

func main() {
	cfg := streamLoadConfig{
		Host:     getenv("STARROCKS_HOST", defaultHost),
		Port:     getenv("STARROCKS_HTTP_PORT", defaultHTTPPort),
		Database: getenv("STARROCKS_DB", defaultDB),
		Table:    getenv("STARROCKS_TABLE", defaultTable),
		User:     getenv("STARROCKS_USER", defaultUser),
		Password: os.Getenv("STARROCKS_PASSWORD"),
	}

	if err := streamLoad(cfg); err != nil {
		log.Fatal(err)
	}
}

func streamLoad(cfg streamLoadConfig) error {
	loadURL := fmt.Sprintf("http://%s:%s/api/%s/%s/_stream_load", cfg.Host, cfg.Port, cfg.Database, cfg.Table)
	jsonData := strings.Join([]string{
		`{"dt":"2024-01-01 00:00:00","id":"3","msg":"test_message1938","status":"success"}`,
		`{"dt":"2024-01-01 00:00:01","id":"4","msg":"another_msg1938","status":"failed"}`,
	}, "\n")

	req, err := http.NewRequest(http.MethodPut, loadURL, bytes.NewBufferString(jsonData))
	if err != nil {
		return fmt.Errorf("create request failed: %w", err)
	}

	req.SetBasicAuth(cfg.User, cfg.Password)
	req.Header.Set("Expect", "100-continue")
	req.Header.Set("format", "json")
	req.Header.Set("label", generateRandomID())
	req.Header.Set("Content-Type", "application/json")

	var redirectHost []string
	client := &http.Client{
		Timeout: 10 * time.Minute,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) == 0 {
				return nil
			}
			if auth := via[0].Header.Get("Authorization"); auth != "" {
				req.Header.Set("Authorization", auth)
			}
			if len(via) >= 10 {
				return http.ErrUseLastResponse
			}
			redirectHost = append(redirectHost, req.URL.Host)
			return nil
		},
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("send request failed: %w, redirect hosts: %s", err, strings.Join(redirectHost, ","))
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("read response failed: %w, redirect hosts: %s", err, strings.Join(redirectHost, ","))
	}

	fmt.Printf("redirect hosts: %s\nhttp status: %d\nresponse:\n%s\n", strings.Join(redirectHost, ","), resp.StatusCode, body)
	return nil
}

func generateRandomID() string {
	const charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
	segmentLengths := [5]int{8, 4, 4, 4, 12}
	result := make([]string, len(segmentLengths))

	for i, length := range segmentLengths {
		segment := make([]byte, length)
		if _, err := rand.Read(segment); err != nil {
			now := time.Now()
			return fmt.Sprintf("%x-%x-%x-%x-%x",
				now.UnixNano(),
				now.Unix()&0xffff,
				now.Unix()>>16,
				now.Unix()&0xffff,
				now.UnixNano()&0xffffff,
			)
		}

		for j := range segment {
			segment[j] = charset[int(segment[j])%len(charset)]
		}
		result[i] = string(segment)
	}

	return strings.Join(result, "-")
}

func getenv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
