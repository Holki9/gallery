---
name: storage-analyzer
description: Analyze phone storage, find what takes up disk space, check free space, and identify large folders and downloads.
---

# Storage Analyzer

## Instructions

To analyze device storage and find why storage is low or what takes up space:
1. Call the `run_intent` tool with the following exact parameters:
- intent: `analyze_storage`
- parameters: `{}`
2. Summarize the returned JSON for the user in clear language, highlighting total space, used space, free space, and the largest folders (e.g. Telegram, DCIM, Download).
