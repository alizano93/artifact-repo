#!/bin/bash

source ~/.rvm/scripts/rvm

eval `ssh-agent`
./hsmclient-provisioner/hsmclient-setup.rb -b cloudhsm-ssh-keypair \
                                           -k hsm-client-key \
                                           -u hsmclient \
                                           -p passphrase.yml \
                                           -d /home/hsmclient \
                                           -i 10.0.201.209
cd hydrate-security-module && ./test.sh
