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

package com.dorisdb.utils

import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.connectors.kafka.internals.KeyedSerializationSchemaWrapper
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer010, FlinkKafkaProducer010, KafkaDeserializationSchema}

import java.util
import java.util.{ArrayList => JArrayList, List => JList}
import java.util.Properties

import scala.collection.JavaConversions._
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.{PartitionInfo, TopicPartition}



object KfkUtil {

  // 获取kafkaConsumer source
  def getConsumerObj(topic:String, prop: Properties, startFrom:String = "subscribe") :FlinkKafkaConsumer010[String] = {
      // kafka consumer
      val myConsumer = new FlinkKafkaConsumer010[String](topic, new SimpleStringSchema(), prop)
      startFrom match {
         case "subscribe" => myConsumer.setStartFromGroupOffsets() // 默认为该规则
         case "earliest" => myConsumer.setStartFromEarliest()
         case "latest" => myConsumer.setStartFromLatest()
         case _ if Character.isDigit(startFrom.trim()(0)) => myConsumer.setStartFromTimestamp(startFrom.toInt)
         case _ => myConsumer.setStartFromGroupOffsets()
      }
    myConsumer
  }

  // 获取kafkaProducer sink
  def getProducerObj(topic:String, brokers:String):FlinkKafkaProducer010[String] ={
    val prop = new Properties()
    prop.setProperty("bootstrap.servers", brokers)
    new FlinkKafkaProducer010[String](
      topic,
      new KeyedSerializationSchemaWrapper[String](new SimpleStringSchema),
      prop)
  }

  /**
    * 记录程序的状态notify到Kafka
    *
    * @param topics
    * @param brokers
    * @param key
    * @param data
    */
  def send(topics:String, brokers:String, key:String, data: String): Unit ={
    val props = new Properties()
        props.put("bootstrap.servers", brokers)
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val prod = new KafkaProducer[String,String](props)
    try{
      prod.send(new ProducerRecord[String,String](topics, key ,  data ))
      prod.flush()
    } catch {
      case _:Exception =>
    }finally {
      prod.close()
    }
  }

  def propPut( brokers:String, groupId:String): Properties ={
    val props = new Properties()
    props.put("bootstrap.servers", brokers)
    props.put("group.id", groupId)
    props.put("enable.auto.commit", "false")
    props.put("auto.commit.interval.ms", "1000")
    props.put("session.timeout.ms", "30000")
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props
  }

}
