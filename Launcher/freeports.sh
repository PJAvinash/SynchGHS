#!/bin/bash

configPath=$1
# Read the number of nodes from the configuration file
num_nodes=$(sed -n '1p' $configPath)

# Get the hostname of the current machine
current_hostname=$(hostname)

# Loop through each node and SSH into it to free up the port
for ((i=1; i<=num_nodes; i++))
do
  # Extract the UID, hostname, and port for the current node
  node_info=$(sed -n "${i+1}p" config.txt)
  uid=$(echo "$node_info" | awk '{print $1}')
  hostname=$(echo "$node_info" | awk '{print $2}')
  port=$(echo "$node_info" | awk '{print $3}')
  
  # Free up the port on the current machine if it is in the configuration file
  if [ "$current_hostname" = "$hostname" ]
  then
    fuser -k "$port/tcp"
    echo "Freed up port $port on $current_hostname"
  else
    # SSH into the current node and free up the specified port
    ssh "$hostname" "fuser -k $port/tcp"
    echo "Freed up port $port on $hostname"
  fi
done
