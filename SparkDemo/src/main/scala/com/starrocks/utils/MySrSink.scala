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

package com.starrocks.utils

import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.impl.client.CloseableHttpClient

class MySrSink(headers:Map[String,String],
               dbName:String,
               userName:String,
               password:String,
               tblName:String,
               hostName:String,
               port:Int = 18030,
               debug:Boolean = true, showPayLoad: Boolean = false) extends Serializable {
  val CHARSET = "UTF-8"
  val BINARY_CT = "application/octet-stream"
  val CONTENT_TYPE = "text/plain"
  var TIMEOUT = 30000

  var api = s"http://${hostName}:${port}/api/${dbName}/${tblName}/_stream_load"
  var httpClient: CloseableHttpClient = _
  var response:CloseableHttpResponse = _
  var status :Boolean = true

  def invoke(value: String): Unit = {
    httpClient = PutUtil.clientGen(userName, password)
    try {
      val res = PutUtil.put(httpClient, value, api, CONTENT_TYPE, headers, debug, showPayLoad)
      status = res._1
      httpClient = res._2
      response = res._3
    } catch {
      case ex:Exception => {
        println("### invoke ERROR:")
        ex.printStackTrace()
      }
    } finally {
      try {
        httpClient.close()
        response.close()
      } catch {
        case ex:Exception => {
          println("### http close ERROR:")
          ex.printStackTrace()
        }
      }
    }
  }
}