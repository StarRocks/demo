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

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.{Charset, StandardCharsets}

import org.apache.commons.net.util.Base64
import org.apache.http.HttpHeaders
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{CloseableHttpResponse, HttpPut}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, DefaultConnectionKeepAliveStrategy, DefaultRedirectStrategy, HttpClientBuilder}
import org.joda.time.DateTime

object PutUtil {
  var CHARSET = "UTF-8"
  val BINARY_CT = "application/octet-stream"
  var CONTENT_TYPE = s"text/plain; charset=${CHARSET}"
  var TIMEOUT = 30000
  var userName = "root"
  var password = ""

  def basicAuthHeader( username:String,  password:String):String =  {
      val  tobeEncode:String = username + ":" + password
      val encoded = Base64.encodeBase64(tobeEncode.getBytes(StandardCharsets.UTF_8))
      val res = "Basic " + new String(encoded)
//      println("here:"+res)
      res
    }

  def clientGen(user:String = "root", passwd:String = ""): CloseableHttpClient ={
    this.userName = user
    this.password = passwd
    var httpClient: Any = null
    HttpClientBuilder.create()
      .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy)
      .setRedirectStrategy(new DefaultRedirectStrategy(){  // refer to linux cmd: curl --location-trusted
        override def isRedirectable(method: String): Boolean = {
          super.isRedirectable(method)
          true
        }
      })
    .build()
  }

  /**
    * HTTP put
    *
    * @param payload      data to send(string)
    * @param api          Stream load API
    * @param contentType  json/ binary type to PUT
    */
  def put( httpclient:CloseableHttpClient,
           payload:String,
           api:String ,
           contentType:String = CONTENT_TYPE ,
           headers: Map[String, String] = null,
           debug: Boolean = false,
           showPayLoad: Boolean = false): (Boolean,CloseableHttpClient, CloseableHttpResponse) ={
    var response:CloseableHttpResponse = null
    var status = true
    try{
      val httpPut = new HttpPut(api)
      val requestConfig = RequestConfig.custom()
        .setAuthenticationEnabled(true)
        .setCircularRedirectsAllowed(true)
        .setRedirectsEnabled(true)
        .setRelativeRedirectsAllowed(true)
        .setExpectContinueEnabled(true)
        .setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT)
        .setSocketTimeout(TIMEOUT).build()
      httpPut.setConfig(requestConfig)
      httpPut.setHeader(HttpHeaders.EXPECT,"100-continue")  // .setExpectContinueEnabled(true)
      httpPut.setHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(this.userName, this.password))  // Authorization: Basic cm9vdDo=

      if (headers != null && headers.size > 0) {
        headers.foreach(entry =>{
          httpPut.setHeader(entry._1, entry._2)
        })
      }

      val content =new StringEntity(payload, Charset.forName(CHARSET))
      content.setContentType(contentType)
      content.setContentEncoding(CHARSET)
      httpPut.setEntity(content)
      response = httpclient.execute(httpPut)
      if(debug) {
        println("### Debug: "+new DateTime())
        println(response.getStatusLine())
        val br = new BufferedReader(new InputStreamReader(response.getEntity.getContent()))
        val sb = new StringBuffer()
        var str = ""
        while( str != null){
          sb.append(str.trim)
          str = br.readLine()
        }
        httpPut.getAllHeaders.foreach(println)
        println(sb.toString)
        println("### payload: ")
        if (showPayLoad) println(payload)
      }
      status = true
      (status, httpclient, response)
    }catch {
      case ex:Exception => {
        println(s"### post err: @ ${new DateTime().toLocalDateTime()}")
        ex.printStackTrace()}
        status = false
        (status, httpclient, response)
    }
    (status, httpclient, response)
  }


  /**
   * PutUtil Object mainly handles static funcs for Spark
   *
   * PutUtil.main() runs locally can help to test putting payload into stream load API
   *    Args for this demo:
   *    - hostname: master1
   *    - fe http port: 8030
   *    - database name: starrocks_demo
   *    - table names: demo1_dup_tb1 and demo1_agg_tb2
   *    - TODO customize above args to fit your environment.
   */
  def main(args: Array[String]): Unit = {
    // duplicate table1
    // cols: date, hour, minute, name , metric
    val api = "http://master1:8030/api/starrocks_demo/demo1_dup_tb1/_stream_load"
    val payload = "20190903_11_1_tom_130\n20190903_11_2_jerry_838"
    val headers = Map(
      //"label"->"label123"
      "max_filter_ratio"->"0.2",
      "columns"->"date,hour,minute,username,visit",
      "column_separator"->"_"
    )
    put(clientGen(), payload, api, this.CONTENT_TYPE,headers, true)._3.close()

    // agg replace col table2
    // cols: id, name , metric
    val api2 = "http://master1:8030/api/starrocks_demo/demo1_agg_tb2/_stream_load"
    val payload2 = "1_tom_313\n1_tom_318"
    val headers2 = Map(
      //"label"->"label123"
      "max_filter_ratio"->"0.2",
      "columns"->"siteid,username,visit",
      "column_separator"->"_"
    )
    put(clientGen(), payload2, api2, this.CONTENT_TYPE,headers2, true)._3.close()

  }
}