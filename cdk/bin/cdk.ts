import "source-map-support/register";
import { GuRootExperimental } from "@guardian/cdk/lib/experimental/constructs";
import { ElasticSearchMonitor } from "../lib/elastic-search-monitor";

const app = new GuRootExperimental();
new ElasticSearchMonitor(app, "ElasticSearchMonitor-PROD", {
  stack: "deploy",
  stage: "PROD",
  env: {
    region: "eu-west-1",
  },
});
