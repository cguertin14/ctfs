#!/usr/bin/env bash

# Create ctf bin directory
CTF_BIN=$HOME/ctf/bin
mkdir -p $CTF_BIN
echo 'export PATH=$PATH:/home/charles/ctf/bin' >> $HOME/.zshrc

# Install IDA free
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

# Install cfr - yer another java decompiler
wget https://www.benf.org/other/cfr/cfr-0.152.jar -O $CTF_BIN/cfr

# Go packages
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
go install github.com/OJ/gobuster@latest
go install github.com/ffuf/ffuf@latest

# APT packages
sudo apt install -y \
	python3 \
	python3-pip \
	nmap \
	hydra \
	wireshark \
	jq \
	sqlmap \
	ncat

# Python packages
pip3 install pywhat volatility3
