package com.me.todoapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Reminders {
    static final String PREFS = "reminders";

    static PendingIntent pi(Context c, JSONObject o) throws JSONException {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("id", o.getInt("id"));
        i.putExtra("title", o.optString("title"));
        i.putExtra("text", o.optString("text"));
        return PendingIntent.getBroadcast(c, o.getInt("id"), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static synchronized void sync(Context c, String json) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, 0);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray old = new JSONArray(sp.getString("json", "[]"));
            for (int i = 0; i < old.length(); i++) am.cancel(pi(c, old.getJSONObject(i)));
            JSONArray arr = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.getLong("at");
                if (at <= now) continue;
                PendingIntent p = pi(c, o);
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
            sp.edit().putString("json", json).apply();
        } catch (Exception e) { }
    }

    public static void restore(Context c) { sync(c, c.getSharedPreferences(PREFS, 0).getString("json", "[]")); }
}
