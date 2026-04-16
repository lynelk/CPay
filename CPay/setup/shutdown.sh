#!/bin/bash


####kill -KILL $(cat /var/run/cpayadmin-reactjs.pid)###

#echo "Stopping React App..."

#for pid in $(ps aux | grep "react-app-rewired" | awk '{print $2}'); do kill -9 $pid; done

#echo "Done stopping React App..."

kill  -KILL $(cat /var/run/cpayadmin.pid)

echo "Done shutting down Cpay Java App!"