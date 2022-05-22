import requests

res = requests.get("http://chal1.hackademy.ctf/")
print(res.text)

res = requests.get("http://chal1.hackademy.ctf/robots.txt")
print(res.text)