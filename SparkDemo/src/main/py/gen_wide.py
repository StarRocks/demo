#!/bin/python
# Copyright (c) 2021 Beijing Dingshi Zongheng Technology Co., Ltd. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# See the License for the specific language governing permissions and
# limitations under the License.

import sys
import random
import time

def genRand(s = 10000):
    return random.randint(1,s)

def getLine(cols = 10):
    tpl = "%s\t"
    line = ""
    for x in range(int(cols) -1):
        line = line + tpl % genRand(x + 10)
    line = line + str(genRand(int(cols) + 10))
    return line
def getTable(lines = 10, cols = 10):
    tpl = "%s\n"
    table = ""
    for x in range(int(lines) ):
        table = table + tpl % getLine(cols)
    return table.strip()

def main():
    lines = sys.argv[1]
    cols = sys.argv[2]
    data = getTable(lines, cols)
    print(data)
    # f = file(fname, 'wr+')
    # f.write(data)
    # f.flush()
    # f.close()

if __name__ == '__main__':
    main()