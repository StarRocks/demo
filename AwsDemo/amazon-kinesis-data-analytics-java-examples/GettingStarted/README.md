# Amazon Kinesis Data Analytics Examples

Just copied the GettingStarted project from repo https://github.com/aws-samples/amazon-kinesis-data-analytics-java-examples here.

And, add a CelerData sink instead of the ExampleOutputStream sink.
Then, this application will consume the data from the ExampleInputStream, and sink to CelerData cluster.

You can configure `Runtime properties` by adding a `ProducerConfigProperties` property group. And, add some key/value pairs,
such as `jdbc-url`, `load-url`, etc. Of course, you can change properties directly in the application code.

