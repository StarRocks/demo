
# DorisDB Demo

[![license](docs/imgs/dorisdb.svg)](LICENSE)

本项目旨在提供完整的代码demo示例，便于开发者快速掌握相关场合的开发流程

![banner1](docs/imgs/banner1.png)

# 一、spark开发

[01_sparkStreaming2DorisDB](docs/01_sparkStreaming2DorisDB.md)

```
spark streaming -> stream load -> dorisDB 
```
 
[02_sparkConnector2DorisDB](docs/02_sparkConnector2DorisDB.md)  
```
dorisDB -> spark-connector -> etl -> stream load ->  dorisDB
```

[03_sparkLoad2DorisDB](docs/03_sparkLoad2DorisDB.md)
```
Hive  -----> spark load -> spark etl ->   broker load  ->  dorisDB 
```
[04_sparkGenParquet](docs/04_sparkGenParquet.md)

> Generate Parquet Data

# 二、flink开发

[05_flinkConnector_Bean2DorisDB](docs/05_flinkConnector_Bean2DorisDB.md)
```
bean --->   flink-connector --->  dorisDB 02_flinkConnector_Bean2DorisDB
```
[06_flinkConnector_Json2DorisDB](docs/06_flinkConnector_Json2DorisDB.md) 
```
json   -->   flink-connector --->  dorisDB
```
[07_flinkConnector_Sql2DorisDB](docs/07_flinkConnector_Sql2DorisDB.md) 
```
flinkSql --> flin-connector -->  dorisDB 
``` 

## License

DorisDB/demo is under the Apache 2.0 license. See the [LICENSE](./LICENSE) file for details.
