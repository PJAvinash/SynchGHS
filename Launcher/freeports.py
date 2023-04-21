import argparse
import subprocess
import re
import socket

#netID
netID = 'jxp220032'

# hostname
currentHost = socket.gethostname()

# Define argument parser to accept config file
parser = argparse.ArgumentParser()
parser.add_argument("config_file", help="Path to configuration file")
args = parser.parse_args()

# Open and read the config file
with open(args.config_file, "r") as f:
    lines = f.readlines()

hosts = {}
# Loop through the lines and extract the hostname and port number using regex
for line in lines:
    match = re.match(r'^\d+ (\S+) (\d+)$', line)
    if match:
        hostname = match.group(1)
        port = int(match.group(2))
        hosts[hostname] = port

# Loop through each host and SSH into it to execute command
for host,port in hosts.items():
    ssh_command = f"ssh {netID}@{host} 'fuser -k '{port}/tcp''"
    if(currentHost == host):
        ssh_command = f"fuser -k '{port}/tcp'"
    print(ssh_command)
    subprocess.Popen(ssh_command, shell=True)