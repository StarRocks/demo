
# DorisDB Demo cn

[![license](../imgs/dorisdb.svg)](LICENSE)

本项目旨在提供完整的代码，帮助开发者快速了解各种场景的开发方法。

![banner1](../imgs/banner1.png)

# 1. spark demo

[sparkStreaming2DorisDB](01_sparkStreaming2DorisDB.md)

用spark streaming消费Kafka数据，通过stream load接口实时导入dorisdb

``` 
kafka -> spark streaming -> stream load API-> DorisDB 
```
 
[sparkConnector2DorisDB](02_sparkConnector2DorisDB.md)  

用spark connector读取dorisdb数据

```
DorisDB -> spark-connector -> etl -> stream load API ->  DorisDB
```

[sparkLoad2DorisDB](03_sparkLoad2DorisDB.md)

用Spark load导入数据到dorisdb

```
Hive  -----> spark load -> spark etl ->   broker load  ->  DorisDB 
```
[sparkGenParquet](04_sparkGenParquet.md)

parquet生成器

> Generate Parquet Data

[bitmapDict](08_userPortrait_bitmapDict.md)

用spark load导入时，构建用户画像-全局字典

```
Hive  -----> spark load (uuid=bitmap_dict(uuid))  ->  DorisDB 
```

# 2. flink demo

[flinkConnector_Bean2DorisDB](05_flinkConnector_Bean2DorisDB.md)
```
bean --->   flink-connector --->  DorisDB 
```
[flinkConnector_Json2DorisDB](06_flinkConnector_Json2DorisDB.md) 
```
json   -->   flink-connector --->  DorisDB
```
[flinkConnector_Sql2DorisDB](07_flinkConnector_Sql2DorisDB.md) 
```
flinkSql --> flin-connector -->  DorisDB 
``` 

## License

DorisDB/demo is under the Apache 2.0 license. See the [LICENSE](./LICENSE) file for details.
