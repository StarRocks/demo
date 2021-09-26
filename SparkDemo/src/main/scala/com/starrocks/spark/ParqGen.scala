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

package com.starrocks.spark

import com.starrocks.utils.LoggerUtil
import org.apache.spark.sql.{Row, SaveMode, SparkSession}
import org.apache.spark.{Partitioner, SparkConf}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object ParqGen {
  val appName = "app_spark_gen"

  LoggerUtil.setSparkLogLevels()

  def getLine(cols:Int = 10) : List[Int] ={
      val line = new ArrayBuffer[Int]()
      val rand = new Random
      for (x <- Range(0, cols, 1) ) {
        line append rand.nextInt(x + 1024)
      }
      val res = line.toList
      line.clear()
      res
  }

  def getTable(lines:Int = 10,  cols:Int = 10) :String = {
      val arr = getLine(cols)
      val tpl = "%s\n"
      var table = ""
      val rand = new Random
      for ( _ <- Range(0, lines, 1 )) {
        table = table + tpl.format( rand.shuffle(arr).mkString("\t"))
      }
      table.stripMargin('\n')
  }


  def main(args: Array[String]): Unit = {

    // Args
    val sinkBuckets = if (args.length > 0  ) args(0).toInt else 5
    val pqPath = if (args.length > 1 ) args(1) else  "/simon_test/t1"
    val bucketsLines = if (args.length > 2 ) args(2).toInt else 1000
    val genDup = if (args.length > 3 ) args(3).toInt else 5
    val loop = if (args.length > 4 ) args(4).toInt else 5
    val computeBuckets = if (args.length > 5  ) args(5).toInt else 5
    val master = if (args.length > 6  ) args(6) else "local"
    val cols = if (args.length > 7 ) args(7).toInt else 400

    // Env
    val conf = new SparkConf()
      .setAppName(appName)
      .setMaster(master)
      .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
      .set("spark.debug.maxToStringFields","10000")
    val spark = SparkSession.builder().config(conf).master(master).getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("WARN")
    import spark.implicits._
    import org.apache.spark.sql.types._

    // base * genDup
    // Cost Tips: generates 1k x 4 lines takes 4 mins, size 17MB
    var rdd = sc.parallelize(Range(0,computeBuckets))
      .map(x => (x,x))
      .partitionBy(new Partitioner {
        override def numPartitions: Int = computeBuckets
        override def getPartition(key: Any): Int = key.toString.toInt
      })
      .mapPartitions(itr=>{
        itr.map( _ => {
          val base = getTable(bucketsLines, cols)
          (base * genDup).stripMargin('\n')
        } )
      })

    // df * loop
    for (_ <- Range(0, loop, 1)) {
      rdd = rdd union rdd
    }

    // The schema is encoded in a string
    val schemaString = Range(0, cols, 1).map(x => s"_c$x").mkString(",")

    // Generate the schema based on the string of schema
    val fields = schemaString.split(",")
      .map(fieldName => StructField(fieldName, StringType, nullable = true))
    val schema = StructType(fields)

    val rowRdd = rdd.flatMap(_.split("\n"))
      .map(_.split("\t"))
      .map(x=>{
        Row.fromSeq(x.toSeq)
      })
    val df = spark.createDataFrame(rowRdd, schema)
    df.coalesce(sinkBuckets)
      .write.mode(SaveMode.Append).parquet(pqPath)

    spark.close()
  }
}
