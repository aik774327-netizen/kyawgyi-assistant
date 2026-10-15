package com.example.permissions

import android.app.AlarmManager
import android.content.Context
import android.os.Build

/**
 * Dedicated Exact Alarm Permission & Capability Manager.
 * Checks SCHEDULE_EXACT_ALARM permission (Android 12+) for precise appliance sleep timers.
 */
class ExactAlarmPermissionManager(private val context: Context) {

  companion object {
    const val MYANMAR_RATIONALE = "ပန်ကာနှင့် စက်ပစ္စည်းများကို အချိန်ကိုက် တိကျစွာ အလိုအလျောက် ပိတ်နိုင်ရန် (Sleep Timer) Exact Alarm ခွင့်ပြုချက် လိုအပ်ပါသည်။"
    const val ENGLISH_RATIONALE = "Exact Alarm permission is required for accurate appliance sleep timers and scheduled automation."
    const val MYANMAR_DENIED_FALLBACK = "Exact Alarm ခွင့်ပြုချက် မရရှိပါက စက္ကန့်တိကျသော နှိုးစက်အစား စံချိန်မီ ပုံမှန်နှိုးစက်စနစ် (Standard Alarm) ဖြင့် အစားထိုး အသုံးပြုပါမည် (အနည်းငယ် အချိန်ကွာဟမှု ရှိနိုင်ပါသည်)။"
    const val ENGLISH_DENIED_FALLBACK = "Without Exact Alarm permission, sleep timers gracefully degrade to standard non-exact alarms with minor timing variance."
  }

  fun canScheduleExactAlarms(): Boolean {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      am?.canScheduleExactAlarms() ?: true
    } else {
      true
    }
  }
}
