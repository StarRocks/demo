
# StarRocks Demo

[![license](docs/imgs/starrocks.svg)](LICENSE)

This project provides a bunch of code examples in relevant occasions,
to help developers master the development process.

![banner1](https://github.com/StarRocks/demo/blob/master/docs/imgs/new_banner.png)

# 0. misc demo
[MiscDemo](MiscDemo) folder involves miscellaneous code implementation in several different languages such as golang, java, nodejs, php, python and so forth.
- [starrocks client](MiscDemo/connect/)
- [stream load](MiscDemo/stream_load/) 

# 1. spark demo

[sparkStreaming2StarRocks](docs/01_sparkStreaming2StarRocks.md)

```
spark streaming -> stream load -> StarRocks 
```
 
[sparkConnector2StarRocks](docs/02_sparkConnector2StarRocks.md)  
```
StarRocks -> spark-connector -> etl -> stream load ->  StarRocks
```

[sparkLoad2StarRocks](docs/03_sparkLoad2StarRocks.md)

```
Hive  -----> spark load -> spark etl ->   broker load  ->  StarRocks 
```

[sparkGenParquet](docs/04_sparkGenParquet.md)
> Generate Parquet Data

[bitmapDict](docs/08_userPortrait_bitmapDict.md)
```
Hive  -----> spark load (bitmap dict for userPortait)  ->  StarRocks 
```

# 2. flink demo

[flinkConnector_Bean2StarRocks](docs/05_flinkConnector_Bean2StarRocks.md)
```
bean --->   flink-connector --->  StarRocks 
```
[flinkConnector_Json2StarRocks](docs/06_flinkConnector_Json2StarRocks.md) 
```
json   -->   flink-connector --->  StarRocks
```
[flinkConnector_Sql2StarRocks](docs/07_flinkConnector_Sql2StarRocks.md) 
```
flinkSql --> flink-connector -->  StarRocks 
```

## License

StarRocks/demo is under the Apache 2.0 license. See the [LICENSE](./LICENSE) file for details.
