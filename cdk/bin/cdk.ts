import "source-map-support/register";
import { GuRoot } from "@guardian/cdk/lib/constructs/root";
import { ElasticSearchMonitor } from "../lib/elastic-search-monitor";

const app = new GuRoot();
new ElasticSearchMonitor(app, "ElasticSearchMonitor-PROD", {
  stack: "deploy",
  stage: "PROD",
  env: {
    region: "eu-west-1",
  },
});
