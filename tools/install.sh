#!/usr/bin/env bash

# Create ctf bin directory
CTF_BIN=$HOME/ctf/bin
mkdir -p $CTF_BIN
echo 'export PATH=$PATH:/home/charles/ctf/bin' >> $HOME/.zshrc

# Install IDA pro
if [ ! -f $CTF_BIN/idafree-7.7/ida64 ]
then
	wget https://out7.hex-rays.com/files/idafree77_linux.run
	chmod +x idafree77_linux.run
	./idafree77_linux.run
	rm idafree77_linux.run
	ln -s $CTF_BIN/idafree-7.7/ida64 $CTF_BIN/idafree
fi

# Install Burp suite
wget https://portswigger.net/burp/releases/download -O $CTF_BIN/burpsuite

# APT packages
sudo apt install -y \
	python3 \
	python3-pip \
	nmap \
	hydra

# Python packages
pip3 install pywhat volatility3