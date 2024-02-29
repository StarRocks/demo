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

import entity.BitmapValue;

/**
 * 测试用例，主要展示bitmap java对象的相关操作
 * 将bitmap对象转换成base64字符串后，可以直接将字符串对象，写入到StarRocks表的bitmap类型字段
 */
public class BitmapDemo {
    public static void main(String[] args) {
        testAdd();
//        testOr();
    }

    /**
     * bitmap add后，结果转换成base64字符串示例
     */
    private static void testAdd() {
        BitmapValue value = new BitmapValue();
        for (long i = 4000000000L; i <= 4000000000L + 100; i++) {
            value.add(i);
        }

        String base64Str = BitmapValue.parseBitmapValueToBase64(value);
        System.out.println("base64Str = " + base64Str);
    }

    /**
     * bitmap or 并与base64字符串互相转换 示例
     */
    private static void testOr() {
        BitmapValue value1 = new BitmapValue();
        value1.add(1);
        value1.add(2);
        value1.add(3);
        BitmapValue value2 = new BitmapValue();
        value2.add(3);
        value2.add(4);
        value2.add(5);

        value1.or(value2);
        //转换成base64字符串
        String base64Str = BitmapValue.parseBitmapValueToBase64(value1);
        System.out.println("base64Str = " + base64Str);

        //base64字符串转bitmap对象
        BitmapValue bitmapValue = BitmapValue.parseBase64ToBitmapValue(base64Str);
        System.out.println("bitmapValue = " + bitmapValue.contains(1));
        System.out.println("bitmapValue = " + bitmapValue.contains(2));
        System.out.println("bitmapValue = " + bitmapValue.contains(5));
        System.out.println("bitmapValue = " + bitmapValue.contains(6));
    }
}
