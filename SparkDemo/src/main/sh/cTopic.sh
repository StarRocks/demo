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

id=`docker ps |grep landoop| head -n 1| awk '{print $1}'`
action=$1
name=$2
partitions=$3
case $action in
        "create")
        docker exec -it $id kafka-topics --create --if-not-exists --partitions ${partitions:=4} --topic  "${name:=starrocks_t1_src}"  --zookeeper  localhost:2181  --replication-factor 1
        ;;
        "delete")
        docker exec -it $id kafka-topics --delete --topic  $name  --zookeeper  localhost:2181
        ;;
        *)
        echo "Usage: $0 [create|delete]  topicName partitions."
        echo "Current topics:"
        docker exec -it $id kafka-topics  --list  --zookeeper localhost:2181
esac