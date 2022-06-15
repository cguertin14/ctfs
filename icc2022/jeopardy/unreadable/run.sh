#!/usr/bin/env bash
set -euo pipefail

rm -rf encryption-by-mutilple-subtitution to_encrypt.txt
git clone https://github.com/flowlord/encryption-by-mutilple-subtitution
cat plain.txt flag.txt | tr '[A-Z\n]' '[a-z ]' | sed 's/[^a-z ]//g' > to_encrypt.txt
cd encryption-by-mutilple-subtitution
# This was the latest commit while developing the challenge
#  this doesn't mean that whatever commit might have appeared after
#  necessarily fixes or modifies the approach to this challenge
git checkout 7d422e5ea9b508bff0b7617ead303d35e9d644cc
git apply ../patch
cp /usr/share/dict/american-english word_lst.txt
python3 -c 'import keylib_generator as gen; gen.mixer()'
python3 -c 'import keylib_generator as gen; gen.gen_lib_cle(1)'
python3 -c 'import MSE; print(MSE.mse_cipher(input()))' < ../to_encrypt.txt | tee ../encrypted.txt
python3 -c 'import MSE; print(MSE.mse_decipher(input()))' < ../encrypted.txt > ../decrypted.txt
cd ..
diff -sqb decrypted.txt to_encrypt.txt
rm -rf decrypted.txt encryption-by-mutilple-subtitution to_encrypt.txt
