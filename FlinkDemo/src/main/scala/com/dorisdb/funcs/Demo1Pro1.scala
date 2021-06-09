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

package com.dorisdb.funcs


import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.configuration.Configuration
import com.alibaba.fastjson.JSON
import org.joda.time.DateTime
import org.apache.flink.util.Collector

import com.dorisdb.utils.Consts

class Demo1Pro1(dorisSinkBuckets:Int = 2)  extends ProcessFunction[String, (Int, String)]{
      override def open(parameters: Configuration): Unit = super.open(parameters)

      override def processElement(value: String, ctx: ProcessFunction[String, (Int, String)]#Context, out: Collector[(Int, String)]): Unit = {
          val jsObj = JSON.parseObject(value)
          val uid = jsObj.getIntValue("uid")
          val site = jsObj.getString("site")
          val time = jsObj.getLong("time")
          val dt = new DateTime(time * 1000)
          val date = dt.toString( s"yyyy${Consts.dateSep}MM${Consts.dateSep}dd")
          val hour = dt.getHourOfDay
          val minute = dt.getMinuteOfHour
          val resTp = (uid.abs%dorisSinkBuckets, Array(site, date, hour, minute,  uid).mkString(Consts.dorisSep))
          out.collect(  resTp )
      }


}
