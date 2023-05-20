#!/usr/bin/env bash

# useful packages
brew install \
	nmap \
	hydra \
	wireshark \
	jq \
	sqlmap \
	binwalk \
	powershell

# Create ctf bin directory
CTF_BIN=$HOME/ctf/bin
mkdir -p $CTF_BIN

# Add ctf bin to $PATH to .zshrc,
# if not already there
if ! grep -q 'export PATH=$PATH:$HOME/ctf/bin' $HOME/.zshrc; then
	echo 'export PATH=$PATH:$HOME/ctf/bin' >> $HOME/.zshrc
fi

# Web installations
# wget https://portswigger.net/burp/releases/download -O $CTF_BIN/burpsuite # Burp suite -> download online instead
wget https://www.benf.org/other/cfr/cfr-0.152.jar -O $CTF_BIN/cfr # Java decompiler

# Install Metasploit
curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > msfinstall
chmod 755 msfinstall && ./msfinstall && rm msfinstall

# Install ghidra
wget https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_10.1.4_build/ghidra_10.1.4_PUBLIC_20220519.zip
unzip ghidra_10.1.4_PUBLIC_20220519.zip -d $CTF_BIN
rm ghidra_10.1.4_PUBLIC_20220519.zip

# Go packages
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
go install github.com/OJ/gobuster@latest
go install github.com/ffuf/ffuf@latest
go install github.com/ropnop/kerbrute@latest

# Git repos
git clone git@github.com:sherlock-project/sherlock.git $CTF_BIN/sherlock

# Python packages
pip3 install pywhat volatility3 web3
