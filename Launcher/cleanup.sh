#!/bin/bash

configPath=$1

netID = jxp220032

# Get the PIDs of the java processes started by the launch.sh script

pids=$(pgrep -f "java Main $configPath")
# Loop through the PIDs and terminate the corresponding processes
for pid in $pids
do
  echo "Terminating process $pid ..."
  kill $pid
done

# Parse the input file and extract the list of nodes
nodes=($(awk '$0 !~ /^#/ && NF {print $2}' $configPath))

for node in "${nodes[@]}"
do
  # Skip the host machine
  if [[ "$node" == "$host" ]]; then
    continue
  fi
  echo "Connecting to $node ..."
  ssh $netID@$node 
  childPIDs = $(pgrep -f "java Main $configPath")
  for pid in $pids
  do
    echo "Terminating process $pid ..."
    kill $pid
    done
done
