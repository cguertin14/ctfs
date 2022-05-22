import grpc
import enoki_pb2
import enoki_pb2_grpc

from concurrent import futures
from pathlib import Path
from lib import Authentication
from lib import Database

class Enoki(enoki_pb2_grpc.EnokiServicer):
    ###########################################################################
    # WALLET
    ###########################################################################
    def WalletCreator(self, request, context):
        db = Database()

        if db.username_exists(request.username):
            return enoki_pb2.WalletCreated()
            
        id_wallet = db.create_wallet(request.username, request.password)

        return enoki_pb2.WalletCreated(
            id_wallet=id_wallet, 
            message='And on the eighth day, money was created out of thin air.'
        )

    def UsernameChanger(self, request, context):
        db = Database()

        if not db.authenticate(request) \
           or db.username_exists(request.username):
            return enoki_pb2.UsernameChanged()

        db.change_username(request.creds.username, request.username)

        return enoki_pb2.UsernameChanged(
            username=request.username, 
            message='Username has been successfully changed.'
        )

    def WalletsViewer(self, request, context):
        db = Database()

        if not db.authenticate(request):
            return enoki_pb2.WalletInfo()

        wallets = db.get_wallets()

        for wallet in wallets:
            yield enoki_pb2.WalletInfo(
                id_wallet=wallet['id_wallet'],
                username=wallet['username'],
                shiitakoin=wallet['shiitakoin'],
                level=wallet['level']
            )

    ###########################################################################
    # NFT
    ###########################################################################
    def NFTCreator(self, request, context):
        db = Database()

        # Management said that we have to save space, so I hope that your
        # NFT can fit in 100 bytes of data.
        if not db.authenticate(request) \
           or len(request.name) > 50 \
           or len(request.data) > 100 \
           or not request.data.startswith('data:image/'):
            return enoki_pb2.NFTCreated()

        id_wallet = db.get_wallet_id(request.creds.username)
        id_nft = db.create_nft(id_wallet, request.name, request.data)

        return enoki_pb2.NFTCreated(
            id_nft=id_nft, 
            message='NFT has been successfully created.'
        )

    def MyNFTsViewer(self, request, context):
        db = Database()

        if not db.authenticate(request):
            return enoki_pb2.NFTInfo()

        nfts = db.get_nfts_by_username(request.creds.username)

        for nft in nfts:
            yield enoki_pb2.NFTInfo(
                id_nft=nft['id_nft'],
                id_wallet=nft['id_wallet'],
                name=nft['name'],
                data=nft['data'],
            )

    ###########################################################################
    # NFT MINT
    ###########################################################################
    def NFTMinter(self, request, context):
        db = Database()

        if not db.authenticate(request) \
           or not db.owns_nft(request.id_nft, request.creds.username) \
           or db.already_minted(request.id_nft) \
           or request.buyout <= 0:
            return enoki_pb2.NFTMinted()

        id_minted = db.mint_nft(request.id_nft, request.buyout)

        return enoki_pb2.NFTMinted(
            id_minted=id_minted, 
            message='NFT has been successfully minted.'
        )
    
    def MintBuyer(self, request, context):
        db = Database()

        if not db.authenticate(request) \
           or not db.valid_mint_buy(request.creds.username, request.id_minted):
            return enoki_pb2.MintBought()

        id_nft = db.buy_mint(request.creds.username, request.id_minted)

        return enoki_pb2.MintBought(
            id_nft=id_nft,
            message='NFT has been successfully purchased.'
        )

    def MyMintsViewer(self, request, context):
        db = Database()

        if not db.authenticate(request):
            return enoki_pb2.MintInfo()

        mints = db.get_mints_by_username(request.creds.username)

        for mint in mints:
            yield enoki_pb2.MintInfo(
                id_minted=mint['id_minted'],
                id_nft=mint['id_nft'],
                id_wallet=mint['id_wallet'],
                name=mint['name'],
                data=mint['data'],
                buyout=mint['buyout'],
                active=mint['active'],
            )

    def MintsViewer(self, request, context):
        db = Database()

        if not db.authenticate(request):
            return enoki_pb2.MintInfo()

        mints = db.get_active_mints(request.creds.username)

        for mint in mints:
            yield enoki_pb2.MintInfo(
                id_minted=mint['id_minted'],
                id_nft=mint['id_nft'],
                id_wallet=mint['id_wallet'],
                name=mint['name'],
                data=mint['data'],
                buyout=mint['buyout'],
                active=mint['active'],
            )

    ###########################################################################
    # NFT BID
    ###########################################################################
    def MintBidder(self, request, context):
        db = Database()

        if not db.authenticate(request) \
           or not db.valid_mint_bid(
               request.id_minted, request.creds.username, request.bid
           ) \
           or db.already_bid(
               request.id_minted, request.creds.username
           ) \
           or not db.has_bid_funds(request.creds.username, request.bid) \
           or request.bid <= 0:
            return enoki_pb2.MintBidded()

        id_bid = db.bid_mint(
            request.id_minted, request.creds.username, request.bid
        )

        return enoki_pb2.MintBidded(
            id_bid=id_bid, 
            message='Bid has been successfully placed.'
        )

    def BidCanceller(self, request, context):
        db = Database()

        if not db.authenticate(request) \
           or not db.valid_bid_cancel(request.id_bid, request.creds.username):
            return enoki_pb2.BidCancelled()

        db.cancel_bid(request.id_bid, request.creds.username)

        return enoki_pb2.BidCancelled(
            id_bid=request.id_bid,
            message='Bid has been successfully cancelled.'
        )

    def MyBidsViewer(self, request, context):
        db = Database()

        if not db.authenticate(request):
            return enoki_pb2.BidInfo()

        bids = db.get_bids_by_username(request.creds.username)

        for bid in bids:
            yield enoki_pb2.BidInfo(
                id_bid=bid['id_bid'],
                id_minted=bid['id_minted'],
                id_wallet=bid['id_wallet'],
                bid=bid['bid'],
                active=bid['active']
            )

    def FlagPrinter(self, request, context):
        db = Database()
        flag = ''

        if not db.authenticate(request):
            return enoki_pb2.FlagInfo()

        funds = db.get_wallet_funds(request.creds.username)

        if funds >= 100000:
            flag = Path('config/flag.txt').read_text()

        return enoki_pb2.FlagInfo(
            flag=flag
        )

def serve():
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10), 
        interceptors=(Authentication(),)
    )

    enoki_pb2_grpc.add_EnokiServicer_to_server(
        Enoki(), server
    )

    server_credentials = Authentication.get_server_credentials()
    server.add_secure_port('[::]:50054', server_credentials)
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    serve()