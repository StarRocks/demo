// Copyright (c) 2020 Beijing Dingshi Zongheng Technology Co., Ltd. All rights reserved.
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

object ParqGen {
  // parameters
  val master = "local"
  val appName = "app_spark_gen"

  LoggerUtil.setSparkLogLevels()

  import org.apache.spark.sql.{SaveMode, SparkSession}
  import scala.util.Random

  def genRand(s:Int = 10000): Int = {
    new Random().nextInt(s)
  }

  def getLine(cols:Int = 10): String ={
      val tpl = "%s\t"
      var line = ""
      for (x <- Range(0, cols, 1) ) {
        line = line + tpl.format(genRand(x + 1024))
      }
      line = line + genRand(cols + 10).toString
      line
  }

  def getTable(lines:Int = 10,  cols:Int = 10) :String = {
      val tpl = "%s\n"
      var table = ""
      for (x<- Range(0, lines, 1 )) {
        table = table + tpl.format( getLine(cols))
      }
      table.stripMargin('\n')
  }


  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setAppName(appName)
      .setMaster(master)
      .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
    val spark = SparkSession.builder().config(conf).master(master).enableHiveSupport().getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("WARN")
    import spark.implicits._

    val csv_temp = if (args.length > 0  ) args(0) else "hdfs://mycluster:9002/simon_test/csv_temp"
    val pq_path = if (args.length > 1 ) args(1) else  "hdfs://mycluster:9002/simon_test/t1"
    val total_lines = if (args.length > 2 ) args(2).toLong else 130000000l
    val batch_lines = if (args.length > 3 ) args(3).toInt else 1000000
    val cols = if (args.length > 4 ) args(4).toInt else 80 + 167 + 45 + 23

    var i = 1
    while (i < total_lines) {
      if (i% 13 == 1) {  // real getTable() calls
        // clear
        try {
          val hadoopConf = new org.apache.hadoop.conf.Configuration()
          val hdfs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(csv_temp), hadoopConf)
          hdfs.delete(new org.apache.hadoop.fs.Path(csv_temp), true)
        } catch { case _ : Exception => { }}

        // Randomly generate 10000 rows, multiply 100 times and generate CSV file
        val data = getTable(batch_lines/100, cols)
        val dtimes = ((data+"\n" )* 100).stripSuffix("\n")
        val res = sc.parallelize(dtimes.split("\n"))
        res.saveAsTextFile(csv_temp)
      }

      // 1000000 lines csv to parquet
      spark.read.option("delimiter", "\t").csv(csv_temp)
        .repartition(1)
        .write.mode(SaveMode.Append).parquet(pq_path)
      i = i + batch_lines
    }

    spark.close()
  }
}
