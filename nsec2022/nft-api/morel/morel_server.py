import grpc
import morel_pb2
import morel_pb2_grpc

from concurrent import futures
from pathlib import Path
from lib import Authentication

class Morel(morel_pb2_grpc.MorelServicer):
    def AuthenticationValidator(self, request, context):
        authenticated = True
        flag = Path('config/flag.txt').read_text()

        return morel_pb2.AuthenticationAnswer(
            authenticated=authenticated, flag=flag
        )

def serve():
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10), 
        interceptors=(Authentication(),)
    )

    morel_pb2_grpc.add_MorelServicer_to_server(
        Morel(), server
    )

    server_credentials = Authentication.get_server_credentials()
    server.add_secure_port('[::]:50053', server_credentials)
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    serve()