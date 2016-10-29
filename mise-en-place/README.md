#Mise-en-Place provisioning tasks

##What do you get?

* Run provisioning commands locally or over SSH or both
* Define Pre and Post tasks for a given command
* Handle SSH secrets in a consistent secure fashion

##Usage

```
<hsmclient-setup> [-b] bucket-name
                  [-k] object-name that holds priv key
                  [-p] object-name that holds passphrase on 1st line
                  [-u] system user who will own key pair
                  [-d] destination path for key pair installation
                  [-i] HSM ip address
```
 Fetches SSH private key and passphrase from s3 using instance role
 credentials and installs them locally for later use by cloudhsm cli commands.
 Configures Linux HSM client using aws cli cloudhsm commands.

###Example:

```
 ./hsmclient-setup.rb -b cloudhsm-ssh-keypair \
                      -k hsm-client-key \
                      -u hsmclient \
                      -p passphrase.yml \
                      -d /home/hsmclient/.ssh \
                      -i 10.0.201.209
```

##TODO

* Add idempotent capabilities
