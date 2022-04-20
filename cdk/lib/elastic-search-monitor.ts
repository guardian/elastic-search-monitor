import { ComparisonOperator, Metric } from "@aws-cdk/aws-cloudwatch";
import { SecurityGroup } from "@aws-cdk/aws-ec2";
import { Schedule } from "@aws-cdk/aws-events";
import { PolicyStatement } from "@aws-cdk/aws-iam";
import { Runtime } from "@aws-cdk/aws-lambda";
import { EmailSubscription } from "@aws-cdk/aws-sns-subscriptions";
import type { App } from "@aws-cdk/core";
import { CfnParameter, Duration } from "@aws-cdk/core";
import { GuScheduledLambda } from "@guardian/cdk";
import { GuAlarm } from "@guardian/cdk/lib/constructs/cloudwatch";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import { GuVpc } from "@guardian/cdk/lib/constructs/ec2";
import { GuSnsTopic } from "@guardian/cdk/lib/constructs/sns";

const app = "elastic-search-monitor";

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
      app,
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

    const topicForElasticsearchAlerts = new GuSnsTopic(this, "AlertChannel", {
      displayName: `Elastic search alert channel for ${this.stage}`,
      topicName: `Elastic-Search-Alerts-${this.stage}`,
      existingLogicalId: {
        logicalId: "AlertChannel",
        reason: "Migrated from CloudFormation",
      },
    });

    topicForElasticsearchAlerts.addSubscription(
      new EmailSubscription("devx.sec.ops@guardian.co.uk")
    );

    const clusterName = "elk";

    const metric = (metricName: string) => {
      return new Metric({
        metricName,
        namespace: `${this.stack}/${clusterName}`,
        dimensionsMap: {
          Cluster: clusterName,
        },
      });
    };

    const commonAlarmProps = {
      app,
      snsTopicName: topicForElasticsearchAlerts.topicName,
      period: Duration.minutes(1),
    };

    const lessThanAlarmProps = {
      ...commonAlarmProps,
      comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
      statistic: "Minimum",
    };

    const greaterThanAlarmProps = {
      ...commonAlarmProps,
      comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
      statistic: "Maximum",
    };

    new GuAlarm(this, "DataNodeCountAlarm", {
      ...lessThanAlarmProps,
      alarmDescription: `Unexpected count of data nodes in ${this.stage}`,
      evaluationPeriods: 2,
      metric: metric("NumberOfDataNodes"),
      threshold: 3,
    });

    new GuAlarm(this, "MasterNodeCountAlarm", {
      ...lessThanAlarmProps,
      alarmDescription: `Unexpected count of master nodes in ${this.stage}`,
      evaluationPeriods: 2,
      metric: metric("NumberOfRespondingMastersNodes"),
      threshold: 3,
    });

    new GuAlarm(this, "ClusterStatusAlarm", {
      ...greaterThanAlarmProps,
      alarmDescription: `Unexpected cluster status in ${this.stage}`,
      evaluationPeriods: 15,
      metric: metric("Status"),
      // Green = 0, Yellow = 1, Red = 2
      threshold: 0,
    });

    new GuAlarm(this, "RedClusterStatusAlarm", {
      ...greaterThanAlarmProps,
      alarmDescription: `Red cluster status in ${this.stage}`,
      evaluationPeriods: 1,
      metric: metric("Status"),
      // Green = 0, Yellow = 1, Red = 2
      threshold: 1,
    });

    new GuAlarm(this, "DataNodeJvmHeapUsageAlarm", {
      ...greaterThanAlarmProps,
      alarmDescription: `A data node is using too much of its heap in ${this.stage}`,
      evaluationPeriods: 2,
      metric: metric("MaxJvmHeapUsage"),
      threshold: 85,
    });

    new GuAlarm(this, "DataNodeLowStorageAlarm", {
      ...lessThanAlarmProps,
      alarmDescription: `A data node is running low on storage space in ${this.stage}`,
      evaluationPeriods: 2,
      metric: metric("MinFreeDiskSpace"),
      threshold: 500000000000,
    });
  }
}
