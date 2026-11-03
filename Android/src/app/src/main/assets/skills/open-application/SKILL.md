---
name: open-application
description: Open or launch any installed app on the phone like Telegram, YouTube, Camera, Calculator, Gallery, or Settings.
---

# Open Application

## Instructions

To open or launch an application on the user's phone:
1. Determine the name of the app requested by the user (e.g. "Telegram", "Калькулятор", "YouTube", "Камера", "Галерея", "Настройки", "WhatsApp").
2. Call the `run_intent` tool with the following exact parameters:
- intent: `open_app`
- parameters: A JSON string with the following field:
  - `app_name`: the display name or package of the application to open. String.
