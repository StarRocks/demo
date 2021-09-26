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

package com.starrocks.spark

import com.starrocks.utils.{Consts, LoggerUtil, MySrSink}
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import com.starrocks.connector.spark._

object SparkConnector2StarRocks {
  // parameters
  val starRocksName =  "starrocks_demo"
  val tblNameSrc =  "demo1_spark_tb1"
  val tblNameDst =  "demo1_spark_tb2"
  val userName =  "root"
  val password =  ""
  val srFe = "master1"   // fe hostname
  val port =  8030          // fe http port
  val filterRatio =  0.2
  val columns = "uid,date,hour,minute,site"
  val master = "local"
  val appName = "app_spark_demo2"
  val partitions =   2   // computing parallelism
  val buckets =   1      // sink parallelism
  val debug = false

  LoggerUtil.setSparkLogLevels()

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf()
      .setAppName(appName)
      .setMaster(master)
      .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
    val spark = SparkSession.builder().config(conf).master(master).enableHiveSupport().getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("WARN")
    import spark.implicits._

    val starrocksSparkDF = spark.read.format("starrocks")
      .option("starrocks.table.identifier", s"${starRocksName}.${tblNameSrc}")
      .option("starrocks.fenodes", s"${srFe}:${port}")
      .option("user", s"${userName}")
      .option("password", s"${password}")
      .load().repartition(partitions)

    starrocksSparkDF.show(5, false)
    starrocksSparkDF.createOrReplaceTempView("view_tb1")
    val resDf = spark.sql(
      """
        |select uid, date, hour, minute, site
        |from view_tb1
        |lateral view explode(split(uid_list_str,',')) temp_tbl as uid
        |""".stripMargin)

    resDf.show(5, false)  // IDEA/REPL local outputs

    resDf.map( x => x.toString().replaceAll("\\[|\\]","").replace(",",Consts.starrocksSep))
      .repartition(buckets).foreachPartition(
      itr => {
        val sink = new MySrSink(Map(
          // "label"->"label123" ：
          //     1. If not customized, StarRocks randomly generates a code as the label;
          //     2. Stream-load label is 'Unique', the Stream-load with same label can be loaded only once.
          //        [Good choice]: the label can be combined with info like batch-time and TaskContext.get.partitionId().
          "max_filter_ratio" -> s"${filterRatio}",
          "columns" -> columns,
          "column_separator" -> Consts.starrocksSep),
          starRocksName,
          userName,
          password,
          tblNameDst,
          srFe,
          port,
          debug,
          debug)
        if (itr.hasNext) sink.invoke(itr.mkString("\n"))
      }
    )

    spark.close()
  }
}
