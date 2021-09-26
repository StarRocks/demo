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


# 1000 * 10 * 64 * 20 = 1280w, 20x 10g mem, parquet 140 G, 1hour
sinkBuckets=20
pqPath="hdfs://cs01:9002/simon_test/k4_test2"
bucketsLines=1000
genDup=10
computeBuckets=20
master=yarn
cols=400
loop=6  # pow(2, loop  ), 2^6 = 64

source /home/disk1/starrocks/.bashrc
spark-submit --name ParqGen \
--class com.starrocks.spark.ParqGen \
--conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
--conf spark.sql.inMemoryColumnarStorage.compressed=true \
--conf spark.speculation=false  \
--master $master \
--driver-memory 5g \
--executor-memory 10g \
--num-executors $computeBuckets \
--conf spark.driver.maxResultSize=4g \
--conf spark.shuffle.manager=sort \
--conf spark.shuffle.consolidateFiles=true \
--conf spark.sql.sources.partitionColumnTypeInference.enabled=false  \
--conf spark.driver.extraJavaOptions='-XX:+UseG1GC'  \
--conf spark.executor.extraJavaOptions='-XX:+UseG1GC'  \
--conf spark.memory.fraction=0.6 \
/home/disk1/starrocks/jars/SparkParqGen.v3.jar \
$sinkBuckets \
$pqPath \
$bucketsLines \
$genDup \
$loop \
$computeBuckets \
$master \
$cols