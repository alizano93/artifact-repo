#!/bin/sh
 
salogin -c -s 1 -i 1:1 
salogin -o -s 1 -i 1:1
export KEY_STORE_PASSWORD="password2"
export LUNA_LOGIN_PASSWORD="S3kur3dPazz"

export MESSAGE="This is a test `date`" 
export FILE_NAME="test.camera.model.2.txt"
echo "${MESSAGE}" > ~/${FILE_NAME}
export SECURE_DATA_NAME="camera-model-${FILE_NAME}"
export INPUT_FILE=~/${FILE_NAME}
export OUTPUT_FILE=~/output.${FILE_NAME}
#java  -Xdebug -Xrunjdwp:transport=dt_socket,address=8888,server=y,suspend=n  -jar hydrate-security-module-0.0.1-SNAPSHOT-jar-with-dependencies.jar 
#!/bin/sh
export CLIENT_METADATA="-m request-accepted-date=`date +%Y-%m-%dT%H:%M:%S%:z` hostname=`hostname`"
java -jar target/hydrate-security-module-0.0.1-SNAPSHOT-jar-with-dependencies.jar -d -f ${INPUT_FILE} -n ${SECURE_DATA_NAME} -s ${CLIENT_METADATA}
java -jar target/hydrate-security-module-0.0.1-SNAPSHOT-jar-with-dependencies.jar -d -f ${OUTPUT_FILE} -n ${SECURE_DATA_NAME} -g

diff "${INPUT_FILE}" "${OUTPUT_FILE}" && echo "It went all good, passed smoke test" 
#java  -Xdebug -Xrunjdwp:transport=dt_socket,address=8888,server=y,suspend=n  -jar hydrate-security-module-0.0.1-SNAPSHOT-jar-with-dependencies.jar 
#!/bin/sh

#java -jar target/hydrate-security-module-0.0.1-SNAPSHOT-jar-with-dependencies.jar -d -f ~/test.txt -n HersonVaultCLI1 -s
#java -jar target/hydrate-security-module-0.0.1-SNAPSHOT-jar-with-dependencies.jar -d -f ~/test2.txt -n HersonVaultCLI1 -g
salogin -c -s 1 -i 1:1 
