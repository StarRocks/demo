# Documentation utility environment

This is not associated with any particular quickstart or tutorial, but it is 
useful when you need a default configuration including:

- One FE
- One BE
- MinIO for object storage

Some StarRocks features, for example the FILES() table function, are meant to 
be used with S3 or S3 compatible object storage. The MinIO service in this 
configuration is good for that.

## MinIO credentials

See the compose file, specifically the minio_mc service for the service account
access key and secret.

