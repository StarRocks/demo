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

import com.alibaba.fastjson.JSON
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.{SparkConf, TaskContext}
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.{CanCommitOffsets, HasOffsetRanges, KafkaUtils, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import com.starrocks.utils.{Consts, LoggerUtil, MySrSink}
import org.joda.time.DateTime

import scala.collection.mutable.ListBuffer

object SparkStreaming2StarRocks {
  LoggerUtil.setSparkLogLevels()
  // parameters
  val topics =  "spark_demo1_src"
  val brokers =  "127.0.0.1:9092"
  val starRocksName =  "starrocks_demo"
  val tblName =  "demo1_spark_tb0"
  val userName =  "root"
  val password =  ""
  val srFe = "master1"   // fe hostname
  val port =  8030          // fe http port
  val filterRatio =  0.2
  val columns = "site,date,hour,minute,uv,uv=to_bitmap(uv)"
  val master = "local"
  val consumerGroup =  "demo1_kgid1"
  val appName = "app_spark_demo1"
  val duration =  10 // 10s window
  val partitions =   2   // computing parallelism
  val buckets =   1      // sink parallelism
  val debug = true

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setAppName(appName)
      .setMaster(master)
      .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
    val spark = SparkSession.builder().config(conf).master(master).enableHiveSupport().getOrCreate()
    val ssc = new StreamingContext(spark.sparkContext, Seconds(duration))
    ssc.sparkContext.setLogLevel("WARN")
    import spark.implicits._

    val kafkaParams = Map("bootstrap.servers" -> brokers,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> consumerGroup,
      "auto.offset.reset" -> "latest" )
    val topic = topics.split(",")
    var offsetRanges = Array.empty[OffsetRange]
    val stream = KafkaUtils.createDirectStream[String, String](ssc, PreferConsistent, Subscribe[String,String](topic,kafkaParams))

    stream.foreachRDD(rdd=>{
      if(!rdd.isEmpty()) {
        offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
        if(master.contains("local")){  // IDEA/REPL test locally
          rdd.foreachPartition { iter =>
            val o: OffsetRange = offsetRanges(TaskContext.get.partitionId)
            println(s"${o.topic} ${o.partition} ${o.fromOffset} ${o.untilOffset}")
          }
        }
        try{stream.asInstanceOf[CanCommitOffsets].commitAsync(offsetRanges)}catch {case _:Exception =>}
      }
    })

    stream.repartition(partitions).transform(rdd=>
    {
      //   {uid:1, site: https://www.starrocks.com/, time: 1621410635}
      rdd.mapPartitions(itr =>
      {
        //    ETL:
        val list = new ListBuffer[String]()
        while(itr.hasNext) {
          val jsonRawStr:String = itr.next.value()
          val jsObj = JSON.parseObject(jsonRawStr.trim.toLowerCase())
          val uid = jsObj.getInteger("uid")
          val site = jsObj.getString("site")
          val time = jsObj.getLong("time")
          val dt = new DateTime(time * 1000)
          val date = dt.toString( s"yyyy${Consts.dateSep}MM${Consts.dateSep}dd")
          val hour = dt.getHourOfDay
          val minute = dt.getMinuteOfHour
          list append Array(site, date, hour, minute,  uid).mkString(Consts.starrocksSep)
        }
        list.iterator
      })
    }).foreachRDD( rdd =>{
      rdd.repartition(buckets).foreachPartition( iter => {
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
          tblName,
          srFe,
          port,
          debug,
          debug)

        if (iter.hasNext) sink.invoke(iter.mkString("\n"))

//        println(iter.mkString("\n"))

      })

    })

    ssc.start()
    ssc.awaitTermination()
  }
}