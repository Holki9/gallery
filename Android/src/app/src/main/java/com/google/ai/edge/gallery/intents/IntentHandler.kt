/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ai.edge.gallery.intents

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import com.google.ai.edge.gallery.notifications.NotificationScheduleManagerEntryPoint
import com.google.ai.edge.gallery.proto.ScheduledNotification
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.EntryPointAccessors
import java.lang.Exception
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@JsonClass(generateAdapter = true)
data class SendEmailParams(
  val extra_email: String,
  val extra_subject: String,
  val extra_text: String,
)

@JsonClass(generateAdapter = true)
data class SendSmsParams(val phone_number: String, val sms_body: String)

@JsonClass(generateAdapter = true)
data class CreateCalendarEventParams(
  val title: String,
  val description: String,
  val begin_time: String,
  val end_time: String,
)

@JsonClass(generateAdapter = true) data class ReadCalendarEventsParams(val date: String)

@JsonClass(generateAdapter = true)
data class CalendarEventDto(
  val title: String,
  val description: String,
  val begin_time: String,
  val end_time: String,
)

@JsonClass(generateAdapter = true)
data class ReadCalendarEventsResponse(val events: List<CalendarEventDto>)

enum class IntentAction(val action: String) {
  SEND_EMAIL("send_email"),
  SEND_SMS("send_sms"),
  CREATE_CALENDAR_EVENT("create_calendar_event"),
  READ_CALENDAR_EVENTS("read_calendar_events"),
  GET_CURRENT_DATE_AND_TIME("get_current_date_and_time"),
  SCHEDULE_NOTIFICATION("schedule_notification"),
  OPEN_APP("open_app"),
  EXECUTE_SHELL("execute_shell"),
  ANALYZE_STORAGE("analyze_storage"),
  FIND_FILES("find_files"),
  READ_FILE("read_file");

  companion object {
    fun from(action: String): IntentAction? = entries.find { it.action == action }
  }
}

@JsonClass(generateAdapter = true)
data class ScheduleNotificationParams(
  val title: String,
  val message: String,
  val hour: Int,
  val minute: Int,
  val deeplink: String? = null,
  val task_id: String? = null,
  val model_name: String? = null,
  val year: Int? = null,
  val month: Int? = null,
  val day: Int? = null,
  val repeat_daily: Boolean? = null,
)

object IntentHandler {
  private const val TAG = "IntentHandler"

  suspend fun handleAction(
    context: Context,
    action: String,
    parameters: String,
    // requestPermission is a suspend function that takes a permission string and returns true if
    // the permission is granted, false otherwise.
    requestPermission: suspend (String) -> Boolean,
  ): String {
    return when (IntentAction.from(action)) {
      IntentAction.SEND_EMAIL -> {
        try {
          val moshi = Moshi.Builder().build()
          val jsonAdapter = moshi.adapter(SendEmailParams::class.java)
          val params = jsonAdapter.fromJson(parameters)
          if (params != null) {
            val intent =
              Intent(Intent.ACTION_SEND).apply {
                data = "mailto:".toUri()
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(params.extra_email))
                putExtra(Intent.EXTRA_SUBJECT, params.extra_subject)
                putExtra(Intent.EXTRA_TEXT, params.extra_text)
              }
            context.startActivity(intent)
            "succeeded"
          } else {
            Log.e(TAG, "Failed to parse send_email parameters: $parameters")
            "failed"
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to parse send_email parameters: $parameters", e)
          "failed"
        }
      }
      IntentAction.SEND_SMS -> {
        try {
          val moshi = Moshi.Builder().build()
          val jsonAdapter = moshi.adapter(SendSmsParams::class.java)
          val params = jsonAdapter.fromJson(parameters)
          if (params != null) {
            val uri = "smsto:${params.phone_number}".toUri()
            val intent = Intent(Intent.ACTION_SENDTO, uri)
            intent.putExtra("sms_body", params.sms_body)
            context.startActivity(intent)
            "succeeded"
          } else {
            Log.e(TAG, "Failed to parse send_sms parameters: $parameters")
            "failed"
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to parse send_sms parameters: $parameters", e)
          "failed"
        }
      }
      IntentAction.CREATE_CALENDAR_EVENT -> {
        try {
          val moshi = Moshi.Builder().build()
          val jsonAdapter = moshi.adapter(CreateCalendarEventParams::class.java)
          val params = jsonAdapter.fromJson(parameters)
          if (params != null) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val beginTimeMillis = format.parse(params.begin_time)?.time ?: 0L
            val endTimeMillis = format.parse(params.end_time)?.time ?: 0L
            val intent =
              Intent(Intent.ACTION_INSERT).apply {
                data = Events.CONTENT_URI
                putExtra(Events.TITLE, params.title)
                putExtra(Events.DESCRIPTION, params.description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
              }
            context.startActivity(intent)
            "succeeded"
          } else {
            Log.e(TAG, "Failed to parse create_calendar_event parameters: $parameters")
            "failed"
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to parse create_calendar_event parameters: $parameters", e)
          "failed"
        }
      }
      IntentAction.READ_CALENDAR_EVENTS -> {
        readCalendarEvents(context, parameters, requestPermission)
      }
      IntentAction.GET_CURRENT_DATE_AND_TIME -> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss EEEE", Locale.getDefault())
        val currentDateAndTime = sdf.format(Date())
        Log.d(
          TAG,
          "get_current_date_and_time via handleAction. Current date and time: $currentDateAndTime",
        )
        currentDateAndTime
      }
      IntentAction.SCHEDULE_NOTIFICATION -> {
        scheduleNotification(context, parameters)
      }
      IntentAction.OPEN_APP -> openApp(context, parameters)
      IntentAction.EXECUTE_SHELL -> executeShell(parameters)
      IntentAction.ANALYZE_STORAGE -> analyzeStorage()
      IntentAction.FIND_FILES -> findFiles(parameters)
      IntentAction.READ_FILE -> readFile(parameters)
      null -> "failed"
    }
  }

  private fun openApp(context: Context, parameters: String): String {
    return try {
      val json = JSONObject(parameters)
      val appName = json.optString("app_name", "").trim()
      if (appName.isEmpty()) return "failed: app_name is required"
      val pm = context.packageManager
      val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
      }
      val list = pm.queryIntentActivities(intent, 0)
      var matchedPkg: String? = null
      var matchedLabel: String? = null
      for (info in list) {
        val label = info.loadLabel(pm).toString()
        val pkg = info.activityInfo.packageName
        if (label.equals(appName, ignoreCase = true) || pkg.equals(appName, ignoreCase = true)) {
          matchedPkg = pkg
          matchedLabel = label
          break
        }
      }
      if (matchedPkg == null) {
        for (info in list) {
          val label = info.loadLabel(pm).toString()
          val pkg = info.activityInfo.packageName
          if (label.contains(appName, ignoreCase = true) || pkg.contains(appName, ignoreCase = true)) {
            matchedPkg = pkg
            matchedLabel = label
            break
          }
        }
      }
      if (matchedPkg != null) {
        val launchIntent = pm.getLaunchIntentForPackage(matchedPkg)
        if (launchIntent != null) {
          launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(launchIntent)
          "succeeded: Application '$matchedLabel' ($matchedPkg) opened successfully."
        } else {
          "failed: Cannot launch $matchedPkg"
        }
      } else {
        val sample = list.take(10).joinToString(", ") { it.loadLabel(pm).toString() }
        "failed: App '$appName' not found. Available sample: $sample"
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed openApp", e)
      "failed: ${e.message}"
    }
  }

  private fun executeShell(parameters: String): String {
    return try {
      val json = JSONObject(parameters)
      val command = json.optString("command", "").trim()
      if (command.isEmpty()) return "failed: command is required"
      val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
      val stdout = proc.inputStream.bufferedReader().readText().take(3000)
      val stderr = proc.errorStream.bufferedReader().readText().take(1000)
      val code = proc.waitFor()
      val res = JSONObject().apply {
        put("command", command)
        put("exit_code", code)
        put("stdout", stdout.ifEmpty { "(no output)" })
        if (stderr.isNotEmpty()) put("stderr", stderr)
      }
      res.toString()
    } catch (e: Exception) {
      Log.e(TAG, "Failed executeShell", e)
      "failed: ${e.message}"
    }
  }

  private fun analyzeStorage(): String {
    return try {
      val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
      val block = stat.blockSizeLong
      val total = stat.blockCountLong * block
      val free = stat.availableBlocksLong * block
      val used = total - free

      fun fmt(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB", gb)
      }

      val folders = mutableMapOf<String, String>()
      val sdcard = File("/sdcard")
      if (sdcard.exists() && sdcard.isDirectory) {
        listOf("Download", "DCIM", "Pictures", "Telegram", "Documents", "Android").forEach { name ->
          val f = File(sdcard, name)
          if (f.exists()) {
            val sz = f.walkTopDown().maxDepth(3).filter { it.isFile }.map { it.length() }.sum()
            if (sz > 0) folders[name] = fmt(sz)
          }
        }
      }

      val res = JSONObject().apply {
        put("total_storage", fmt(total))
        put("used_storage", fmt(used))
        put("free_storage", fmt(free))
        put("folder_sizes", JSONObject(folders as Map<*, *>))
      }
      res.toString()
    } catch (e: Exception) {
      Log.e(TAG, "Failed analyzeStorage", e)
      "failed: ${e.message}"
    }
  }

  private fun findFiles(parameters: String): String {
    return try {
      val json = JSONObject(parameters)
      val query = json.optString("query", "").trim()
      val path = json.optString("path", "/sdcard").trim()
      val root = File(path)
      if (!root.exists() || !root.isDirectory) return "failed: directory '$path' not found"

      val list = JSONArray()
      root.walkTopDown().maxDepth(5).filter { it.isFile && (query.isEmpty() || it.name.contains(query, ignoreCase = true)) }.take(30).forEach { f ->
        val mb = f.length().toDouble() / (1024 * 1024)
        val sz = if (mb >= 1.0) String.format(Locale.US, "%.2f MB", mb) else "${f.length() / 1024} KB"
        list.put(JSONObject().apply {
          put("name", f.name)
          put("path", f.absolutePath)
          put("size", sz)
        })
      }
      JSONObject().apply {
        put("query", query)
        put("count", list.length())
        put("results", list)
      }.toString()
    } catch (e: Exception) {
      Log.e(TAG, "Failed findFiles", e)
      "failed: ${e.message}"
    }
  }

  private fun readFile(parameters: String): String {
    return try {
      val json = JSONObject(parameters)
      val path = json.optString("file_path", "").trim()
      val f = File(path)
      if (!f.exists()) return "failed: file not found: $path"
      if (f.isDirectory) return "failed: $path is a directory"
      val text = f.readText().take(4000)
      JSONObject().apply {
        put("path", path)
        put("bytes", f.length())
        put("content", text)
      }.toString()
    } catch (e: Exception) {
      Log.e(TAG, "Failed readFile", e)
      "failed: ${e.message}"
    }
  }

  suspend fun readCalendarEvents(
    context: Context,
    parameters: String,
    requestPermission: suspend (String) -> Boolean,
  ): String {
    if (
      checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      val granted = requestPermission(Manifest.permission.READ_CALENDAR)
      if (!granted) {
        Log.e(TAG, "READ_CALENDAR permission denied by user")
        return "failed: READ_CALENDAR permission denied by user"
      }
    }

    try {
      val moshi = Moshi.Builder().build()
      val jsonAdapter = moshi.adapter(ReadCalendarEventsParams::class.java)
      val params = jsonAdapter.fromJson(parameters)
      if (params != null) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateObj = format.parse(params.date)
        if (dateObj != null) {
          val cal =
            Calendar.getInstance().apply {
              timeInMillis = dateObj.time
              set(Calendar.HOUR_OF_DAY, 0)
              set(Calendar.MINUTE, 0)
              set(Calendar.SECOND, 0)
              set(Calendar.MILLISECOND, 0)
            }
          val startOfDayMillis = cal.timeInMillis

          cal.apply {
            add(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MILLISECOND, -1)
          }
          val endOfDayMillis = cal.timeInMillis

          val projection =
            arrayOf(Instances.TITLE, Instances.DESCRIPTION, Instances.BEGIN, Instances.END)

          val builder = Instances.CONTENT_URI.buildUpon()
          ContentUris.appendId(builder, startOfDayMillis)
          ContentUris.appendId(builder, endOfDayMillis)

          val cursor =
            context.contentResolver.query(
              builder.build(),
              projection,
              null,
              null,
              "${Instances.BEGIN} ASC",
            )

          val eventsList = mutableListOf<CalendarEventDto>()
          cursor?.use { c ->
            val titleIdx = c.getColumnIndex(Instances.TITLE)
            val descIdx = c.getColumnIndex(Instances.DESCRIPTION)
            val startIdx = c.getColumnIndex(Instances.BEGIN)
            val endIdx = c.getColumnIndex(Instances.END)
            val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            while (c.moveToNext()) {
              val title = if (titleIdx >= 0) c.getString(titleIdx) ?: "" else ""
              val desc = if (descIdx >= 0) c.getString(descIdx) ?: "" else ""
              val start = if (startIdx >= 0) c.getLong(startIdx) else 0L
              val end = if (endIdx >= 0) c.getLong(endIdx) else 0L
              eventsList.add(
                CalendarEventDto(
                  title = title,
                  description = desc,
                  begin_time = if (start > 0) timeFormat.format(Date(start)) else "",
                  end_time = if (end > 0) timeFormat.format(Date(end)) else "",
                )
              )
            }
          }
          val responseAdapter = moshi.adapter(ReadCalendarEventsResponse::class.java)
          return responseAdapter.toJson(ReadCalendarEventsResponse(eventsList))
        } else {
          Log.e(TAG, "Failed to parse read_calendar_events date: ${params.date}")
          return "failed"
        }
      } else {
        Log.e(TAG, "Failed to parse read_calendar_events parameters: $parameters")
        return "failed"
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read calendar events: $parameters", e)
      return "failed: ${e.message}"
    }
  }

  fun scheduleNotification(context: Context, parameters: String): String {
    try {
      val moshi = Moshi.Builder().build()
      val jsonAdapter = moshi.adapter(ScheduleNotificationParams::class.java)
      val params = jsonAdapter.fromJson(parameters)
      if (params != null) {
        val notificationProtoBuilder =
          ScheduledNotification.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle(params.title)
            .setMessage(params.message)
            .setHour(params.hour)
            .setMinute(params.minute)
            .setChannelId("agent_skill_tasks_channel")
            .setChannelName("Agent Skill Task")
        if (params.deeplink != null) {
          notificationProtoBuilder.setDeeplink(params.deeplink)
        } else if (params.task_id != null && params.model_name != null) {
          val uri =
            "com.google.ai.edge.gallery://model/${params.task_id}/${params.model_name}"
              .toUri()
              .buildUpon()
              .appendQueryParameter("query", params.message)
              .build()
              .toString()
          Log.d(TAG, "Setting constructed deeplink to: $uri")
          notificationProtoBuilder.setDeeplink(uri)
        } else if (params.task_id != null) {
          val uri =
            "com.google.ai.edge.gallery://${params.task_id}/"
              .toUri()
              .buildUpon()
              .appendQueryParameter("query", params.message)
              .build()
              .toString()
          Log.d(TAG, "Setting constructed deeplink to: $uri")
          notificationProtoBuilder.setDeeplink(uri)
        } else {
          val fallbackUri =
            "com.google.ai.edge.gallery://llm_agent_chat/"
              .toUri()
              .buildUpon()
              .appendQueryParameter("query", params.message)
              .build()
              .toString()
          Log.d(TAG, "Setting fallback deeplink to: $fallbackUri")
          notificationProtoBuilder.setDeeplink(fallbackUri)
        }
        if (params.year != null) {
          notificationProtoBuilder.setYear(params.year)
        }
        if (params.month != null) {
          notificationProtoBuilder.setMonth(params.month)
        }
        if (params.day != null) {
          notificationProtoBuilder.setDay(params.day)
        }
        if (params.repeat_daily != null) {
          notificationProtoBuilder.setRepeatDaily(params.repeat_daily)
        }

        val entryPoint =
          EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationScheduleManagerEntryPoint::class.java,
          )
        val success =
          entryPoint
            .notificationScheduleManager()
            .scheduleNotification(notificationProtoBuilder.build())
        if (!success) {
          return "failed"
        }
        return "succeeded"
      } else {
        Log.e(TAG, "Failed to parse schedule_notification parameters: $parameters")
        return "failed"
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse schedule_notification parameters: $parameters", e)
      return "failed"
    }
  }
}
