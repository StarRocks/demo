// Copyright 2021 DorisDB, Inc.
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

package com.dorisdb.flink


import java.util.concurrent.TimeUnit
import com.dorisdb.connector.flink.DorisSink
import com.dorisdb.connector.flink.table.{DorisDynamicTableSinkFactory, DorisSinkOptions}
import com.dorisdb.funcs.MySource
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.bridge.scala.StreamTableEnvironment
import org.apache.flink.table.api._
import org.apache.flink.types.Row

/**
  * Demo3：
  *    通过org.apache.flink.types.Row构建TemporaryView
  *    FlinkSql通过flink-connector-dorisdb 写入数据到 DorisDB；
  */
object Demo3 {
  def main(args: Array[String]): Unit = {
    // DorisDynamicTableSinkFactory
    // 使用 Blink Planner 创建流表运行环境
    val env = getExecutionEnvironment()
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    val settings = EnvironmentSettings.newInstance.useBlinkPlanner.inStreamingMode.build
    val streamTableEnv = StreamTableEnvironment.create(env,settings)

    val source: DataStream[Row] = env
      .addSource(new MySource())(getRowTypeInfo())
        .uid("sourceStream-uid").name("sourceStream")
        .setParallelism(1)

    val sourceTable = streamTableEnv.fromDataStream(source,'NAME,'SCORE)
    streamTableEnv.createTemporaryView("sourceTable",sourceTable)

    streamTableEnv.executeSql(
      """
        |CREATE TABLE TestTable(
        |NAME VARCHAR,
        |SCORE INT
        |) WITH (
        |'connector' = 'doris',
        |'jdbc-url'='jdbc:mysql://master1:9030?doris_demo',
        |'load-url'='master1:8030',
        |'database-name' = 'doris_demo',
        |'table-name' = 'demo2_flink_tb1',
        |'username' = 'root',
        |'password' = '',
        |'sink.buffer-flush.max-rows' = '1000000',
        |'sink.buffer-flush.max-bytes' = '300000000',
        |'sink.buffer-flush.interval-ms' = '300000',
        |'sink.max-retries' = '3',
        |'sink.properties.row_delimiter' = '\\x02',
        |'sink.properties.column_separator' = '\\x01',
        |'sink.properties.columns' = 'NAME, SCORE'
        |)
        |""".stripMargin
    )

    streamTableEnv.executeSql(
      """
        |insert into TestTable select `NAME`,`SCORE` from sourceTable
      """.stripMargin)

//    env.execute("DorisDBSink_SQL")    // IDEA里执行时，可以注释掉本行

  }

  def getExecutionEnvironment():StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setMaxParallelism(3)
    env.setParallelism(3)
    // 失败重试
    env.setRestartStrategy(RestartStrategies.failureRateRestart(
      3, // 每个时间间隔的最大故障次数
      org.apache.flink.api.common.time.Time.of(5, TimeUnit.MINUTES), // 测量故障率的时间间隔
      org.apache.flink.api.common.time.Time.of(10, TimeUnit.SECONDS) // 延时
    ))
    // checkpoint配置
    env.enableCheckpointing(1000 * 600)
    env.getCheckpointConfig.setCheckpointingMode(CheckpointingMode.AT_LEAST_ONCE)
    env.getCheckpointConfig.setMinPauseBetweenCheckpoints(500)
    env.getCheckpointConfig.setCheckpointTimeout(1000 * 60 * 10)
    env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
    env.getCheckpointConfig.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION)
    env.getCheckpointConfig.setTolerableCheckpointFailureNumber(Integer.MAX_VALUE)
    env
  }

  def getRowTypeInfo(): RowTypeInfo = {
    new RowTypeInfo(
      TypeInformation.of(classOf[String]),TypeInformation.of(classOf[Int]))
  }

}