
# DorisDB Demo

[![license](docs/imgs/dorisdb.svg)](LICENSE)

This project provides a bunch of code examples in relevant occasions,
to help developers master the development process.

![banner1](docs/imgs/banner1.png)

# 0. misc demo
[MiscDemo](MiscDemo) folder involves miscellaneous code implementation in several different languages such as golang, java, nodejs, php, python and so forth.
- [dorisdb client](MiscDemo/connect/)
- [stream load](MiscDemo/stream_load/) 

# 1. spark demo

[01_sparkStreaming2DorisDB](docs/01_sparkStreaming2DorisDB.md)

```
spark streaming -> stream load -> DorisDB 
```
 
[02_sparkConnector2DorisDB](docs/02_sparkConnector2DorisDB.md)  
```
DorisDB -> spark-connector -> etl -> stream load ->  DorisDB
```

[03_sparkLoad2DorisDB](docs/03_sparkLoad2DorisDB.md)
```
Hive  -----> spark load -> spark etl ->   broker load  ->  DorisDB 
```
[04_sparkGenParquet](docs/04_sparkGenParquet.md)

> Generate Parquet Data

# 2. flink demo

[05_flinkConnector_Bean2DorisDB](docs/05_flinkConnector_Bean2DorisDB.md)
```
bean --->   flink-connector --->  DorisDB 
```
[06_flinkConnector_Json2DorisDB](docs/06_flinkConnector_Json2DorisDB.md) 
```
json   -->   flink-connector --->  DorisDB
```
[07_flinkConnector_Sql2DorisDB](docs/07_flinkConnector_Sql2DorisDB.md) 
```
flinkSql --> flin-connector -->  DorisDB 
``` 

## Chinese wiki
-> [Wiki list](docs/cn/README_cn.md) 

## License

DorisDB/demo is under the Apache 2.0 license. See the [LICENSE](./LICENSE) file for details.
