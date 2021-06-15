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

package com.dorisdb.spark

import com.dorisdb.utils.{Consts, LoggerUtil, MyDorisSink}
import org.apache.spark.{SparkConf, TaskContext}
import org.apache.spark.sql.SparkSession
import org.apache.doris.spark._

object SparkConnector2DorisDB {
  // parameters
  val dorisDbName =  "doris_demo"
  val tblNameSrc =  "demo1_spark_tb1"
  val tblNameDst =  "demo1_spark_tb2"
  val userName =  "root"
  val password =  ""
  val dorisFe = "master1"
  val port =  8030
  val filterRatio =  0.8
  val columns = "uid,date,hour,minute,site"
  val master = "local"
  val appName = "app_spark_demo2"
  val partitions =   2   //计算的并发度
  val buckets =   1      // 写doris的并发度
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

    val dorisSparkDF = spark.read.format("doris")
      .option("doris.table.identifier", s"${dorisDbName}.${tblNameSrc}")
      .option("doris.fenodes", s"${dorisFe}:${port}")
      .option("user", s"${userName}")
      .option("password", s"${password}")
      .load().repartition(partitions)

    dorisSparkDF.show(5, false)
    dorisSparkDF.createOrReplaceTempView("view_tb1")
    val resDf = spark.sql(
      """
        |select uid, date, hour, minute, site
        |from view_tb1
        |lateral view explode(split(uid_list_str,',')) temp_tbl as uid
        |""".stripMargin)

    resDf.show(5, false)  // 本地打印

    resDf.map( x => x.toString().replaceAll("\\[|\\]","").replace(",",Consts.dorisSep))
      .repartition(buckets).foreachPartition(
      itr => {
        val sink = new MyDorisSink(Map( //"label"->"label123"  , 使用指定的label需注意唯一性，否则会提示已经存在而无法导入
          "max_filter_ratio" -> s"${filterRatio}",
          "columns" -> columns,
          "column_separator" -> Consts.dorisSep),
          dorisDbName,
          userName,
          password,
          tblNameDst,
          dorisFe,
          port,
          debug,
          debug)
        if (itr.hasNext) sink.invoke(itr.mkString("\n"))
      }
    )

    spark.close()
  }
}
