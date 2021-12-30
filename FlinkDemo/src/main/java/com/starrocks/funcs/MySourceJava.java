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
package com.starrocks.funcs;

import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.Random;

/**
 * customised source
 */
public class MySourceJava implements SourceFunction<Row> {

    private boolean isRunning = true;

    @Override
    public void run(SourceContext ctx) throws Exception {
        Random random = new Random(100);
        while (isRunning){
            long time = System.currentTimeMillis();
            ArrayList<String> eleList = new ArrayList<String>(3);
            eleList.add("stephen");eleList.add("lebron");eleList.add("kobe");
            for(String ele : eleList){
            ctx.collect(Row.of(ele, new Integer(random.nextInt())));
            }
            // collects every 5s
            Thread.sleep(5000);
        }
    }

    @Override
    public void cancel() {

    }

}
