package se.drutt;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static se.drutt.Constants.*;

public class SpinUpServerStack extends Stack {
    public SpinUpServerStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public SpinUpServerStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Thank you https://bobbyhadz.com/blog/aws-cdk-ec2-instance-example!!
        // Adopted to Java and modified.


        // 👇 Create VPC in which we'll launch the Instance
        List<SubnetConfiguration> subnets = new ArrayList<>();
        subnets.add(SubnetConfiguration.builder()
                .name("public")
                .cidrMask(24)
                .subnetType(SubnetType.PUBLIC)
                .build());

        Vpc vpc = Vpc.Builder.create(this, PROJECT_NAME + "-vpc")
                .cidr("10.0.0.0/16")
                .natGateways(0)
                .subnetConfiguration(subnets)
                .build();


        // 👇 create Security Group for the Instance
        SecurityGroup securityGroup = SecurityGroup.Builder.create(this, PROJECT_NAME + "-security-group")
                .vpc(vpc)
                .allowAllOutbound(true)
                .description("Allow outbound traffic and incomming ssh.")
                .build();
        securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "Allow SSH from anywhere.");


        // 👇 create a Role for the EC2 Instance, and give it read only access to S3.
        List<IManagedPolicy> managedPolicies = Arrays.asList(ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess"));

        Role serverRole = Role.Builder.create(this, PROJECT_NAME + "-role")
                .assumedBy(ServicePrincipal.Builder.create("ec2.amazonaws.com").build())
                .roleName(PROJECT_NAME + "EC2")
                .description("Role for compute servers created in the " + PROJECT_NAME + ".")
                .managedPolicies(managedPolicies)
                .build();


        // 👇 create the EC2 Instance
        Instance server = Instance.Builder.create(this, PROJECT_NAME + "-EC2-instance")
                .vpc(vpc)
                .role(serverRole)
                .securityGroup(securityGroup)
                .instanceName(PROJECT_NAME + "-server-1")
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
                .machineImage(AmazonLinuxImage.Builder.create()
                        .generation(AmazonLinuxGeneration.AMAZON_LINUX_2)
                        .build())
                .keyName(KEY_PAIR_NAME)
                .build();

        // And run a command on the server to initialize it.
        server.addUserData(SERVER_INIT_COMMAND);

        CfnOutput.Builder.create(this, PROJECT_NAME + "-server-ip-output")
                .description("Server 1 IP")
                .value(server.getInstancePublicIp())
                .build();
    }
}
