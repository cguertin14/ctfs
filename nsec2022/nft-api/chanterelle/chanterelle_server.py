import grpc
import chanterelle_pb2
import chanterelle_pb2_grpc

from concurrent import futures
from pathlib import Path

class Chanterelle(chanterelle_pb2_grpc.ChanterelleServicer):
    def CAPrinter(self, request, context):
        ca = Path('config/ca.pem').read_text()
        flag = Path('config/flag.txt').read_text()

        return chanterelle_pb2.CA(ca=ca, flag=flag)

def serve():
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10)
    )

    chanterelle_pb2_grpc.add_ChanterelleServicer_to_server(
        Chanterelle(), server
    )

    server.add_insecure_port('[::]:50052')
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    serve()