import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { ElasticSearchMonitor } from "./elastic-search-monitor";

describe("The ElasticSearchMonitor stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new ElasticSearchMonitor(app, "ElasticSearchMonitor", {
      stack: "deploy",
      stage: "TEST",
    });
    expect(Template.fromStack(stack)).toMatchSnapshot();
  });
});
