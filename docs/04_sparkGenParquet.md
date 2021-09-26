# 04_sparkGenParquet

# Description

Simulate 3 big tables as parquet format, which can be used in broker-load, spark-load and external-hive table testings.


```
t1:
{
 int:80,
 string:167,
 timestamp:45,
 double:23,
 rows_count:130000000
}

t2:
{
 int:20,
 string:87,
 timestamp:17,
 double:31,
 rows_count:86000000
}

t3:
{
 int:1,
 string:16,
 timestamp:4,
 rows_count:340000000
}
```

# Test

```
scala> spark.sparkContext.parallelize(Seq((1,2),(3,4))).toDF("a","b").repartition(1).write.parquet("hdfs://mycluster:9002/simon_test_t1")
```

Init temp path:

```
hadoop fs -mkdir hdfs://mycluster:9002/simon_test/csv_temp
```

Run a spark-shell

```
[starrocks@stability01 bin]$ spark-shell --executor-memory 30g  --driver-memory  30g --executor-cores 5  --driver-cores 5
```

Input ':paste' in Spark-shell REPL environment followed by a 'enter hit',

paste below codes and click 'ctrl+D' to really run the codes: 

```
import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random

def genRand(s:Int = 10000): Int = {
  new Random().nextInt(s)
}

def getLine(cols:Int = 10): String ={
    val tpl = "%s\t"
    var line = ""
    for (x <- Range(0, cols, 1) ) {
      line = line + tpl.format(genRand(x + 1024))
    }
    line = line + genRand(cols + 10).toString
    line
}

def getTable(lines:Int = 10,  cols:Int = 10) :String = {
    val tpl = "%s\n"
    var table = ""
    for (x<- Range(0, lines, 1 )) {
      table = table + tpl.format( getLine(cols))
    }
    table.stripMargin('\n')
}


val csv_temp = "hdfs://mycluster:9002/simon_test/csv_temp"
val pq_path = "hdfs://mycluster:9002/simon_test/t1"
val total_lines = 130000000
var i = 1

while (i < total_lines) {
  if (i% 13 == 1) {  
    // clear
    try {
      val hadoopConf = new org.apache.hadoop.conf.Configuration()
      val hdfs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(csv_temp), hadoopConf)
      hdfs.delete(new org.apache.hadoop.fs.Path(csv_temp), true)
    } catch { case _ : Exception => { }}
    
    val data = getTable(10000, 80 + 167 + 45 + 23)  
    val dtimes = ((data+"\n" )* 100).stripSuffix("\n")
    val res = sc.parallelize(dtimes.split("\n"))
    res.saveAsTextFile(csv_temp)
  }

  spark.read.option("delimiter", "\t").csv(csv_temp)
    .repartition(1)
    .write.mode(SaveMode.Append).parquet(pq_path)
  i = i + 1000000
}
```

CSV temp files 
and 462.8 M snappy.parquet with 1000000 lines
will be generated.

```
[starrocks@stability01 ~]$ hadoop fs -du -h hdfs://mycluster:9002/simon_test/csv_temp/*
0  hdfs://mycluster:9002/simon_test/csv_temp/_SUCCESS
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00000
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00001
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00002
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00003
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00004
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00005
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00006
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00007
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00008
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00009
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00010
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00011
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00012
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00013
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00014
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00015
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00016
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00017
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00018
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00019
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00020
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00021
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00022
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00023
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00024
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00025
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00026
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00027
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00028
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00029
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00030
38.2 M  hdfs://mycluster:9002/simon_test/csv_temp/part-00031


[starrocks@stability01 ~]$ hadoop fs -text hdfs://mycluster:9002/simon_test/csv_temp/part-00031 | wc -l
31254
[starrocks@stability01 ~]$ echo "31254 * 32 " | bc
1000128


[starrocks@stability01 ~]$ hadoop fs -du -h hdfs://mycluster:9002/simon_test/t1/*
0  hdfs://mycluster:9002/simon_test/t1/_SUCCESS
128 M  hdfs://mycluster:9002/simon_test/t1/_temporary/0
462.8 M  hdfs://mycluster:9002/simon_test/t1/part-00000-f37b44ee-73b5-4d33-afe0-0efe23396e90-c000.snappy.parquet
462.8 M  hdfs://mycluster:9002/simon_test/t1/part-00000-f941438c-359a-4e2e-b27e-1359dc35450f-c000.snappy.parquet
```

Verification
- 1 parquet file contains 1000000 lines and 316 cols

```
scala> val qarq = spark.read.parquet("hdfs://mycluster:9002/simon_test/t1/part-00000-f941438c-359a-4e2e-b27e-1359dc35450f-c000.snappy.parquet")
qarq: org.apache.spark.sql.DataFrame = [_c0: string, _c1: string ... 314 more fields]

scala> qarq.count
res0: Long = 1000000

scala> qarq.schema.head
res8: org.apache.spark.sql.types.StructField = StructField(_c0,StringType,true)

scala> qarq.schema.last
res9: org.apache.spark.sql.types.StructField = StructField(_c315,StringType,true)
```

# Performing

The run.sh script

```
#!/bin/bash

csv_temp=$1
pq_path=$2
total_lines=$3
cols=$4

hadoop fs -mkdir -p $csv_temp

spark-submit --name ParqGen \
--class com.starrocks.spark.ParqGen \
--conf spark.serializer=org.apache.spark.serializer.KryoSerializer \
--conf spark.sql.inMemoryColumnarStorage.compressed=true \
--conf spark.speculation=false  \
--master local \
--driver-memory 30g \
--executor-memory 30g \
--num-executors 1 \
--conf spark.driver.maxResultSize=4g \
--conf spark.shuffle.manager=sort \
--conf spark.shuffle.consolidateFiles=true \
--conf spark.sql.sources.partitionColumnTypeInference.enabled=false  \
--conf spark.driver.extraJavaOptions='-XX:+UseG1GC'  \
--conf spark.memory.fraction=0.6 \
/home/disk1/starrocks/misc/SparkParqGen.jar \
$csv_temp \
$pq_path \
$total_lines \
10000000 \
$cols
```

Execute the run.sh to generate 3 tables:
```
nohup bash run.sh  hdfs://mycluster:9002/simon_test/csv_temp  hdfs://mycluster:9002/simon_test/t1  130000000  315 &

nohup bash run.sh  hdfs://mycluster:9002/simon_test/csv_temp_t2  hdfs://mycluster:9002/simon_test/t2  86000000  155

nohup bash run.sh  hdfs://mycluster:9002/simon_test/csv_temp_t3  hdfs://mycluster:9002/simon_test/t3  340000000  21
```

3 tables with parquet files 
- 78G in total

```
[starrocks@stability01 misc]$ hadoop fs -du -s -h  hdfs://mycluster:9002/simon_test/t2 | head
15.1 G  hdfs://mycluster:9002/simon_test/t2
[starrocks@stability01 misc]$ hadoop fs -du -s -h  hdfs://mycluster:9002/simon_test/t1 | head
53.3 G  hdfs://mycluster:9002/simon_test/t1
[starrocks@stability01 misc]$ hadoop fs -du -s -h  hdfs://mycluster:9002/simon_test/t3 | head
9.8 G  hdfs://mycluster:9002/simon_test/t3

[starrocks@stability01 misc]$ echo "15.1 + 53.3 + 9.8" |bc
78.2
```

StarRocks DDL
```
-- cols = 316
create table t1
(
_c0  INT,
_c1  INT,
_c2  INT,
_c3  INT,
_c4  INT,
_c5  INT,
_c6  INT,
_c7  INT,
_c8  INT,
_c9  INT,
_c10  INT,
_c11  INT,
_c12  INT,
_c13  INT,
_c14  INT,
_c15  INT,
_c16  INT,
_c17  INT,
_c18  INT,
_c19  INT,
_c20  INT,
_c21  INT,
_c22  INT,
_c23  INT,
_c24  INT,
_c25  INT,
_c26  INT,
_c27  INT,
_c28  INT,
_c29  INT,
_c30  INT,
_c31  INT,
_c32  INT,
_c33  INT,
_c34  INT,
_c35  INT,
_c36  INT,
_c37  INT,
_c38  INT,
_c39  INT,
_c40  INT,
_c41  INT,
_c42  INT,
_c43  INT,
_c44  INT,
_c45  INT,
_c46  INT,
_c47  INT,
_c48  INT,
_c49  INT,
_c50  INT,
_c51  INT,
_c52  INT,
_c53  INT,
_c54  INT,
_c55  INT,
_c56  INT,
_c57  INT,
_c58  INT,
_c59  INT,
_c60  INT,
_c61  INT,
_c62  INT,
_c63  INT,
_c64  INT,
_c65  INT,
_c66  INT,
_c67  INT,
_c68  INT,
_c69  INT,
_c70  INT,
_c71  INT,
_c72  INT,
_c73  INT,
_c74  INT,
_c75  INT,
_c76  INT,
_c77  INT,
_c78  INT,
_c79  INT,
_c80  VARCHAR(65533),
_c81  VARCHAR(65533),
_c82  VARCHAR(65533),
_c83  VARCHAR(65533),
_c84  VARCHAR(65533),
_c85  VARCHAR(65533),
_c86  VARCHAR(65533),
_c87  VARCHAR(65533),
_c88  VARCHAR(65533),
_c89  VARCHAR(65533),
_c90  VARCHAR(65533),
_c91  VARCHAR(65533),
_c92  VARCHAR(65533),
_c93  VARCHAR(65533),
_c94  VARCHAR(65533),
_c95  VARCHAR(65533),
_c96  VARCHAR(65533),
_c97  VARCHAR(65533),
_c98  VARCHAR(65533),
_c99  VARCHAR(65533),
_c100  VARCHAR(65533),
_c101  VARCHAR(65533),
_c102  VARCHAR(65533),
_c103  VARCHAR(65533),
_c104  VARCHAR(65533),
_c105  VARCHAR(65533),
_c106  VARCHAR(65533),
_c107  VARCHAR(65533),
_c108  VARCHAR(65533),
_c109  VARCHAR(65533),
_c110  VARCHAR(65533),
_c111  VARCHAR(65533),
_c112  VARCHAR(65533),
_c113  VARCHAR(65533),
_c114  VARCHAR(65533),
_c115  VARCHAR(65533),
_c116  VARCHAR(65533),
_c117  VARCHAR(65533),
_c118  VARCHAR(65533),
_c119  VARCHAR(65533),
_c120  VARCHAR(65533),
_c121  VARCHAR(65533),
_c122  VARCHAR(65533),
_c123  VARCHAR(65533),
_c124  VARCHAR(65533),
_c125  VARCHAR(65533),
_c126  VARCHAR(65533),
_c127  VARCHAR(65533),
_c128  VARCHAR(65533),
_c129  VARCHAR(65533),
_c130  VARCHAR(65533),
_c131  VARCHAR(65533),
_c132  VARCHAR(65533),
_c133  VARCHAR(65533),
_c134  VARCHAR(65533),
_c135  VARCHAR(65533),
_c136  VARCHAR(65533),
_c137  VARCHAR(65533),
_c138  VARCHAR(65533),
_c139  VARCHAR(65533),
_c140  VARCHAR(65533),
_c141  VARCHAR(65533),
_c142  VARCHAR(65533),
_c143  VARCHAR(65533),
_c144  VARCHAR(65533),
_c145  VARCHAR(65533),
_c146  VARCHAR(65533),
_c147  VARCHAR(65533),
_c148  VARCHAR(65533),
_c149  VARCHAR(65533),
_c150  VARCHAR(65533),
_c151  VARCHAR(65533),
_c152  VARCHAR(65533),
_c153  VARCHAR(65533),
_c154  VARCHAR(65533),
_c155  VARCHAR(65533),
_c156  VARCHAR(65533),
_c157  VARCHAR(65533),
_c158  VARCHAR(65533),
_c159  VARCHAR(65533),
_c160  VARCHAR(65533),
_c161  VARCHAR(65533),
_c162  VARCHAR(65533),
_c163  VARCHAR(65533),
_c164  VARCHAR(65533),
_c165  VARCHAR(65533),
_c166  VARCHAR(65533),
_c167  VARCHAR(65533),
_c168  VARCHAR(65533),
_c169  VARCHAR(65533),
_c170  VARCHAR(65533),
_c171  VARCHAR(65533),
_c172  VARCHAR(65533),
_c173  VARCHAR(65533),
_c174  VARCHAR(65533),
_c175  VARCHAR(65533),
_c176  VARCHAR(65533),
_c177  VARCHAR(65533),
_c178  VARCHAR(65533),
_c179  VARCHAR(65533),
_c180  VARCHAR(65533),
_c181  VARCHAR(65533),
_c182  VARCHAR(65533),
_c183  VARCHAR(65533),
_c184  VARCHAR(65533),
_c185  VARCHAR(65533),
_c186  VARCHAR(65533),
_c187  VARCHAR(65533),
_c188  VARCHAR(65533),
_c189  VARCHAR(65533),
_c190  VARCHAR(65533),
_c191  VARCHAR(65533),
_c192  VARCHAR(65533),
_c193  VARCHAR(65533),
_c194  VARCHAR(65533),
_c195  VARCHAR(65533),
_c196  VARCHAR(65533),
_c197  VARCHAR(65533),
_c198  VARCHAR(65533),
_c199  VARCHAR(65533),
_c200  VARCHAR(65533),
_c201  VARCHAR(65533),
_c202  VARCHAR(65533),
_c203  VARCHAR(65533),
_c204  VARCHAR(65533),
_c205  VARCHAR(65533),
_c206  VARCHAR(65533),
_c207  VARCHAR(65533),
_c208  VARCHAR(65533),
_c209  VARCHAR(65533),
_c210  VARCHAR(65533),
_c211  VARCHAR(65533),
_c212  VARCHAR(65533),
_c213  VARCHAR(65533),
_c214  VARCHAR(65533),
_c215  VARCHAR(65533),
_c216  VARCHAR(65533),
_c217  VARCHAR(65533),
_c218  VARCHAR(65533),
_c219  VARCHAR(65533),
_c220  VARCHAR(65533),
_c221  VARCHAR(65533),
_c222  VARCHAR(65533),
_c223  VARCHAR(65533),
_c224  VARCHAR(65533),
_c225  VARCHAR(65533),
_c226  VARCHAR(65533),
_c227  VARCHAR(65533),
_c228  VARCHAR(65533),
_c229  VARCHAR(65533),
_c230  VARCHAR(65533),
_c231  VARCHAR(65533),
_c232  VARCHAR(65533),
_c233  VARCHAR(65533),
_c234  VARCHAR(65533),
_c235  VARCHAR(65533),
_c236  VARCHAR(65533),
_c237  VARCHAR(65533),
_c238  VARCHAR(65533),
_c239  VARCHAR(65533),
_c240  VARCHAR(65533),
_c241  VARCHAR(65533),
_c242  VARCHAR(65533),
_c243  VARCHAR(65533),
_c244  VARCHAR(65533),
_c245  VARCHAR(65533),
_c246  VARCHAR(65533),
_c247  BIGINT,
_c248  BIGINT,
_c249  BIGINT,
_c250  BIGINT,
_c251  BIGINT,
_c252  BIGINT,
_c253  BIGINT,
_c254  BIGINT,
_c255  BIGINT,
_c256  BIGINT,
_c257  BIGINT,
_c258  BIGINT,
_c259  BIGINT,
_c260  BIGINT,
_c261  BIGINT,
_c262  BIGINT,
_c263  BIGINT,
_c264  BIGINT,
_c265  BIGINT,
_c266  BIGINT,
_c267  BIGINT,
_c268  BIGINT,
_c269  BIGINT,
_c270  BIGINT,
_c271  BIGINT,
_c272  BIGINT,
_c273  BIGINT,
_c274  BIGINT,
_c275  BIGINT,
_c276  BIGINT,
_c277  BIGINT,
_c278  BIGINT,
_c279  BIGINT,
_c280  BIGINT,
_c281  BIGINT,
_c282  BIGINT,
_c283  BIGINT,
_c284  BIGINT,
_c285  BIGINT,
_c286  BIGINT,
_c287  BIGINT,
_c288  BIGINT,
_c289  BIGINT,
_c290  BIGINT,
_c291  BIGINT,
_c292  DOUBLE,
_c293  DOUBLE,
_c294  DOUBLE,
_c295  DOUBLE,
_c296  DOUBLE,
_c297  DOUBLE,
_c298  DOUBLE,
_c299  DOUBLE,
_c300  DOUBLE,
_c301  DOUBLE,
_c302  DOUBLE,
_c303  DOUBLE,
_c304  DOUBLE,
_c305  DOUBLE,
_c306  DOUBLE,
_c307  DOUBLE,
_c308  DOUBLE,
_c309  DOUBLE,
_c310  DOUBLE,
_c311  DOUBLE,
_c312  DOUBLE,
_c313  DOUBLE,
_c314  DOUBLE,
_c315  DOUBLE
 ) 
duplicate key(_c0, _c1, _c2, _c3, _c4)
distributed by hash(_c0) buckets 30
;


-- cols = 156
create table t2
(
_c0  INT,
_c1  INT,
_c2  INT,
_c3  INT,
_c4  INT,
_c5  INT,
_c6  INT,
_c7  INT,
_c8  INT,
_c9  INT,
_c10  INT,
_c11  INT,
_c12  INT,
_c13  INT,
_c14  INT,
_c15  INT,
_c16  INT,
_c17  INT,
_c18  INT,
_c19  INT,
_c20  VARCHAR(65533),
_c21  VARCHAR(65533),
_c22  VARCHAR(65533),
_c23  VARCHAR(65533),
_c24  VARCHAR(65533),
_c25  VARCHAR(65533),
_c26  VARCHAR(65533),
_c27  VARCHAR(65533),
_c28  VARCHAR(65533),
_c29  VARCHAR(65533),
_c30  VARCHAR(65533),
_c31  VARCHAR(65533),
_c32  VARCHAR(65533),
_c33  VARCHAR(65533),
_c34  VARCHAR(65533),
_c35  VARCHAR(65533),
_c36  VARCHAR(65533),
_c37  VARCHAR(65533),
_c38  VARCHAR(65533),
_c39  VARCHAR(65533),
_c40  VARCHAR(65533),
_c41  VARCHAR(65533),
_c42  VARCHAR(65533),
_c43  VARCHAR(65533),
_c44  VARCHAR(65533),
_c45  VARCHAR(65533),
_c46  VARCHAR(65533),
_c47  VARCHAR(65533),
_c48  VARCHAR(65533),
_c49  VARCHAR(65533),
_c50  VARCHAR(65533),
_c51  VARCHAR(65533),
_c52  VARCHAR(65533),
_c53  VARCHAR(65533),
_c54  VARCHAR(65533),
_c55  VARCHAR(65533),
_c56  VARCHAR(65533),
_c57  VARCHAR(65533),
_c58  VARCHAR(65533),
_c59  VARCHAR(65533),
_c60  VARCHAR(65533),
_c61  VARCHAR(65533),
_c62  VARCHAR(65533),
_c63  VARCHAR(65533),
_c64  VARCHAR(65533),
_c65  VARCHAR(65533),
_c66  VARCHAR(65533),
_c67  VARCHAR(65533),
_c68  VARCHAR(65533),
_c69  VARCHAR(65533),
_c70  VARCHAR(65533),
_c71  VARCHAR(65533),
_c72  VARCHAR(65533),
_c73  VARCHAR(65533),
_c74  VARCHAR(65533),
_c75  VARCHAR(65533),
_c76  VARCHAR(65533),
_c77  VARCHAR(65533),
_c78  VARCHAR(65533),
_c79  VARCHAR(65533),
_c80  VARCHAR(65533),
_c81  VARCHAR(65533),
_c82  VARCHAR(65533),
_c83  VARCHAR(65533),
_c84  VARCHAR(65533),
_c85  VARCHAR(65533),
_c86  VARCHAR(65533),
_c87  VARCHAR(65533),
_c88  VARCHAR(65533),
_c89  VARCHAR(65533),
_c90  VARCHAR(65533),
_c91  VARCHAR(65533),
_c92  VARCHAR(65533),
_c93  VARCHAR(65533),
_c94  VARCHAR(65533),
_c95  VARCHAR(65533),
_c96  VARCHAR(65533),
_c97  VARCHAR(65533),
_c98  VARCHAR(65533),
_c99  VARCHAR(65533),
_c100  VARCHAR(65533),
_c101  VARCHAR(65533),
_c102  VARCHAR(65533),
_c103  VARCHAR(65533),
_c104  VARCHAR(65533),
_c105  VARCHAR(65533),
_c106  VARCHAR(65533),
_c107  BIGINT,
_c108  BIGINT,
_c109  BIGINT,
_c110  BIGINT,
_c111  BIGINT,
_c112  BIGINT,
_c113  BIGINT,
_c114  BIGINT,
_c115  BIGINT,
_c116  BIGINT,
_c117  BIGINT,
_c118  BIGINT,
_c119  BIGINT,
_c120  BIGINT,
_c121  BIGINT,
_c122  BIGINT,
_c123  BIGINT,
_c124  DOUBLE,
_c125  DOUBLE,
_c126  DOUBLE,
_c127  DOUBLE,
_c128  DOUBLE,
_c129  DOUBLE,
_c130  DOUBLE,
_c131  DOUBLE,
_c132  DOUBLE,
_c133  DOUBLE,
_c134  DOUBLE,
_c135  DOUBLE,
_c136  DOUBLE,
_c137  DOUBLE,
_c138  DOUBLE,
_c139  DOUBLE,
_c140  DOUBLE,
_c141  DOUBLE,
_c142  DOUBLE,
_c143  DOUBLE,
_c144  DOUBLE,
_c145  DOUBLE,
_c146  DOUBLE,
_c147  DOUBLE,
_c148  DOUBLE,
_c149  DOUBLE,
_c150  DOUBLE,
_c151  DOUBLE,
_c152  DOUBLE,
_c153  DOUBLE,
_c154  DOUBLE,
_c155  DOUBLE
 ) 
duplicate key(_c0, _c1, _c2, _c3, _c4)
distributed by hash(_c0) buckets 30
;


-- cols = 22
create table t3
(
   _c0   INT,
   _c1  STRING,
   _c2  STRING,
   _c3  STRING,
   _c4  STRING,
   _c5  STRING,
   _c6  STRING,
   _c7  STRING,
   _c8  STRING,
   _c9  STRING,
   _c10  STRING,
   _c11  STRING,
   _c12  STRING,
   _c13  STRING,
   _c14  STRING,
   _c15  STRING,
   _c16  STRING,
   _c17  BIGINT,
   _c18  BIGINT,
   _c19  BIGINT,
   _c20  BIGINT,
   _c21  BIGINT      
 ) 
duplicate key(_c0, _c1, _c2, _c3, _c4)
distributed by hash(_c0) buckets 30
;
```


# License

StarRocks/demo is under the Apache 2.0 license. See the [LICENSE](../LICENSE) file for details.