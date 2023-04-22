import os

# Create the logs directory if it doesn't exist
if not os.path.exists('logs'):
    os.makedirs('logs')

with open('logs.txt', 'r') as f:
    lines = f.readlines()

# Loop through the lines and extract the lines starting with 'uid: 1'
print(len(lines))
uid1_lines = []
for line in lines:
    if line.startswith("uid:9"):
        uid1_lines.append(line)

# Write the lines starting with 'uid: 1' to a log file
with open('logs/uid1.txt', 'w') as f:
    f.writelines(uid1_lines)
