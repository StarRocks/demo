package com.amazonaws.services.kinesisanalytics;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.starrocks.connector.flink.StarRocksSink;
import com.starrocks.connector.flink.table.sink.StarRocksSinkOptions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * A basic Kinesis Data Analytics for Java application with Kinesis data
 * streams as source and sink.
 */
public class BasicStreamingJob {
    private static final Log log = LogFactory.getLog(BasicStreamingJob.class);

    private static final String region = "us-west-2";
    private static final String inputStreamName = "ExampleInputStream";
    private static final String outputStreamName = "ExampleOutputStream";

    private static DataStream<String> createSourceFromStaticConfig(StreamExecutionEnvironment env) {
        Properties inputProperties = new Properties();
        inputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, region);
        inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

        return env.addSource(new FlinkKinesisConsumer<>(inputStreamName, new SimpleStringSchema(), inputProperties));
    }

    private static DataStream<String> createSourceFromApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
        return env.addSource(new FlinkKinesisConsumer<>(inputStreamName, new SimpleStringSchema(),
                applicationProperties.get("ConsumerConfigProperties")));
    }

    private static KinesisStreamsSink<String> createSinkFromStaticConfig() {
        Properties outputProperties = new Properties();
        outputProperties.setProperty(AWSConfigConstants.AWS_REGION, region);

        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setStreamName(outputProperties.getProperty("OUTPUT_STREAM", "ExampleOutputStream"))
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    private static KinesisStreamsSink<String> createSinkFromApplicationProperties() throws IOException {
        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(KinesisAnalyticsRuntime.getApplicationProperties().get("ProducerConfigProperties"))
                .setSerializationSchema(new SimpleStringSchema())
                .setStreamName(outputStreamName)
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        /* If you would like to use runtime configuration properties, uncomment the lines below
         * DataStream<String> input = createSourceFromApplicationProperties(env);
         */
        log.info("Create an input");
        DataStream<String> input = createSourceFromStaticConfig(env);

        /* If you would like to use runtime configuration properties, uncomment the lines below
         * input.sinkTo(createSinkFromApplicationProperties())
         */
        log.info("Start to create an sink");
        // input.sinkTo(createSinkFromStaticConfig());
        input.addSink(createCelerDataSinkFromApplicationProperties());
        log.info("Success to add a CelerData sink");

        env.execute("Flink Streaming Java API Skeleton");
    }

    private static SinkFunction<String> createCelerDataSinkFromApplicationProperties() throws IOException {
        Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
        Properties outputProperties = applicationProperties.get("ProducerConfigProperties");
        if (outputProperties == null) {
            outputProperties = new Properties();
            log.info("ProducerConfigProperties is not set. It will use default config");
        }
        else {
            log.info("ProducerConfigProperties: " + outputProperties.toString());
        }

        StarRocksSinkOptions.Builder builder = StarRocksSinkOptions.builder()
                .withProperty("jdbc-url", outputProperties.getProperty("jdbc-url", "jdbc:mysql://xxxxxxxx.cloud-app.celerdata.com:9030"))
                .withProperty("load-url", outputProperties.getProperty("load-url", "https://xxxxxxxx.cloud-app.celerdata.com"))
                .withProperty("username", outputProperties.getProperty("username", "admin"))
                .withProperty("password", outputProperties.getProperty("password", "123456"))
                .withProperty("database-name", outputProperties.getProperty("database-name", "example_db"))
                .withProperty("table-name", outputProperties.getProperty("table-name", "stock"))
                .withProperty("sink.properties.format", "json")
                .withProperty("sink.properties.jsonpaths", "[\"event_time\", \"ticker\", \"price\"]")
                // .withProperty("sink.properties.columns", "event_time, ticker, price")
                .withProperty("sink.properties.strip_outer_array", "true");
        for (Map.Entry<Object, Object> property : outputProperties.entrySet()) {
            if (StringUtils.startsWith(property.getKey().toString(), "sink.")) {
                builder.withProperty(property.getKey().toString(), property.getValue().toString());
            }
        }
        return StarRocksSink.sink(builder.build());
    }
}
