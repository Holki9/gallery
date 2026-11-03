---
name: read-file-content
description: Read the text content of a local file on device storage offline.
---

# Read File Content

## Instructions

To read the content of a local file on the device:
1. Call the `run_intent` tool with the following exact parameters:
- intent: `read_file`
- parameters: A JSON string with the following field:
  - `file_path`: the absolute path of the file to read. String.
2. Present the contents of the file to the user.
