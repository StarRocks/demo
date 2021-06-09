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


import com.dorisdb.utils.TimeHandler
import org.apache.flink.streaming.api.functions.source.SourceFunction

import scala.io.Source
import scala.util.Random


class Demo1Source(maxCount:Int = Int.MaxValue, intervalSec:Int = 1) extends SourceFunction[String]  {

  var done = false
  var c = 0

  def genUid( s:Int = 10000): Int = {
    new Random().nextInt(s)
  }

  def getSite():  String ={
    val site_scop = Array("https://www.dorisdb.com/", "https://trial.dorisdb.com/", "https://docs.dorisdb.com/")
    val idx = new Random().nextInt( site_scop.length -1  )
    site_scop(idx)
  }

  def getTm(): Long ={
    val dealy_jitter = new Random().nextInt(1800) * -1
    val chance = new Random().nextInt(3)
    TimeHandler.getNowLongSec() + dealy_jitter * chance
  }

  def gen() :String  = """{ "uid":%d, "site": "%s", "time": %s } """.format(genUid(), getSite(), getTm())

  override def run(sourceContext: SourceFunction.SourceContext[String]): Unit = {
    val lines = new Random().nextInt(maxCount)

    while (true) {
      for (_ <- Range(0, lines)) {
        val data = gen()
        sourceContext.collect(data)
        c = c+1
        if (c > 10) {
          Thread.sleep(1000 * intervalSec)
          c = 0
        }
      }

      Thread.sleep(1000 * intervalSec)
    }

  }

  override def cancel(): Unit = {
    done = true
  }
}
