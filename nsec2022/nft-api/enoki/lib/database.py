import sqlite3
from hashlib import sha512

class Database:
    def __init__(self):
        conn = sqlite3.connect('lib/nft.db', isolation_level=None)
        conn.row_factory = sqlite3.Row
        self.cur = conn.cursor()

    ###########################################################################
    # USER
    ###########################################################################
    def authenticate(self, request):
        row = self.cur.execute(
            'SELECT username FROM wallet \
             WHERE username = ? AND password = ?', 
            (request.creds.username, request.creds.password)
        ).fetchone()

        return True if row else False

    def username_exists(self, username):
        row = self.cur.execute(
            'SELECT username FROM wallet \
             WHERE username = ?', 
            (username,)
        ).fetchone()

        return True if row else False

    def get_level_by_username(self, username):
        row = self.cur.execute(
            'SELECT level FROM wallet \
             WHERE username = ?',
            (username,)
        ).fetchone()

        return row['level']

    def change_username(self, username, new_username):
        self.cur.execute(
            'UPDATE wallet \
             SET username = ? \
             WHERE username = ?',
            (new_username, username)
        )

    ###########################################################################
    # WALLET
    ###########################################################################
    def create_wallet(self, username, password):
        # I hope nobody will realize that these shiitakoins are only a made up
        # number in a database...
        self.cur.execute(
            'INSERT INTO wallet (username, password, shiitakoin, level) \
             VALUES (?, ?, 100, 1)',
            (username, password)
        )

        return self.cur.lastrowid

    def get_wallets(self):
        rows = self.cur.execute(
            'SELECT * FROM wallet'
        ).fetchall()

        return rows

    def get_wallet_id(self, username):
        row = self.cur.execute(
            'SELECT id_wallet FROM wallet \
             WHERE username = ?', 
            (username,)
        ).fetchone()

        return row['id_wallet']

    def get_wallet_funds(self, username):
        row = self.cur.execute(
            'SELECT shiitakoin FROM wallet \
             WHERE username = ?',
            (username,)
        ).fetchone()

        return row['shiitakoin']

    def remove_funds(self, username, amount):
        # Failsafe
        self.cur.execute(
            'UPDATE wallet \
             SET shiitakoin = CASE WHEN shiitakoin - ? < 0 THEN 0 ELSE shiitakoin - ? END \
             WHERE username = ?',
            (amount, amount, username)
        )

    def add_funds(self, username, amount):
        self.cur.execute(
            'UPDATE wallet \
             SET shiitakoin = shiitakoin + ? \
             WHERE username = ?',
            (amount, username)
        )

    ###########################################################################
    # NFT
    ###########################################################################
    def create_nft(self, id_wallet, name, data):
        self.cur.execute(
            'INSERT INTO nft (id_wallet, name, data) \
             VALUES (?, ?, ?)', 
            (id_wallet, name, data)
        )

        return self.cur.lastrowid

    def owns_nft(self, id_nft, username):
        row = self.cur.execute(
            'SELECT id_nft FROM nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE id_nft = ? AND username = ?',
            (id_nft, username)
        ).fetchone()

        return True if row else False

    def get_nfts_by_username(self, username):
        rows = self.cur.execute(
            'SELECT nft.* FROM nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE username = ?', 
            (username,)
        ).fetchall()

        return rows

    def set_nft_owner(self, id_nft, id_wallet):
        self.cur.execute(
            'UPDATE nft \
             SET id_wallet = ? \
             WHERE id_nft = ?',
            (id_wallet, id_nft)
        )

    ###########################################################################
    # NFT MINT
    ###########################################################################
    def already_minted(self, id_nft):
        row = self.cur.execute(
            'SELECT id_nft FROM nft_minted \
             WHERE id_nft = ? AND active = 1', 
            (id_nft,)
        ).fetchone()

        return True if row else False

    def mint_nft(self, id_nft, buyout):
        self.cur.execute(
            'INSERT INTO nft_minted (id_nft, buyout, active) \
             VALUES (?, ?, 1)', 
            (id_nft, buyout)
        )

        return self.cur.lastrowid

    def get_mints_by_username(self, username):
        rows = self.cur.execute(
            'SELECT nft_minted.*, nft.id_wallet, name, data FROM nft_minted \
             INNER JOIN nft ON nft.id_nft = nft_minted.id_nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE username = ? AND active = 1', 
            (username,)
        ).fetchall()

        return rows

    def get_active_mints(self, username):
        rows = self.cur.execute(
            'SELECT nft_minted.*, nft.id_wallet, name, data FROM nft_minted \
             INNER JOIN nft ON nft.id_nft = nft_minted.id_nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE nft_minted.active = 1 AND username != ? AND level > ?', 
            (username, self.get_level_by_username(username))
        ).fetchall()

        return rows

    def valid_mint_buy(self, username, id_minted):
        funds = self.get_wallet_funds(username)

        # Sure, buy your own NFT if that makes you happy...
        row = self.cur.execute(
            'SELECT id_minted FROM nft_minted \
             INNER JOIN nft ON nft.id_nft = nft_minted.id_nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE id_minted = ? AND buyout <= ? AND active = 1 \
             AND (username = ? OR level > ?)', 
            (id_minted, funds, username, self.get_level_by_username(username))
        ).fetchone()

        return True if row else False

    def get_mint_buyout(self, id_minted):
        row = self.cur.execute(
            'SELECT buyout FROM nft_minted \
             WHERE id_minted = ?',
             (id_minted,)
        ).fetchone()

        return row['buyout']

    def get_mint_username(self, id_minted):
        row = self.cur.execute(
            'SELECT username FROM nft_minted \
             INNER JOIN nft ON nft.id_nft = nft_minted.id_nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE id_minted = ?',
             (id_minted,)
        ).fetchone()

        return row['username']

    def get_mint_id_nft(self, id_minted):
        row = self.cur.execute(
            'SELECT id_nft FROM nft_minted \
             WHERE id_minted = ?',
             (id_minted,)
        ).fetchone()

        return row['id_nft']

    def set_mint_inactive(self, id_minted):
        self.cur.execute(
            'UPDATE nft_minted \
             SET active = 0 \
             WHERE id_minted = ?',
            (id_minted,)
        )

    def buy_mint(self, username, id_minted):
        # Blockchain technology? I have no idea how to implement that,
        # but I promised management that the code would be using
        # military-grade cryptography.
        for c in username:
            sha512(username.encode())

        buyout = self.get_mint_buyout(id_minted)
        seller_username = self.get_mint_username(id_minted)
        id_nft = self.get_mint_id_nft(id_minted)
        id_wallet = self.get_wallet_id(username)

        self.remove_funds(username, buyout)
        self.add_funds(seller_username, buyout)

        self.set_mint_inactive(id_minted)
        self.set_nft_owner(id_nft, id_wallet)

        return id_nft

    ###########################################################################
    # NFT BID
    ###########################################################################
    def valid_mint_bid(self, id_minted, username, bid):
        # We forbid you to bid on a mint of the same wallet level as yours,
        # since those are gonna yield a terrible profit for the business.
        row = self.cur.execute(
            'SELECT id_minted FROM nft_minted \
             INNER JOIN nft ON nft.id_nft = nft_minted.id_nft \
             INNER JOIN wallet ON nft.id_wallet = wallet.id_wallet \
             WHERE id_minted = ? AND username != ? \
             AND buyout > ? AND level > ? AND active = 1', 
            (id_minted, username, bid, self.get_level_by_username(username))
        ).fetchone()

        return True if row else False

    def already_bid(self, id_minted, username):
        row = self.cur.execute(
            'SELECT id_bid FROM nft_bid \
             WHERE id_minted = ? AND id_wallet = ? AND active = 1',
            (id_minted, self.get_wallet_id(username))
        ).fetchone()

        return True if row else False

    def valid_bid_cancel(self, id_bid, username):
        row = self.cur.execute(
            'SELECT id_bid FROM nft_bid \
             INNER JOIN wallet ON nft_bid.id_wallet = wallet.id_wallet \
             WHERE id_bid = ? AND username = ? AND active = 1', 
            (id_bid, username)
        ).fetchone()

        return True if row else False

    def has_bid_funds(self, username, amount):
        row = self.cur.execute(
            'SELECT id_wallet FROM wallet \
             WHERE username = ? AND shiitakoin >= ?', 
            (username, amount)
        ).fetchone()

        return True if row else False

    def bid_mint(self, id_minted, username, bid):
        self.cur.execute(
            'INSERT INTO nft_bid (id_minted, id_wallet, bid, active) \
             VALUES (?, ?, ?, 1)', 
            (id_minted, self.get_wallet_id(username), bid)
        )

        self.remove_funds(username, bid)

        return self.cur.lastrowid

    def get_bids_by_username(self, username):
        rows = self.cur.execute(
            'SELECT nft_bid.* FROM nft_bid \
             INNER JOIN wallet ON nft_bid.id_wallet = wallet.id_wallet \
             WHERE username = ?', 
            (username,)
        ).fetchall()

        return rows

    def get_bid_amount(self, id_bid):
        row = self.cur.execute(
            'SELECT bid FROM nft_bid \
             WHERE id_bid = ?', 
            (id_bid,)
        ).fetchone()

        return row['bid']

    def cancel_bid(self, id_bid, username):
        self.cur.execute(
            'UPDATE nft_bid SET active = 0 \
             WHERE id_bid = ?',
            (id_bid,)
        )

        self.add_funds(username, self.get_bid_amount(id_bid))


###############################################################################
# SCHEMA
###############################################################################

# CREATE TABLE "wallet" (
# 	"id_wallet"	INTEGER,
# 	"username"	INTEGER,
# 	"password"	TEXT,
# 	"shiitakoin"	INTEGER,
# 	"level"	INTEGER,
# 	PRIMARY KEY("id_wallet" AUTOINCREMENT)
# )

# CREATE TABLE "nft" (
# 	"id_nft"	INTEGER,
# 	"id_wallet"	INTEGER,
# 	"name"	TEXT,
# 	"data"	TEXT,
# 	PRIMARY KEY("id_nft" AUTOINCREMENT),
# 	FOREIGN KEY("id_wallet") REFERENCES "wallet"("id_wallet")
# )

# CREATE TABLE "nft_minted" (
# 	"id_minted"	INTEGER,
# 	"id_nft"	INTEGER,
# 	"buyout"	NUMERIC,
# 	"active"	INTEGER,
# 	PRIMARY KEY("id_minted" AUTOINCREMENT),
# 	FOREIGN KEY("id_nft") REFERENCES "nft"("id_nft")
# )

# CREATE TABLE "nft_bid" (
# 	"id_bid"	INTEGER,
# 	"id_minted"	INTEGER,
# 	"id_wallet"	INTEGER,
# 	"bid"	INTEGER,
# 	"active"	INTEGER,
# 	PRIMARY KEY("id_bid" AUTOINCREMENT),
# 	FOREIGN KEY("id_minted") REFERENCES "nft_minted"("id_minted")
# )