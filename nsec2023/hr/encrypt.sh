read -e -p "What is your username?" USERNAME
echo $USERNAME
KEY=key.pub
echo $KEY

openssl rand -base64 32 > $USERNAME.bin
openssl rsautl -encrypt -inkey $KEY -pubin -in $USERNAME.bin -out $USERNAME.bin.enc
openssl enc -aes-256-cbc -salt -in data.txt -out $USERNAME.enc -pass file:./$USERNAME.bin

echo "Please upload the file $USERNAME.bin.enc and $USERNAME.enc"

