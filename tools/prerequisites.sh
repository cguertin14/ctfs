#!/usr/bin/env bash

# Install APT packages
sudo apt update && sudo apt install -y \
	python3 \
	python3-pip \
	curl \
	git

# Install latest version of Go
LATEST_GO_VERSION="$(curl -s https://go.dev/VERSION?m=text | head -n1)"
echo $LATEST_GO_VERSION
wget https://go.dev/dl/${LATEST_GO_VERSION}.linux-amd64.tar.gz
sudo rm -rf /usr/local/go && sudo tar -C /usr/local -xzf ${LATEST_GO_VERSION}.linux-amd64.tar.gz
rm -rf go*

if ! grep -q 'export PATH=$PATH:/usr/local/go/bin' $HOME/.zshrc; then
	echo 'export PATH=$PATH:/usr/local/go/bin' >> $HOME/.zshrc
fi
