#!/bin/bash
DIR="/etc/init.d/cpayadmin/"
INSTALLDIR="/opt/cpayadmin/"
INSTALLDIRLOCKS="/opt/cpayadmin/locks/"
JAR_FILE="../InitializrSpringbootProject/target/cito-0.0.1-SNAPSHOT.jar"
REACT_APP_CODE="../clientside/."
LOG_DIR="/var/log/cpayadmin/"
REACT_FILE="/opt/cpayadmin/reactjs/"

if [ -d "$DIR" ]; then
    cp shutdown.sh /etc/init.d/cpayadmin/shutdown.sh
    cp start.sh /etc/init.d/cpayadmin/start.sh
    cp restart.sh /etc/init.d/cpayadmin/restart.sh
else
    ###  Now create thid drectory ###
    mkdir -p "$DIR"
    cp shutdown.sh /etc/init.d/cpayadmin/shutdown.sh
    cp start.sh /etc/init.d/cpayadmin/start.sh
    cp restart.sh /etc/init.d/cpayadmin/restart.sh
fi

#if [ -d "$REACT_FILE" ]; then
    ### Copy React App code ####
#    cp -R -f "$REACT_APP_CODE" "$REACT_FILE"
#else
    ###  Now create react directory and copy react code ###
#    mkdir -p "$REACT_FILE" "$REACT_FILE"
    cp -R -f "$REACT_APP_CODE" "$REACT_FILE"
#fi


if [ -d "$INSTALLDIR" ]; then
    cp "$JAR_FILE" "$INSTALLDIR"
else
    ###  Now create install dir and transfer the jar file there ###
    mkdir -p "$INSTALLDIR" 
    cp "$JAR_FILE" "$INSTALLDIR"
fi

#Install the locks directory
if [ -d "$INSTALLDIRLOCKS" ]; then
    echo "$INSTALLDIRLOCKS was already created." 
else
    ###  Now create install director y ###
    mkdir -p "$INSTALLDIRLOCKS"
fi

if [ -d "$LOG_DIR" ]; then
    echo "$LOG_DIR already exists"
else
    ###  Now create install dir and transfer the jar file there ###
    mkdir -p "$LOG_DIR" 
fi

echo "Installation Done!."
exit 1
