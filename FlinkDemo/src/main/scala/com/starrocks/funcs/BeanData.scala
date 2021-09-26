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

package com.starrocks.funcs

import scala.beans.BeanProperty

class BeanData {
  // name
  @BeanProperty
  var name: String = _
  // score
  @BeanProperty
  var score: Int = _

  override def toString: String = {
    "{ name:" + this.name + ", score:" + this.score + "}"
  }
}

object BeanData{
  def of(name: String,score: Int) : BeanData = {
    val rowData = new BeanData()
    rowData.setName(name)
    rowData.setScore(score)
    rowData
  }
}
