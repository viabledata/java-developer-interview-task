# Workflow Engine

Central workflow engine


Functional packaging constructs

#### REST API Controllers

Interface that is exposed over HTTP and define the resources for the engine

#### Application Service Code

Internal application code that uses the native Camunda API and does filtering
based on the user who is currently logged in and their team details.

Only certain Camunda REST APIs

#### Core Resources
1. Tasks
2. Process Definitions
3. Notifications
4. Shift
5. Process Instances


See swagger docs:

{env}://swagger-ui.html

If this service is talking to internal services then ensure you have added the internal CA certs as seperate certificate files 
to /ca/xxx.crt. These will be loaded as part of the boot procedure into the java keystore and trusted.

# Drone secrets

Name|Example value
---|---
dev_drone_aws_access_key_id|https://console.aws.amazon.com/iam/home?region=eu-west-2#/users/bf-it-devtest-drone?section=security_credentials
dev_drone_aws_secret_access_key|https://console.aws.amazon.com/iam/home?region=eu-west-2#/users/bf-it-devtest-drone?section=security_credentials
drone_public_token|Drone token (Global for all github repositories and environments)
env_engine_image|quay.io/ukhomeofficedigital/cop-private-workflow-engine
env_engine_name|engine
env_engine_url|engine.dev.cop.homeoffice.gov.uk, engine.staging.cop.homeoffice.gov.uk, engine.cop.homeoffice.gov.uk
env_kube_namespace_private_cop|private-cop-dev, private-cop-staging, private-cop
env_kube_server|https://kube-api-notprod.notprod.acp.homeoffice.gov.uk, https://kube-api-prod.prod.acp.homeoffice.gov.uk
env_kube_token|xxx
ginx_image|quay.io/ukhomeofficedigital/nginx-proxy
nginx_tag|latest
production_drone_aws_access_key_id|https://console.aws.amazon.com/iam/home?region=eu-west-2#/users/bf-it-prod-drone?section=security_credentials
production_drone_aws_secret_access_key|https://console.aws.amazon.com/iam/home?region=eu-west-2#/users/bf-it-prod-drone?section=security_credentials
protocol_https|https://
protocol_jdbc|jdbc:postgresql://
quay_password|xxx (Global for all repositories and environments)
quay_username|docker (Global for all repositories and environments)
slack_webhook|https://hooks.slack.com/services/xxx/yyy/zzz (Global for all repositories and environments)
staging_drone_aws_access_key_id|https://console.aws.amazon.com/iam/home?region=eu-west-2#/users/bf-it-prod-drone?section=security_credentials
staging_drone_aws_secret_access_key|https://console.aws.amazon.com/iam/home?region=eu-west-2#/users/bf-it-prod-drone?section=security_credentials
