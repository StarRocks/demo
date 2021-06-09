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

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.types.Row

import scala.util.Random

/**
  * 自定义数据源
  */
class Demo3Source extends SourceFunction[Row]{

  var ct = 0
  val t = 100 // 100个ele
  val m = 100 // metric

  override def run(ctx: SourceFunction.SourceContext[Row]) :Unit ={
    while (ct < t){
      val time = System.currentTimeMillis()
      val eleList = Range(0,t)
      val metric = Int.box(Random.nextInt(m))
      val ele = eleList(ct)
      ctx.collect(Row.of(""+ ele, metric))
      println(s"ele:${ele}, metric: ${metric}")
      ct = ct +1
    }
    while(true) Thread.sleep(5000)  // 不退出程序
  }

  override def cancel(): Unit = {
    ct = 0
  }

}