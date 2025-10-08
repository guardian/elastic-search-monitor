import { GuScheduledLambda } from "@guardian/cdk";
import { GuAlarm } from "@guardian/cdk/lib/constructs/cloudwatch";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import { GuVpc } from "@guardian/cdk/lib/constructs/ec2";
import type { App } from "aws-cdk-lib";
import { CfnParameter, Duration, Size } from "aws-cdk-lib";
import { ComparisonOperator, Metric } from "aws-cdk-lib/aws-cloudwatch";
import { SecurityGroup } from "aws-cdk-lib/aws-ec2";
import { Schedule } from "aws-cdk-lib/aws-events";
import { PolicyStatement } from "aws-cdk-lib/aws-iam";
import { Runtime } from "aws-cdk-lib/aws-lambda";
import { Topic } from "aws-cdk-lib/aws-sns";
import { EmailSubscription } from "aws-cdk-lib/aws-sns-subscriptions";

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
      monitoringConfiguration: {
        toleratedErrorPercentage: 99,
        numberOfEvaluationPeriodsAboveThresholdBeforeAlarm: 30,
        snsTopicName: "devx-alerts",
      },
      rules: [{ schedule: Schedule.rate(Duration.minutes(1)) }],
      runtime: Runtime.JAVA_11,
      // This lambda needs access to the Deploy Tools VPC so that it can talk to the ELK stack
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

    const topicForElasticsearchAlerts = new Topic(this, "ElkAlertChannel", {
      displayName: `ELK alert channel for ${this.stage}`,
      topicName: `elk-alerts-${this.stage}`,
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
      alarmDescription: `Unexpected count of data nodes in ${this.stage}. Runbook: https://docs.google.com/document/d/1PuEvL7L-CTV72Jx4OmiB3y5hlMmJR7Xz-YxYWAMiGdY`,
      evaluationPeriods: 2,
      metric: metric("NumberOfDataNodes"),
      threshold: 5,
    });

    new GuAlarm(this, "MasterNodeCountAlarm", {
      ...lessThanAlarmProps,
      alarmDescription: `Unexpected count of master nodes in ${this.stage}. Runbook: https://docs.google.com/document/d/1PuEvL7L-CTV72Jx4OmiB3y5hlMmJR7Xz-YxYWAMiGdY`,
      evaluationPeriods: 2,
      metric: metric("NumberOfRespondingMastersNodes"),
      threshold: 3,
    });

    new GuAlarm(this, "ClusterStatusAlarm", {
      ...greaterThanAlarmProps,
      alarmDescription: `Unexpected cluster status in ${this.stage}. See cloudwatch metric value for current cluster status (Green = 0, Yellow = 1, Red = 2). Runbook: https://docs.google.com/document/d/1PuEvL7L-CTV72Jx4OmiB3y5hlMmJR7Xz-YxYWAMiGdY`,
      evaluationPeriods: 30, // Tolerate yellow status for 30 mins before sending an alert
      metric: metric("Status"),
      // Green = 0, Yellow = 1, Red = 2
      threshold: 0,
    });

    new GuAlarm(this, "RedClusterStatusAlarm", {
      ...greaterThanAlarmProps,
      alarmDescription: `Red cluster status in ${this.stage}. See cloudwatch metric value for current cluster status (Green = 0, Yellow = 1, Red = 2). Runbook: https://docs.google.com/document/d/1PuEvL7L-CTV72Jx4OmiB3y5hlMmJR7Xz-YxYWAMiGdY`,
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

    const lowStorageDescription = `A data node is running on less than 80% disk space.
    For more context and troubleshooting instructions, see the runbook:
    https://docs.google.com/document/d/1PuEvL7L-CTV72Jx4OmiB3y5hlMmJR7Xz-YxYWAMiGdY/edit#heading=h.8h00c65wqmv0`;

    // See: https://aws.amazon.com/ec2/instance-types/i3en/ and https://www.google.com/search?q=5000gb+in+gib&oq=5000gb+in+gib
    const totalStorage = Size.gibibytes(4657);
    const twentyPercentDiskSpaceInBytes = Math.round(
     totalStorage.toBytes() * 0.2
    );

    new GuAlarm(this, "DataNodeLowStorageAlarm", {
      ...lessThanAlarmProps,
      alarmDescription: lowStorageDescription,
      evaluationPeriods: 2,
      metric: metric("MinAvailableDiskSpace"),
      threshold: twentyPercentDiskSpaceInBytes,
    });
  }
}
