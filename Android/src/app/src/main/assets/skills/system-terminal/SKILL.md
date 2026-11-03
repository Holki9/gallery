---
name: system-terminal
description: Run shell commands, check memory, processes, system properties, and hardware status on the Android device offline.
---

# System Terminal

## Instructions

To execute a shell command on the Android device:
1. Determine the appropriate Linux/Android command (e.g. `ps -A`, `cat /proc/meminfo`, `getprop`, `df -h`, `uname -a`, `uptime`, `ls /sdcard`, etc.).
2. Call the `run_intent` tool with the following exact parameters:
- intent: `execute_shell`
- parameters: A JSON string with the following field:
  - `command`: the shell command line string to run. String.
