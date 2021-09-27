#!/bin/bash
# Copyright (c) 2021 Beijing Dingshi Zongheng Technology Co., Ltd. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# See the License for the specific language governing permissions and
# limitations under the License.

echo "Usage: $0 topicName  interval"
id=`docker ps |grep landoop| head -n 1| awk '{print $1}'`

interval=$1
lines=$2
topic=$3

echo "Sending time data to ${topic:=starrocks_t1_src} every ${interval:=15} seconds..."
while true ; do
  python ../py/demo1_data_gen.py  $lines| docker exec -i $id  kafka-console-producer --topic "${topic:=starrocks_t1_src}" --broker-list  localhost:9092
  sleep "${interval:=15}"
done