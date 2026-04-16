#!/bin/bash

####  Start React App  #####
#REACT_FILE="/opt/cpayadmin/reactjs/"
#cd "$REACT_FILE"
#nohup npm start > /tmp/cpayadmin.log 2>&1 &
#echo $! > /var/run/cpayadmin-reactjs.pid


####  Start Java Applicaiton - (Cpay Gateway)  #####

nohup java -jar /opt/cpayadmin/cito-0.0.1-SNAPSHOT.jar > /var/log/cpayadmin/log.txt 2>&1 &
echo $! > /var/run/cpayadmin.pid

