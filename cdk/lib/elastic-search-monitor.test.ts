import "@aws-cdk/assert/jest";
import { SynthUtils } from "@aws-cdk/assert";
import { App } from "@aws-cdk/core";
import { ElasticSearchMonitor } from "./elastic-search-monitor";

describe("The ElasticSearchMonitor stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new ElasticSearchMonitor(app, "ElasticSearchMonitor", {
      stack: "deploy",
      stage: "TEST",
    });
    expect(SynthUtils.toCloudFormation(stack)).toMatchSnapshot();
  });
});
