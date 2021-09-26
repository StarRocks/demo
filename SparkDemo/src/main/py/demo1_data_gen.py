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

def genUid(s = 10000):
    return random.randint(1,s)

def getSite():
    site_scope = ['https://www.starrocks.com/', 'https://trial.starrocks.com/', 'https://docs.starrocks.com/']
    idx = random.randint(0,len(site_scope) -1 )
    return site_scope[idx]

def getTm():
    delay_jitter = random.randint(-1800, 0)
    chance = random.randint(0,3)
    return long(time.time() + delay_jitter * chance)

"""
{uid:1, site: https://www.starrocks.com/, time: 1621410635}
"""
def gen():
    data = """{ "uid":%d, "site": "%s", "time": %s } """ % (genUid(), getSite(), getTm())
    return data

def main():
    lines =  random.randint(1,long(sys.argv[1]) )
    for x in range(lines):
        data = gen()
        print(data)
    # f = file(fname, 'wr+')
    # f.write(data)
    # f.flush()
    # f.close()

if __name__ == '__main__':
    main()