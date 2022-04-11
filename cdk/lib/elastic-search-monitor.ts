import { SecurityGroup } from "@aws-cdk/aws-ec2";
import { Schedule } from "@aws-cdk/aws-events";
import { PolicyStatement } from "@aws-cdk/aws-iam";
import { Runtime } from "@aws-cdk/aws-lambda";
import type { App } from "@aws-cdk/core";
import { CfnParameter, Duration } from "@aws-cdk/core";
import { GuScheduledLambda } from "@guardian/cdk";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import { GuVpc } from "@guardian/cdk/lib/constructs/ec2";

export class ElasticSearchMonitor extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);
    const clusterSecurityGroupId = new CfnParameter(
      this,
      "ClusterSecurityGroup",
      {
        type: "AWS::EC2::SecurityGroup::Id",
        description:
          "A security group allowing the lambda to connect to Elasticsearch on port 9200 over TCP",
      }
    );
    const networkInterfacePolicy = new PolicyStatement({
      actions: [
        "ec2:CreateNetworkInterface",
        "ec2:DescribeNetworkInterfaces",
        "ec2:DeleteNetworkInterface",
      ],
      resources: ["*"],
    });
    const ec2DetectionPolicy = new PolicyStatement({
      actions: ["ec2:DescribeInstances"],
      resources: ["*"],
    });
    const pushMetricsPolicy = new PolicyStatement({
      actions: ["cloudwatch:PutMetricData"],
      resources: ["*"],
    });
    const additionalPolicies: PolicyStatement[] = [
      networkInterfacePolicy,
      ec2DetectionPolicy,
      pushMetricsPolicy,
    ];
    const scheduledLambda = new GuScheduledLambda(this, "ScheduledLambda", {
      app: "elastic-search-monitor",
      fileName: "elastic-search-monitor.jar",
      environment: {
        TAG_QUERY_APP: "elk-es-master",
        TAG_QUERY_STACK: "deploy",
        CLUSTER_NAME: "elk",
      },
      description:
        "Monitors your Elasticsearch cluster and reports metrics to CloudWatch",
      handler: "com.gu.elasticsearchmonitor.Lambda::handler",
      monitoringConfiguration: { noMonitoring: true },
      rules: [{ schedule: Schedule.rate(Duration.minutes(1)) }],
      runtime: Runtime.JAVA_8,
      // This lambda needs access to the Deploy Tools VPC so that it can talk to Prism
      vpc: GuVpc.fromIdParameter(this, "vpc"),
      vpcSubnets: {
        subnets: GuVpc.subnetsFromParameter(this),
      },
      securityGroups: [
        SecurityGroup.fromSecurityGroupId(
          this,
          "ElasticsearchClusterSecurityGroup",
          clusterSecurityGroupId.valueAsString
        ),
      ],
    });
    additionalPolicies.map((policy) => scheduledLambda.addToRolePolicy(policy));
  }
}
