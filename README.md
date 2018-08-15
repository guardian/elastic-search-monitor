# elastic-search-monitor

Monitors your elastic search cluster and reports metrics to cloudwatch

## Running locally

* Get a janus token
* Export the following variables, adapting them for your account (this is for deploy tool)

```bash
export TagQueryApp="elk-es-master"
export TagQueryStack="deploy"
export ClusterName="elk"
```
* Then run it by using `sbt run`