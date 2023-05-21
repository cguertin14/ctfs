
# 978273KULS' Infrastructure Documentation (flag-7fa969573c3ff2190e892095166cf71635eca0be)
This documentation aims to offer brief guidance to successfully operate the IAM service account in G.O.D.'s AWS account.

## Installation
Install [AWS CLI version 2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html).

## Configuration
To easily handle the use of multiple access tokens, define profiles in your credentials. Begin by adding a profile for the service account like so:

`vim ~/.aws/credentials`
```
[svc_iam]
aws_access_key_id=AKIAWEKTAQZV5FUIF4MA
aws_secret_access_key=y7bgwcbH3gzba/SYhPuDlkHnuc/BX7a7yjK0hl5K
region=us-east-1
```

You can validate that the profile is functional by using the following command:

`aws sts get-caller-identity --profile svc_iam`

When successful, the command returns the user ID, the account ID and the user's ARN.

## Permissions
Execute the next command to retrieve the service account's privileges:

`aws iam get-user-policy --user-name GOD_svc_iam --policy-name GOD_IAM_Management --profile svc_iam`

## To Keep in Mind
* All AWS resources created by G.O.D. are recognizable by the prefix `GOD_` or the string `god` in their name. This allows to quickly identify G.O.D. resources when a command also returns resources created by default (e.g. roles).
* When using the IAM service to award privileges (e.g. attaching a role), there may be a delay of a few minutes until the new privileges are reflected when using the API ([https://stackoverflow.com/questions/20156043/how-long-should-i-wait-after-applying-an-aws-iam-policy-before-it-is-valid](https://stackoverflow.com/questions/20156043/how-long-should-i-wait-after-applying-an-aws-iam-policy-before-it-is-valid))

## Debug with SSH
Usage: ssh USER@HOST -i private.key -p PORT
  - Internal port: 22
  - External port: 56987

### User
The user `debug_C4A9c1cb` is used on all containers.

### Private Keys From Secrets Manager
Your private key should look like this:

```
-----BEGIN OPENSSH PRIVATE KEY-----
BASE64_PRIVATE_KEY
-----END OPENSSH PRIVATE KEY-----
```

Note: when retrieving the key, it will contain `<br>`. Replace these with new lines.

