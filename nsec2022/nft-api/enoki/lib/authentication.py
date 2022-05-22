import grpc
from pathlib import Path

class Authentication(grpc.ServerInterceptor):
    def __init__(self):
        self.access_key = Path('config/access_key.txt').read_text()
        self._valid_metadata = ('rpc-auth', self.access_key)

        def deny(_, context):            
            context.abort(
                grpc.StatusCode.UNAUTHENTICATED, 
                'Access key is invalid.'
            )

        self._deny = grpc.unary_unary_rpc_method_handler(deny)

    def intercept_service(self, continuation, handler_call_details):
        meta = handler_call_details.invocation_metadata

        self.received_access_key = dict(meta)['rpc-auth']

        if meta and meta[0] == self._valid_metadata:
            return continuation(handler_call_details)
        else:
            return self._deny

    @staticmethod
    def get_server_credentials():
        return grpc.ssl_server_credentials(
            ((Path('config/ca.key').read_bytes(), 
              Path('config/ca.pem').read_bytes()),)
        )