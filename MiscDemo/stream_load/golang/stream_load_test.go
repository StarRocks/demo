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
	"regexp"
	"testing"
)

func TestGenerateLabelFormat(t *testing.T) {
	uuidPattern := regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)
	seen := make(map[string]bool)
	for i := 0; i < 1000; i++ {
		label := generateLabel()
		if !uuidPattern.MatchString(label) {
			t.Fatalf("label is not a valid v4 UUID: %q", label)
		}
		if seen[label] {
			t.Fatalf("duplicate label generated: %q", label)
		}
		seen[label] = true
	}
}
