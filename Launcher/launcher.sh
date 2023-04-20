#!/bin/bash

configPath=$1

# Get the hostname of the current machine
host=$(hostname)

# Parse the input file and extract the list of nodes
nodes=($(awk '$0 !~ /^#/ && NF {print $2}' $configPath))

# Compile the Java program in the parent directory
cd ..
javac Main.java

#create build
for node in "${nodes[@]}"
do
  # Skip the host machine
  if [[ "$node" == "$host" ]]; then
    continue
  fi
  echo "Connecting to $node ..."
  ssh $node "cd .. && javac Main.java "
done

# Loop through the nodes and execute a command over SSH
for node in "${nodes[@]}"
do
  # Skip the host machine
  if [[ "$node" == "$host" ]]; then
    continue
  fi
  echo "Connecting to $node ..."
  ssh -f $node "java Main $configPath"
  sleep 1
done
