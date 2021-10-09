// Copyright (c) 2021 Beijing Dingshi Zongheng Technology Co., Ltd. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.flink

import java.util.concurrent.TimeUnit
import com.starrocks.connector.flink.StarRocksSink
import com.starrocks.connector.flink.row.StarRocksSinkRowBuilder
import com.starrocks.connector.flink.table.StarRocksSinkOptions
import com.starrocks.funcs.BeanData
import com.starrocks.funcs.{BeanData, MySource}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.table.api.{DataTypes, EnvironmentSettings, TableSchema}
import org.apache.flink.table.api.bridge.scala.StreamTableEnvironment
import org.apache.flink.api.scala._
import org.apache.flink.table.api._
import org.apache.flink.table.api.bridge.scala._

/**
 *  Demo1
 *   - define Class BeanData,
 *   - sink to StarRocks via flink-connector-starrocks
 */
object Bean2StarRocks {

  def main(args: Array[String]): Unit = {
    val env = getExecutionEnvironment()
    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime)
    // val settings = EnvironmentSettings.newInstance.useBlinkPlanner.inStreamingMode.build
    // val streamTableEnv = StreamTableEnvironment.create(env,settings)

    val source: DataStream[BeanData] = env
      .addSource(new MySource())
      .uid("sourceStream-uid").name("sourceStream")
      .setParallelism(1)
      .map(x => {
        val name = x.getField(0).toString
        val score = x.getField(1).toString.toInt
        BeanData.of(name,score)
      })
      .uid("sourceStreamMap-uid").name("sourceStreamMap")
      .setParallelism(1)

    source
      .addSink(
        StarRocksSink.sink(
          // the table structure
          TableSchema.builder()
            .field("name", DataTypes.VARCHAR(20))
            .field("score", DataTypes.INT())
            .build(),

          /*
          The sink options for this demo:
          - hostname: master1
          - fe http port: 8030
          - database name: starrocks_demo
          - table names: demo2_flink_tb1
          - TODO: customize above args to fit your environment.
          */
          StarRocksSinkOptions.builder()
            .withProperty("jdbc-url", "jdbc:mysql://master1:9030/starrocks_demo")
            .withProperty("load-url", "master1:8030")
            .withProperty("username", "root")
            .withProperty("password", "")
            .withProperty("table-name", "demo2_flink_tb1")
            .withProperty("database-name", "starrocks_demo")
            .withProperty("sink.properties.row_delimiter","\\x02")      // in case of raw data contains common delimiter like '\n'
            .withProperty("sink.properties.column_separator","\\x01")   // in case of raw data contains common separator like '\t'
            .withProperty("sink.buffer-flush.interval-ms","5000")
            .build(),

          // set the slots with streamRowData
          new StarRocksSinkRowBuilder[BeanData]() {
            @Override
            def accept(slots: Array[Object], streamRowData: BeanData) {
              slots(0) = streamRowData.name
              slots(1) = Int.box(streamRowData.score)
            }
          }
        ))
      .uid("sourceSink-uid").name("sourceSink")
      .setParallelism(1)

    env.execute("StarRocksSink_BeanData")

  }

  def getExecutionEnvironment():StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setMaxParallelism(3)
    env.setParallelism(3)
    env.setRestartStrategy(RestartStrategies.failureRateRestart(
      3, //failureRate
      org.apache.flink.api.common.time.Time.of(5, TimeUnit.MINUTES), // failureInterval
      org.apache.flink.api.common.time.Time.of(10, TimeUnit.SECONDS) // delayInterval
    ))
    // checkpoint options
    env.enableCheckpointing(1000 * 30)
    env.getCheckpointConfig.setCheckpointingMode(CheckpointingMode.AT_LEAST_ONCE)
    env.getCheckpointConfig.setMinPauseBetweenCheckpoints(500)
    env.getCheckpointConfig.setCheckpointTimeout(1000 * 60 * 10)
    env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
    env.getCheckpointConfig.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION)
    env.getCheckpointConfig.setTolerableCheckpointFailureNumber(Integer.MAX_VALUE)
    env
  }

}