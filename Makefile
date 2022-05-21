CTF_BIN = $(shell echo ${HOME}/ctf/bin)

default: # Install tools, dependencies and other goods
	bash tools/install.sh

msfconsole: ## Run msfconsole inside a docker container
	docker run --rm -it metasploitframework/metasploit-framework

burpsuite: ## Run Burp suite
	java -jar ${CTF_BIN}/burpsuite

cfr: ## Use cfr to decompile jar/apk
	java -jar ${CTF_BIN}/cfr ${FILE}

lists: ## Download lists
	wget https://github.com/brannondorsey/naive-hashcat/releases/download/data/rockyou.txt -O lists/rockyou.txt
	wget https://raw.githubusercontent.com/daviddias/node-dirbuster/master/lists/directory-list-2.3-medium.txt -O lists/directory-list-2.3-medium.txt