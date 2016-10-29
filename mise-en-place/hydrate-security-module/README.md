# hydrate-security-module

##Pre-requisites 
 + Unlimited JCE policy set on your `${JAVA_HOME}`
 + Add Safenet Inc propertary libs--_libLunaAPI.so_ and _LunaProvider.jar_--placed on your `${JAVA_HOME}/jre/lib/ext/`

##How to run:
you need to provide `LUNA_LOGIN_PASSWORD` environment variable as well as `KEY_STORE_PASSWORD` to make _run.sh_ able to execute.

Then, just run. 
```sh 
sh run.sh
```
 
##Help
```sh
java -jar target/${ARTIFACT_ID}.jar -h
```
```
usage: hydrate-security-module
 -d,--debug <true|false>                           Set whether to show  debugging messages.
 -f,--file <file-path>                             Path containing the bytes to be (securely)
                                                   stored. **If missing, then will read from
                                                   System.in or print to System.out**
 -g,--get                                          Run on get-data mode
 -m,--metadata <key1=value1  [ ... keyN=valueN]>   Metadata key-value pairs to be attached to
                                                   the raw data content
 -mkn,--master-key-name <name>                     Name of the master key to use.
 -n,--secure-data-name <name>                      Name of the secure data store.
 -s,--store                                        Run on store-data mode
```
