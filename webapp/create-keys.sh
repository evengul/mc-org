#!/bin/bash
cd ./src/main/resources || exit 1
mkdir -p keys
cd keys || exit 1
openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in private_key.pem -out public_key.pem