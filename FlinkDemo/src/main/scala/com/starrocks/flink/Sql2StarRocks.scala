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
import com.starrocks.connector.flink.table.{StarRocksDynamicTableSinkFactory, StarRocksSinkOptions}
import com.starrocks.funcs.MySource
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
import org.apache.calcite.sql.parser.SqlParser.Config
import org.apache.flink.table.planner.delegation.PlannerContext


/**
  * Demo3：
  *    - Construct TemporaryView via org.apache.flink.types.Row
  *    - FlinkSql -> flink-connector-starrocksdb -> StarRocksDB
  */
object Sql2StarRocks {
  def main(args: Array[String]): Unit = {
    // enable Blink Planner
    val env = getExecutionEnvironment()
    val settings = EnvironmentSettings.newInstance.useBlinkPlanner().inStreamingMode.build
    val streamTableEnv = StreamTableEnvironment.create(env,settings)

    val source: DataStream[Row] = env
      .addSource(new MySource())(getRowTypeInfo())
        .uid("sourceStream-uid").name("sourceStream")
        .setParallelism(1)

    val sourceTable = streamTableEnv.fromDataStream(source,'name,'score)
    streamTableEnv.createTemporaryView("sourceTable",sourceTable)

    /*
    The sink options for this demo:
    - hostname: master1
    - fe http port: 8030
    - database name: starrocksdb_demo
    - table names: demo2_flink_tb1
    - TODO: customize above args to fit your environment.
    */
    streamTableEnv.executeSql(
      """
        |CREATE TABLE testTable(
        |`name` VARCHAR,
        |`score` INT
        |) WITH (
        |'connector' = 'starrocks',
        |'jdbc-url'='jdbc:mysql://master1:9030/starrocksdb_demo',
        |'load-url'='master1:8030',
        |'database-name' = 'starrocks_demo',
        |'table-name' = 'demo2_flink_tb3',
        |'username' = 'root',
        |'password' = '',
        |'sink.buffer-flush.max-rows' = '1000000',
        |'sink.buffer-flush.max-bytes' = '300000000',
        |'sink.buffer-flush.interval-ms' = '15000',
        |'sink.max-retries' = '3',
        |'sink.properties.row_delimiter' = '\x02',
        |'sink.properties.column_separator' = '\x01',
        |'sink.properties.columns' = 'NAME, SCORE'
        |)
        |""".stripMargin
    )
    // TODO Cautions for Scala codes：
    // 1. 3x quotation marks save some careful work with escape characters, using '\x02' and  '\x01' directly.
    // 2. When concat multiple lines with double quotation marks, please use "\\x02" and "\\x01" instead, e.g. :
    //  ...
    //  + "'sink.properties.row_delimiter' = '\\x02',"
    //  + "'sink.properties.column_separator' = '\\x01' "
    //  + ...

    streamTableEnv.executeSql(
      """
        |insert into testTable select `name`,`score` from sourceTable
      """.stripMargin)


  }

  def getExecutionEnvironment():StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setMaxParallelism(3)
    env.setParallelism(3)
    env.setRestartStrategy(RestartStrategies.failureRateRestart(
      3, // failureRate
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

  def getRowTypeInfo(): RowTypeInfo = {
    new RowTypeInfo(
      TypeInformation.of(classOf[String]),TypeInformation.of(classOf[Int]))
  }

}