#!/usr/bin/env bash

# Debian files
wget https://packages.microsoft.com/config/ubuntu/22.04/packages-microsoft-prod.deb
sudo dpkg -i packages-microsoft-prod.deb
rm packages-microsoft-prod.deb

# APT packages
sudo apt install -y \
	nmap \
	hydra \
	wireshark \
	jq \
	sqlmap \
	ncat \
	binwalk \
	powershell \
	checksec

# Create ctf bin directory
CTF_BIN=$HOME/ctf/bin
mkdir -p $CTF_BIN

# Add ctf bin to $PATH to .zshrc,
# if not already there
if ! grep -q 'export PATH=$PATH:$HOME/ctf/bin' $HOME/.zshrc; then
	echo 'export PATH=$PATH:$HOME/ctf/bin' >> $HOME/.zshrc
fi

# Install IDA free
if [ ! -f $CTF_BIN/idafree-7.7/ida64 ]
then
	wget https://out7.hex-rays.com/files/idafree77_linux.run
	chmod +x idafree77_linux.run
	./idafree77_linux.run --mode unattended
	rm idafree77_linux.run
	ln -s $CTF_BIN/idafree-7.7/ida64 $CTF_BIN/idafree
fi

# Web installations
wget https://portswigger.net/burp/releases/download -O $CTF_BIN/burpsuite # Burp suite
wget https://www.benf.org/other/cfr/cfr-0.152.jar -O $CTF_BIN/cfr # Java decompiler

# Install Metasploit
curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > msfinstall
chmod 755 msfinstall && ./msfinstall && rm msfinstall

# Install ghidra
wget https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_10.1.4_build/ghidra_10.1.4_PUBLIC_20220519.zip
unzip ghidra_10.1.4_PUBLIC_20220519.zip -d $CTF_BIN
rm ghidra_10.1.4_PUBLIC_20220519.zip

# Install geth (ethereum client)
curl https://gethstore.blob.core.windows.net/builds/geth-linux-amd64-1.10.17-25c9b49f.tar.gz | tar -xzf - -C $CTF_BIN
ln -s $CTF_BIN/geth-linux-amd64-1.10.17-25c9b49f/geth $CTF_BIN/geth

# Go packages
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
go install github.com/OJ/gobuster@latest
go install github.com/ffuf/ffuf@latest
go install github.com/ropnop/kerbrute@latest

# Git repos
git clone https://github.com/sherlock-project/sherlock.git $CTF_BIN/sherlock

# Python packages
sudo apt install -y python3-pywhat
