#!/bin/bash
DOCKER_HOST=$(curl http://169.254.169.254/latest/meta-data/local-ipv4)
DOCKER_CONTAINER=$HOSTNAME

echo $DOCKER_HOST
echo $DOCKER_CONTAINER
HYDRATE_UUID=uuid
CATIDS=catids
#Pull message from work queue
RESPONSE=$(curl -H "Content-Type: application/json" -X GET http://$DOCKER_HOST:8080/mise-en-place/pull)
M=$(echo $RESPONSE|jq '.message')

if [ -z "$M" ] || [ "$M" == null ]
then
	exit 1
fi
HYDRATE_UUID=$(echo $RESPONSE|jq '.message.hydrateUUID')

#
#get all variables for datastager
#
CATIDS=$(echo $RESPONSE|jq '.message.catalogIds | join(" ")')
#
#
echo "*** 1 Mounting Hydrate directory as tmpfs***"
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","mount -t tmpfs none -0 size=10g /tmp/hydrate"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run
if [ "$?" -gt 0 ]; then
 echo "*** Something is wrong with the TMPFS mount***"
fi

echo "*** 2 Checking if Hydrate directory is actually tmpfs***"
df -T /tmp/hydrate

echo "*** 3 Create tiny 3Megs file***"
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","dd if=/dev/zero of=/tmp/hydrate/img.bin bs=6x6x62b count=3"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run

echo "*** 4 Creating loop block device***"
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","losetup -P /dev/loop2 /tmp/hydrate/img.bin"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run

curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","sfdisk /dev/loop2 < /home/hsmclient/Orchestration/layout.in"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run

echo "*** 4.1 Running mkfs on loop device"

curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","mkfs.ext4 /dev/loop2p1"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run


echo "*** 5 Mounting loop device***"

#mkdir /mnt/hydrate

curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","mount -o loop /dev/loop2p1 /tmp/hydrate"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run

echo "*** 5.1 ls -lah on new /mnt/hydrate mount point***"
ls -lah /tmp/hydrate

echo "*** 6 The script starts now***"
echo "*** 7 calling audit cli client***"
sleep 5 
HYDRATE_TEMP_PATH=/tmp/hydrate
USER_TYPE=EC2_INSTANCE
NAME=default
ACTION=INVOKE_ORTHO_REC

java -jar audit-cli-client-0.0.1-jar-with-dependencies.jar -huuid $HYDRATE_UUID -utype $USER_TYPE -name $NAME -action $ACTION -metadata

#Data stager call
java -jar hydrate-data-stager-0.0.1-jar-with-dependencies.jar -nItar -platform p2020 -env dev -catId $CATIDS -path /tmp/hydrate/nItar

sleep 2
g++ -o testRectifier testRectifier.cpp
echo "**** 8 Writing a file to tmpfs directory and heap/stack with a test process ****"
./testRectifier $HYDRATE_TEMP_PATH/nItar output & PID=$!
#echo "test" > $HYDRATE_TEMP_PATH/my_in_memory_file

sleep 1
echo "***** The PID of the test is ${PID} ******"
echo "***** Checking Heap and Stack of ${PID} before Emil's code ******"
cat /proc/$PID/maps

echo "*** 9 List contents on hydrate TMPFS directory ***"
ls -lah .

echo "*** 10 Running Emils code to clear PID heap and stack ***"
#EMIL"S code"
if [[ "" !=  "$PID" ]]; then
  echo "***** Wiping memory for $PID ******"
  gcc -Wall -o zeromem zeromem.c
  ./zeromem $PID
fi

echo "*** 10.1 Grepping the Heap & Stack of the Test Process after Emil's Code***"
cat /proc/$PID/maps

echo "*** 11 Rectifier complete, calling audit cli client..***"
ACTION=TRANSACTION_RELEASED
java -jar audit-cli-client-0.0.1-jar-with-dependencies.jar -huuid $HYDRATE_UUID -utype $USER_TYPE -name $NAME -action $ACTION -metadata

#DATA PUSHER
java -jar hydrate-data-pusher-0.0.1-jar-with-dependencies.jar -um -b hydrate-resultant-bucket -k output/ -u output/ -i $HYDRATE_UUID

#delte from quque
curl -H "Content-Type: application/json" -X POST -d $HYDRATE_UUID http://$DOCKER_HOST:8080/mise-en-place/delete

#SNS NOTIFICATION
#RESULT=$(curl -H "Content-Type: application/json" -X GET https://zde98x8x30.execute-api.us-east-1.amazonaws.com/dev/hydratation?uuid=$HYDRATE_UUID)
#RESULT_URL=$(echo $RESULT | jq '.result')
#java -jar hydrate-sns-ready-notification-0.0.1-jar-with-dependencies.jar -p -i $HYDRATE_UUID -m $RESULT_URL


echo "*** 12 Memory heap & Stack Cleared. Now checking that /mnt/hydrate still has data before zeroing it out using grep -zq . /dev/loop2 && echo "Found a none Zero!" ***"
#dd if=/dev/loop2 bs=1M count=3 | hexdump -C
#cat $HYDRATE_TEMP_PATH/*
grep -zq . /dev/loop2 && echo "Found a none Zero!"

echo "*** 13 Now running a dd zero command to clean the data in /mnt/hydrate ***"

curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","-u","root","'$HOSTNAME'","sh","-c","dd if=/dev/zero of=/dev/loop2 bs=6x6x62b count=3"],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run
#dd if=/dev/zero of=/dev/ram0
#if [ "$?" -gt 0 ]; then
#  echo "Something is wrong with dd"
#fi

echo "*** 14 See if /tmp/hydrate has data after doing the dd zero out command with grep -zq . /dev/loop2 && echo "Found a none Zero!" ***"
sleep 5
#dd if=/dev/loop2 bs=512 | hexdump -C |grep -qi data && echo "found, something went wrong!"
#sleep 1
#dd if=/dev/loop2 bs=512 | hexdump -C
grep -zq . /dev/loop2 && echo "Found a none Zero!"



echo "*** Shutting down... ***"
