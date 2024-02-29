# Java UDF Demo

Same as [Java UDF](https://docs.starrocks.io/en-us/latest/using_starrocks/JAVA_UDF).

## Compile code

Just generate the Jar package using maven tool.

```Bash
cd ${java_udf_project_path}
mvn package
```

You can use the pre-compiled Jar package under `./target/` directly for to test conveniently.

## Build a simple HTTP Server

Use python to build a simple HTTP Server, just run the following command under the directory containing the UDF Jar package.

```Bash
python3 -m http.server 80
```


## Create UDF functions and use them

Same as the SQL statements on [Java UDF](https://docs.starrocks.io/en-us/latest/using_starrocks/JAVA_UDF).