#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
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
"""

import subprocess
import time


class StarRocksClient(object):

    def __init__(self, host, port, database, columns, sep,
                username, password, filename, table, timeout):
        self.filename = filename
        self.table = table
        self.columns = columns
        self.sep = sep
        self.host = host
        self.port = port
        self.database = database
        self.user = username
        self.password = password
        self.timeout = timeout

    def get_label(self):        
        t = time.time().__str__().replace(".", "_")
        return '_'.join([self.database,self.table, t])

    def load(self):
        label = self.get_label()
        cmd = "curl"
        param_location = "--location-trusted"
        param_user = "%s:%s" % (self.user, self.password)
        param_file = "%s" % self.filename
        param_url = "http://%s:%s/api/%s/%s/_stream_load" % (
            self.host, self.port, self.database, self.table
        )
        p = subprocess.Popen([
            cmd, param_location,
            "-H", 'columns: %s' %self.columns,
            "-H", "column_separator: %s" %self.sep,
            "-H", "label: %s" %self.get_label(),
            "-H", "timeout: %d" %self.timeout,
            "-u", param_user,
            "-T", param_file,
            param_url])
        p.wait()
        if p.returncode != 0:
            print """\nLoad to starrocks failed! LABEL is %s""" % (label)
        else:
            print """\nLoad to starrocks success! LABEL is %s """ % (label)
        return label


if __name__ == '__main__':

    """
    -- Stream load Demo with Linux cmd - Curl
    --
    -- StarRocks DDL: 
    CREATE TABLE `starrocks_demo`.`tb1` (
      `k` varchar(65533) NULL COMMENT "",
      `v` varchar(65533) NULL COMMENT ""
    ) ENGINE=OLAP
    DUPLICATE KEY(`k`)
    COMMENT "OLAP"
    DISTRIBUTED BY HASH(`k`) BUCKETS 1
    PROPERTIES (
        "replication_num" = "1",
        "in_memory" = "false",
        "storage_format" = "DEFAULT"
    );
    """

    # load job 1
    client1 = StarRocksClient(
        host="master1",
        port="8030",
        database="starrocks_demo",
        username="root",
        password="",
        filename="/tmp/test.csv",    # data from local file /tmp/test.csv, usage: python CurlStreamLoad.py
        table="tb1",
        columns='k,v',
        sep=",",
        timeout=86400
    )
    client1.load()

    time.sleep(1)

    # load job 2
    client2 = StarRocksClient(
        host="master1",
        port="8030",
        database="starrocks_demo",
        username="root",
        password="",
        filename="-",                  # data from stdin, usage: echo 'k1,v1\nk2,v2'| python CurlStreamLoad.py
        table="tb1",
        columns='k,v',
        sep=",",
        timeout=86400
    )
    client2.load()

