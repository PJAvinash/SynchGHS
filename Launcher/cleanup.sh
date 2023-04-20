#!/bin/bash

# Read the contents of the configuration file into a variable
configPath = $1
config=$(cat $configPath)

# Remove comment lines and extract node hostnames
nodes=$(echo "$config" | grep -E '^[0-9]+$' | head -n 1)
hostnames=$(echo "$config" | grep -E '^[0-9]+\s+\w+\.\w+\.\w+\s+[0-9]+$' | cut -d ' ' -f 2)
netID="jxp220032"

# Get the PIDs of the java processes started by the launch.sh script

pids=$(pgrep -f "java Main $configPath")
# Loop through the PIDs and terminate the corresponding processes
for pid in $pids
do
  echo "Terminating process $pid ..."
  kill $pid
done

for remotehost in "${hostnames[@]}"
do
  # Skip the host machine
  if [[ "$remotehost" == "$host" ]]; then
    continue
  fi
  echo "Connecting to $node ..."
  ssh $netID@$remotehost 
  childPIDs = $(pgrep -f "java Main $configPath")
  for pid in $pids
  do
    echo "Terminating process $pid ..."
    kill $pid
    done
done
