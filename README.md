# elastic-search-monitor

Monitors your Elasticsearch cluster and reports metrics to CloudWatch.

## Running locally

* Get Janus credentials
* Export the following variables, adapting them for your account (this is for Deploy Tools)

```bash
export TAG_QUERY_APP="elk-es-master"
export TAG_QUERY_STACK="deploy"
export CLUSTER_NAME="elk"
```

* Run `python3 -m http.server 8000 --directory test-data` to serve static test data
* Then run the code by using `sbt run`
