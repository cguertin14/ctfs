# ctfs

repository to store artifacts of CTFs I participate to

## Prerequisites

The following prerequisites will be installed automatically:
* Go
* Python 3

You can install `Java` however you want to.

## Install packages

To install useful ctf tools, simply run this command:
```bash
$ make
```

## Useful links

* [Cyberchef](https://gchq.github.io/CyberChef/)
* [MD5 password cracker](https://crackstation.net/)
* [SecLists](https://github.com/danielmiessler/SecLists)

## Useful tips

* OSINT (chal descriptions) often contain useful info (passwords, usernames, etc.)
* Always check a website's related sites (i.e.: github + gist) for info
* Start a local TCP server, on port 3000 for instance (useful for reverse shells, pings, etc.):
```bash
nc -vnlp 3000
```
