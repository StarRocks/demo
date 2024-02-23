from pyspark.sql import SparkSession
spark = SparkSession.builder.appName("GreenTaxi").getOrCreate()

df = spark.read.parquet("/opt/spark/green_tripdata_2023-05.parquet")

df.printSchema()

df.select(df.columns[:7]).show(3)

df.writeTo("demo.nyc.greentaxis").create()
