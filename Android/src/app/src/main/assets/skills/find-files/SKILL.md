---
name: find-files
description: Find or search for files, documents, downloads, and photos in device storage offline.
---

# Find Files

## Instructions

To search for files on the device storage:
1. Call the `run_intent` tool with the following exact parameters:
- intent: `find_files`
- parameters: A JSON string with the following fields:
  - `query`: the search query or file extension (e.g. "apk", "pdf", "отчет", "screenshot", "mp4"). String.
  - `path`: (optional) directory to search in, defaults to "/sdcard". String.
2. Present the found files with their paths and sizes to the user.
