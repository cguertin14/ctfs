CTF_BIN = $(shell echo ${HOME}/ctf/bin)

default: prereq ## Install tools, dependencies and other goods
	bash tools/install.sh

prereq: ## Prerequisites to installation
	bash tools/prerequisites.sh

burpsuite: ## Run Burp suite
	java -jar ${CTF_BIN}/burpsuite

cfr: ## Use cfr to decompile jar/apk
	java -jar ${CTF_BIN}/cfr ${FILE}

sherlock: ## Find usernames on social media
	python3 ${CTF_BIN}/sherlock/sherlock --verbose ${USERNAMES}

lists: ## Download lists
	mkdir -p lists
	wget https://github.com/brannondorsey/naive-hashcat/releases/download/data/rockyou.txt -O lists/rockyou.txt
	wget https://raw.githubusercontent.com/daviddias/node-dirbuster/master/lists/directory-list-2.3-medium.txt -O lists/directory-list-2.3-medium.txt
	wget https://raw.githubusercontent.com/danielmiessler/SecLists/master/Usernames/top-usernames-shortlist.txt -O lists/top-usernames-shortlist.txt