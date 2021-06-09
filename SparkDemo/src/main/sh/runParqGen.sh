#!/bin/bash
# Copyright 2021 DorisDB, Inc.
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

csv_temp="hdfs://mycluster:9002/simon_test/csv_temp_t2"
pq_path="hdfs://mycluster:9002/simon_test/t2"
total_lines=86000000
cols=155

hadoop fs -mkdir -p $csv_temp

spark-submit --name ParqGen \
--class com.dorisdb.spark.ParqGen \
--conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
--conf spark.sql.inMemoryColumnarStorage.compressed=true \
--conf spark.speculation=false  \
--master local \
--driver-memory 30g \
--executor-memory 30g \
--num-executors 1 \
--conf spark.driver.maxResultSize=4g \
--conf spark.shuffle.manager=sort \
--conf spark.shuffle.consolidateFiles=true \
--conf spark.sql.sources.partitionColumnTypeInference.enabled=false  \
--conf spark.driver.extraJavaOptions='-XX:+UseG1GC'  \
--conf spark.memory.fraction=0.6 \
./SparkParqGen.jar \
$csv_temp \
$pq_path \
$total_lines \
1000000 \
$cols
