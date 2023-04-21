#!/bin/bash

current_hostname=$(hostname)
netID="jxp220032"
configPath=$1
# Read the number of nodes from the configuration file
num_nodes=$(sed -n '1p' $configPath)

# Read the hostnames and ports using regex
hostnames=($(sed -nE 's/^[0-9]+ ([^ ]+) [0-9]+$/\1/p' $configPath))
ports=($(sed -nE 's/^[0-9]+ [^ ]+ ([0-9]+)$/\1/p' $configPath))

# Loop through each node and SSH into it to free up the port
for ((i=1; i<=num_nodes; i++))
do
  hostname=${hostnames[$i]}
  port=${ports[$i]}
  
  # Free up the port on the current machine if it is in the configuration file
  if [ "$current_hostname" = "$hostname" ]
  then
    fuser -k "$port/tcp"
    echo "Freed up port $port on $current_hostname"
  else
    # SSH into the current node and free up the specified port
    ssh "$netID@$hostname" "fuser -k $port/tcp"
    echo "Freed up port $port on $hostname"
  fi
done
