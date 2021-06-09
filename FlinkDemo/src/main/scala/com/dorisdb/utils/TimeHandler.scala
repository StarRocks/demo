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

import java.text.SimpleDateFormat

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.mutable.ArrayBuffer

object TimeHandler {


  def date2Stamp(date:String) : Long = {
    val fm = new SimpleDateFormat("yyyy-MM-dd")
    val dt = fm.parse(date)
    val aa = fm.format(dt)
    val tim: Long = dt.getTime()
    tim/1000
  }

    def plusMinAfterNow(plusMinutes: Int): String = {
      val datetime: DateTime = new DateTime()
      datetime.plusMinutes(plusMinutes).toString("yyyyMMddHHmm")
    }

    def minusDayBeforeNow(minusDay: Int, sep:String = "-"): String ={
      val datetime: DateTime = new DateTime()
      datetime.minusDays(minusDay).toString(s"YYYY${sep}MM${sep}dd")
    }

    def minusDayBeforeLongSec(minusDay: Int, time:Long, sep:String = "-"): String ={
      val datetime: DateTime = new DateTime(time * 1000L)
      datetime.minusDays(minusDay).toString(s"YYYY${sep}MM${sep}dd")
    }

    def getNowLongSec(): Long ={
      getNowLongMs() /1000
    }

    def getNowLongMs(): Long ={
      val datetime: DateTime = new DateTime()
      datetime.getMillis
    }

    def changeTimeFromLongMilisec(time: Long, dateFormat: String): String = {

      val date = new DateTime(time)
      date.toString(dateFormat)
    }

    def getDateStrFromLongSec(time: Long, sep:String = "-"): String ={
      changeTimeFromLongMilisec(time * 1000, s"yyyy${sep}MM${sep}dd")
    }

    def getHourIntFromLongSec(time: Long): Int ={
      new DateTime(time * 1000).getHourOfDay
    }

    def getHourIntFromNow():Int ={
      new DateTime().getHourOfDay()
    }

    def getMinuteIntFromNow(): Int ={
      new DateTime().getMinuteOfHour
    }

    def getMinuteIntFromLongSec(time: Long): Int ={
      new DateTime(time * 1000).getMinuteOfHour
    }

  /**
   * 根据当前【分钟】和 【批次间隔】获取批次号
   * @param minute  当前分钟
   * @param duration 批次间隔,默认5分钟
   * @return   0,1,2,...,11
   */
    def getMinuteBatchInHour(minute:Int , duration:Int = 5) : Int  ={
      minute / duration
    }

  def getyMdHmsTime(time : Long,sep:String = "-") : String = {

    val timeFormatter = DateTimeFormat.forPattern(s"yyyy${sep}MM${sep}dd HH:mm:ss")
    val date = new DateTime(time)
    date.toString(timeFormatter)

  }

  def minusHourBeforeLongSec(minusHour: Int, time:Long, hourFormat:String = "H", sep:String = " "): String ={
    val datetime: DateTime = new DateTime(time * 1000L)
    datetime.minusHours(minusHour).toString(s"YYYY-MM-dd${sep}${hourFormat}")
  }

  /**
   *
   * @param minusHour  减去过去几小时
   * @param time       Unix时间戳，long型
   * @param includeCurrentHour  是否包含当前小时, boolean型
   * @param sep        日期和小时之间的分隔符
   * @return           Array( (日期1，小时1),  (日期2, 小时2), ... )
   */
  def minusHourListGen(minusHour: Int,
                       time:Long,
                       includeCurrentHour:Boolean = true,
                       hourFormat:String = "H",  // 以2点为例，H为2， HH为02
                       sep:String = " "): Array[(String, String)] = {
    val res = new ArrayBuffer[(String, String)]
    var hourRange = (1 , minusHour)
    if (includeCurrentHour) hourRange = (0 , minusHour -1 )
    for ( h <-  hourRange._1 to hourRange._2) {
      val info = minusHourBeforeLongSec(h, time, hourFormat, sep).split(sep,-1)
      if (info.length.equals(2)) res.append((info(0), info(1)))
    }
    val resArr = res.toArray
    res.clear()
    resArr
  }

  def minusDayBeforeLongSec(minusDay: Int, time:Long): String ={
    val datetime: DateTime = new DateTime(time * 1000L)
    datetime.minusDays(minusDay).toString("YYYY-MM-dd")
  }


  def minusDayListGen(minusDay: Int,
                       time:Long,
                       includeCurrentDay:Boolean = true
                      ): Array[String] = {
    val res = new ArrayBuffer[String]
    var dayRange = (1 , minusDay)
    if (includeCurrentDay) dayRange = (0 , minusDay -1 )
    for ( d <-  dayRange._1 to dayRange._2) {
      val info = minusDayBeforeLongSec(d, time)
      res.append(info)
    }
    val resArr = res.toArray
    res.clear()
    resArr
  }

  def main(args: Array[String]): Unit = {
    val res = minusDayBeforeLongSec(1, 1571763723, Consts.empty)
    println(res)
    minusHourListGen(24,getNowLongSec(), true, "HH") foreach println
  }
}
