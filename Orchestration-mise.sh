#!/bin/bash
DOCKER_HOST=$(curl http://169.254.169.254/latest/meta-data/local-ipv4)
DOCKER_CONTAINER=$HOSTNAME

echo $DOCKER_HOST
echo $DOCKER_CONTAINER

mkdir /tmp/hydrate
sleep 1
echo "*** 1 Mounting Hydrate directory as tmpfs***"
sleep 1
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"mount -t tmpfs none /tmp/hydrate\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run
if [ "$?" -gt 0 ]; then
 echo "*** Something is wrong with the TMPFS mount***"
fi

echo "*** 2 Checking if Hydrate directory is actually tmpfs***"
sleep 1
df -T /tmp/hydrate

echo "*** 3 Create tiny 3Megs file***"
sleep 1
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"dd if=/dev/zero of=/tmp/hydrate/img.bin bs=6x6x62b count=3\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run

echo "*** 4 Creating loop block device***"
sleep 2
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"losetup -P /dev/loop2 /tmp/hydrate/img.bin\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run
sleep 4
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"sfdisk /dev/loop2 < /root/Orchestration/testrepo/layout.in\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run

echo "*** 4.1 Running mkfs on loop device"
sleep 4
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"mkfs.ext4 /dev/loop2p1\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run


echo "*** 5 Mounting loop device***"
sleep 3
mkdir /mnt/hydrate
sleep 1
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"mount -o loop /dev/loop2p1 /mnt/hydrate\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run
sleep 2
echo "*** 5.1 ls -lah on new /mnt/hydrate mount point***"
ls -lah /mnt/hydrate

echo "*** 6 The script starts now***"
echo "*** 7 calling audit cli client***"
sleep 5 
HYDRATE_TEMP_PATH=/mnt/hydrate
HYDRATE_UUID=e0a7e4b6-8a6e-11e6-ae22-56b6b6499611-HYD
USER_TYPE=EC2_INSTANCE
NAME=default
ACTION=INVOKE_ORTHO_REC

java -jar audit-cli-client-0.0.1-jar-with-dependencies.jar -huuid $HYDRATE_UUID -utype $USER_TYPE -name $NAME -action $ACTION -metadata
#Pull message from work queue
RESPONSE=$(curl -H "Content-Type: application/json" -X GET http://$DOCKER_HOST:8080/mise-en-place/pull)


#
#get all variables for datastager
#
CATIDS=$(echo $RESPONSE|jq '.message.catalogIds | join(" ")')
#
#
#

#Data stager call
java -jar hydrate-data-stager-0.0.1-jar-with-dependencies.jar -nItar -platform p800 -env dev -catId $CATIDS -path /root/

sleep 15
g++ test.cpp -o test
sleep 1
echo "**** 8 Writing a file to tmpfs directory and heap/stack with a test process ****"
./test $HYDRATE_TEMP_PATH & PID=$!
#echo "test" > $HYDRATE_TEMP_PATH/my_in_memory_file
sleep 1
echo "***** The PID of the test is ${PID} ******"
echo "***** Checking Heap and Stack of ${PID} before Emil's code ******"
cat /proc/$PID/maps

sleep 10
echo "*** 9 List contents on hydrate TMPFS directory ***"
ls -lah $HYDRATE_TEMP_PATH

sleep 10
echo "*** 10 Running Emils code to clear PID heap and stack ***"
#EMIL"S code"
if [[ "" !=  "$PID" ]]; then
  echo "***** Wiping memory for $PID ******"
  gcc -Wall -o zeromem zeromem.c
  ./zeromem $PID
fi

sleep 15
echo "*** 10.1 Grepping the Heap & Stack of the Test Process after Emil's Code***"
cat /proc/$PID/maps

echo "*** 11 Rectifier complete, calling audit cli client..***"
sleep 10
ACTION=TRANSACTION_RELEASED
java -jar audit-cli-client-0.0.1-jar-with-dependencies.jar -huuid $HYDRATE_UUID -utype $USER_TYPE -name $NAME -action $ACTION -metadata

sleep 15
echo "*** 12 Memory heap & Stack Cleared. Now checking that /mnt/hydrate still has data before zeroing it out using grep -zq . /dev/loop2 && echo "Found a none Zero!" ***"
#dd if=/dev/loop2 bs=1M count=3 | hexdump -C
#cat $HYDRATE_TEMP_PATH/*
grep -zq . /dev/loop2 && echo "Found a none Zero!"

echo "*** 13 Now running a dd zero command to clean the data in /mnt/hydrate ***"
sleep 5
curl -H "Content-Type: application/json" -X POST -d '{"command" : "docker","parameters" : ["exec","--privileged","'$HOSTNAME'","sh","-c","\"if=/dev/zero of=/dev/loop2 bs=6x6x62b count=3\""],"context" : {"hostname" : "'$HOSTNAME'","task-id" : "'$TASK_ID'"}}' http://$DOCKER_HOST:8080/mise-en-place/run
#dd if=/dev/zero of=/dev/ram0
#if [ "$?" -gt 0 ]; then
#  echo "Something is wrong with dd"
#fi

echo "*** 14 See if /mnt/hydrate has data after doing the dd zero out command with grep -zq . /dev/loop2 && echo "Found a none Zero!" ***"
sleep 5
#dd if=/dev/loop2 bs=512 | hexdump -C |grep -qi data && echo "found, something went wrong!"
#sleep 1
#dd if=/dev/loop2 bs=512 | hexdump -C
grep -zq . /dev/loop2 && echo "Found a none Zero!"

echo "*** Shutting down... ***"