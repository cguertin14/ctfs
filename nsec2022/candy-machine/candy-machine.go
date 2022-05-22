package main

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	b64 "encoding/base64"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	_ "net/http/pprof"
	"os"
)

// Password for the candy machine is stored in a tmp file
func GetPassword() string {
	dat, _ := os.ReadFile("/tmp/password")
	return string(dat)
}

// Using a local key service to load the key
func GetKey() ([]byte, error) {
	resp, err := http.Get("http://127.0.0.1:11222/key")

	if err != nil {
		return nil, err
	}

	body, err := ioutil.ReadAll(resp.Body)

	if err != nil {
		return nil, err
	}

	return body[:16], nil
}

// A valid token for the candy machine must have the exact value returned by this function
func GetMagicValue() string {
	return "candykey"
}

// Check if the candy key is valid
func CheckCandyKeyValue(key string) bool {
	if GetMagicValue() != key {
		return false
	}
	return true
}

// Twice the encryption, twice the protection !
func DecodeStep1(key string) ([]byte, error) {
	aeskey, err := GetKey()

	if err != nil {
		return nil, err
	}

	keyDecoded, err := b64.URLEncoding.DecodeString(key)

	if err != nil {
		return nil, err
	}

	res, err := AESDecrypt(keyDecoded, aeskey)

	if err != nil {
		return nil, err
	}

	return res, nil
}

func DecodeStep2(key []byte) (string, error) {
	aeskey, err := GetKey()

	if err != nil {
		return "", err
	}

	res, err := AESDecrypt(key, aeskey)

	if err != nil {
		return "", err
	}

	return string(res), nil
}

// Endpoint to get a candy from the machine
func OpenTheCandyMachine(w http.ResponseWriter, req *http.Request) {
	key, ok := req.URL.Query()["key"]

	if !ok || len(key[0]) < 1 {
		fmt.Fprintf(w, "Missing candy key\n")
		return
	}

	candyKeyStep1, err := DecodeStep1(key[0])

	// Prevent timing attack here
	if err != nil {
		approxSizeOfBuffer := len(key[0]) * 3 / 4
		DecodeStep2(make([]byte, approxSizeOfBuffer))
		fmt.Fprintf(w, "An error occured\n")
		return
	}

	candyKeyStep2, err := DecodeStep2(candyKeyStep1)

	if err != nil {
		fmt.Fprintf(w, "An error occured\n")
		return
	}

	if !CheckCandyKeyValue(candyKeyStep2) {
		fmt.Fprintf(w, "An error occured\n")
		return
	}

	fmt.Fprintf(w, "Here's your candy : FLAG-....\n")
}

// Admin endpoint to generate a candy key (requires a secret password)
func GenerateCandyKey(w http.ResponseWriter, req *http.Request) {
	password, ok := req.URL.Query()["password"]
	aeskey, err := GetKey()

	if err != nil {
		fmt.Fprintf(w, "Error while loading the key\n")
		return
	}

	if !ok || len(password[0]) < 1 {
		fmt.Fprintf(w, "Missing password\n")
		return
	}

	if password[0] != GetPassword() {
		fmt.Fprintf(w, "Bad password\n")
		return
	}

	data, _ := AESEncrypt([]byte(GetMagicValue()), aeskey)
	data, _ = AESEncrypt(data, aeskey)
	sEnc := b64.URLEncoding.EncodeToString([]byte(data))

	fmt.Fprintf(w, "Here's your candy key : "+sEnc+"\n")
}

// The endpoint of the candy machine
func main() {
	http.HandleFunc("/candy-machine-open", OpenTheCandyMachine)
	http.HandleFunc("/candy-machine-get-key", GenerateCandyKey)
	http.ListenAndServe(":8090", nil)
}

// AES-CBC with PKCS7 padding
func AESEncrypt(src []byte, key []byte) ([]byte, error) {
	block, _ := aes.NewCipher(key)
	content, err := pkcs7Pad(src, block.BlockSize())

	if err != nil {
		return nil, err
	}

	content, err = encryptCBC(key, content)

	if err != nil {
		return nil, err
	}

	return content, nil
}

func AESDecrypt(crypt []byte, key []byte) ([]byte, error) {
	block, _ := aes.NewCipher(key)
	content, err := decryptCBC(key, []byte(crypt))

	if err != nil {
		return nil, err
	}

	return pkcs7Unpad(content, block.BlockSize())
}

// From : https://gist.github.com/locked/b066aa1ddeb2b28e855e
func encryptCBC(key, plaintext []byte) (ciphertext []byte, err error) {
	if len(plaintext)%aes.BlockSize != 0 {
		panic("plaintext is not a multiple of the block size")
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		panic(err)
	}

	ciphertext = make([]byte, aes.BlockSize+len(plaintext))
	iv := ciphertext[:aes.BlockSize]
	if _, err := io.ReadFull(rand.Reader, iv); err != nil {
		panic(err)
	}

	cbc := cipher.NewCBCEncrypter(block, iv)
	cbc.CryptBlocks(ciphertext[aes.BlockSize:], plaintext)

	return
}

func decryptCBC(key, ciphertext []byte) (plaintext []byte, err error) {
	var block cipher.Block

	if block, err = aes.NewCipher(key); err != nil {
		return
	}

	if len(ciphertext) < aes.BlockSize {
		return nil, errors.New("invalid blocksize")
	}

	if len(ciphertext)%aes.BlockSize != 0 {
		return nil, errors.New("invalid blocksize")
	}

	iv := ciphertext[:aes.BlockSize]
	ciphertext = ciphertext[aes.BlockSize:]

	cbc := cipher.NewCBCDecrypter(block, iv)
	cbc.CryptBlocks(ciphertext, ciphertext)

	plaintext = ciphertext
	return
}

// From : https://gist.github.com/huyinghuan/7bf174017bf54efb91ece04a48589b22

// pkcs7Pad right-pads the given byte slice with 1 to n bytes, where
// n is the block size. The size of the result is x times n, where x
// is at least 1.
func pkcs7Pad(b []byte, blocksize int) ([]byte, error) {
	if blocksize <= 0 {
		return nil, errors.New("invalid blocksize")
	}
	if b == nil || len(b) == 0 {
		return nil, errors.New("invalid PKCS7 data (empty or not padded)")
	}
	n := blocksize - (len(b) % blocksize)
	pb := make([]byte, len(b)+n)
	copy(pb, b)
	copy(pb[len(b):], bytes.Repeat([]byte{byte(n)}, n))
	return pb, nil
}

// pkcs7Unpad validates and unpads data from the given bytes slice.
// The returned value will be 1 to n bytes smaller depending on the
// amount of padding, where n is the block size.
func pkcs7Unpad(b []byte, blocksize int) ([]byte, error) {
	if blocksize <= 0 {
		return nil, errors.New("invalid blocksize")
	}
	if b == nil || len(b) == 0 {
		return nil, errors.New("invalid PKCS7 data (empty or not padded)")
	}
	if len(b)%blocksize != 0 {
		return nil, errors.New("invalid padding on input")
	}
	c := b[len(b)-1]
	n := int(c)
	if n == 0 || n > len(b) {
		return nil, errors.New("invalid padding on input")
	}
	for i := 0; i < n; i++ {
		if b[len(b)-n+i] != c {
			return nil, errors.New("invalid padding on input")
		}
	}
	return b[:len(b)-n], nil
}
