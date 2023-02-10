# elastic-search-monitor

Monitors your Elasticsearch cluster and reports metrics to CloudWatch.

## Running locally

* Get Janus credentials
* Export the following variables, adapting them for your account (this is for Deploy Tools)

```bash
export TagQueryApp="elk-es-master"
export TagQueryStack="deploy"
export ClusterName="elk"
```
* Then run it by using `sbt run`

test
