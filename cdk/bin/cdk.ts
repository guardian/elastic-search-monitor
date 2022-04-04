import "source-map-support/register";
import { App } from "@aws-cdk/core";
import { ElasticSearchMonitor } from "../lib/elastic-search-monitor";

const app = new App();
new ElasticSearchMonitor(app, "ElasticSearchMonitor-PROD", {
  stack: "deploy",
  stage: "PROD",
});
