#!/bin/python
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


from requests import Session
import base64


class LoadSession(Session):
    def rebuild_auth(self, prepared_request, response):
        """
        No code here means requests will always preserve the Authorization
        header when redirected.
        """


def main():
    """
    Stream load Demo with Standard Lib requests
    """
    username, password = 'root', ''
    headers={
        "Content-Type":  "text/html; charset=UTF-8",
        #"Content-Type":  "application/octet-stream",  # file upload
        "connection": "keep-alive",
        "max_filter_ratio": "0.2",
        "columns": "k,v",
        "column_separator": ',',
        "Expect": "100-continue",
    }
    payload = '''k1,v1\nk2,v2\nk3,v3'''
    database = 'starrocks_demo'
    tablename = 'tb1'
    api = 'http://master1:8030/api/%s/%s/_stream_load' % (database, tablename)
    session = LoadSession()
    session.auth = (username, password)
    response = session.put(url=api, headers=headers, data=payload)
    #response = session.put(url=api, headers=headers, data= open("a.csv","rb")) # file upload
    print(response.json())


if __name__ == '__main__':
    main()
