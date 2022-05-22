import requests

res = requests.post("http://chal4.hackademy.ctf/login", json={'email': 'damien', 'password': 'damien'})
print(res.text)