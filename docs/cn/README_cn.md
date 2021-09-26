
# StarRocks Demo cn

[![license](../imgs/StarRocks.svg)](LICENSE)

本项目旨在提供完整的代码，帮助开发者快速了解各种场景的开发方法。

![banner1](../imgs/banner1.png)

# 1. spark demo

[sparkStreaming2StarRocks](01_sparkStreaming2StarRocks.md)

用spark streaming消费Kafka数据，通过stream load接口实时导入StarRocks

``` 
kafka -> spark streaming -> stream load API-> StarRocks 
```
 
[sparkConnector2StarRocks](02_sparkConnector2StarRocks.md)  

用spark connector读取StarRocks数据

```
StarRocks -> spark-connector -> etl -> stream load API ->  StarRocks
```

[sparkLoad2StarRocks](03_sparkLoad2StarRocks.md)

用Spark load导入数据到StarRocks

```
Hive  -----> spark load -> spark etl ->   broker load  ->  StarRocks 
```
[sparkGenParquet](04_sparkGenParquet.md)

parquet生成器

> Generate Parquet Data

[bitmapDict](08_userPortrait_bitmapDict.md)

用spark load导入时，构建用户画像-全局字典

```
Hive  -----> spark load (uuid=bitmap_dict(uuid))  ->  StarRocks 
```

# 2. flink demo

[flinkConnector_Bean2StarRocks](05_flinkConnector_Bean2StarRocks.md)
```
bean --->   flink-connector --->  StarRocks 
```
[flinkConnector_Json2StarRocks](06_flinkConnector_Json2StarRocks.md) 
```
json   -->   flink-connector --->  StarRocks
```
[flinkConnector_Sql2StarRocks](07_flinkConnector_Sql2StarRocks.md) 
```
flinkSql --> flin-connector -->  StarRocks 
``` 

## License

StarRocks/demo is under the Apache 2.0 license. See the [LICENSE](./LICENSE) file for details.
