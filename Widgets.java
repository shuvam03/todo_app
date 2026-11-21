package com.me.todoapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Widgets {
    static final String PREFS = "widget", TICK = "com.me.todoapp.TICK";
    static final int[] ROWS = {R.id.r0, R.id.r1, R.id.r2, R.id.r3};
    static final int[] CHECKS = {R.id.c0, R.id.c1, R.id.c2, R.id.c3};
    static final int[] TITLES = {R.id.t0, R.id.t1, R.id.t2, R.id.t3};
    static final int[] TIMES = {R.id.m0, R.id.m1, R.id.m2, R.id.m3};

    static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREFS, 0); }
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    public static void save(Context c, String json) { sp(c).edit().putString("json", json).apply(); updateAll(c); }

    public static synchronized String takePending(Context c) {
        String s = sp(c).getString("pending", "[]");
        sp(c).edit().putString("pending", "[]").apply();
        return s;
    }

    public static synchronized void tick(Context c, int id) {
        try {
            JSONArray p = new JSONArray(sp(c).getString("pending", "[]"));
            p.put(id);
            JSONObject o = new JSONObject(sp(c).getString("json", "{}"));
            JSONArray items = o.optJSONArray("items"), keep = new JSONArray();
            if (items != null) for (int i = 0; i < items.length(); i++) if (items.getJSONObject(i).optInt("id") != id) keep.put(items.getJSONObject(i));
            o.put("items", keep);
            sp(c).edit().putString("pending", p.toString()).putString("json", o.toString()).apply();
        } catch (Exception e) { }
        updateAll(c);
    }

    static List<JSONObject> todays(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray items = new JSONObject(sp(c).getString("json", "{}")).optJSONArray("items");
            String t = today();
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                if (o.optString("day").compareTo(t) <= 0) out.add(o);
            }
        } catch (Exception e) { }
        return out;
    }

    static String nextMeeting(Context c) {
        try {
            JSONArray m = new JSONObject(sp(c).getString("json", "{}")).optJSONArray("meets");
            String t = today(), now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            if (m != null) for (int i = 0; i < m.length(); i++) {
                JSONObject o = m.getJSONObject(i);
                if (o.optString("day").equals(t) && o.optString("t").compareTo(now) >= 0) return "Meeting " + o.optString("time") + " · " + o.optString("title");
            }
        } catch (Exception e) { }
        return null;
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String label(JSONObject o) {
        if (o.optString("day").compareTo(today()) < 0) return "Overdue";
        String t = o.optString("time");
        return t.isEmpty() ? "Any time" : t;
    }

    static RemoteViews small(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_small);
        List<JSONObject> l = todays(c);
        v.setTextViewText(R.id.w_count, String.valueOf(l.size()));
        v.setTextViewText(R.id.w_label, l.size() == 1 ? "task left today" : "tasks left today");
        String meet = nextMeeting(c);
        String next = !l.isEmpty() ? label(l.get(0)) + " · " + l.get(0).optString("title") : meet != null ? meet : "All clear for today";
        v.setTextViewText(R.id.w_next, next);
        v.setOnClickPendingIntent(R.id.w_root, openApp(c));
        return v;
    }

    static RemoteViews medium(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_medium);
        List<JSONObject> l = todays(c);
        v.setTextViewText(R.id.w_date, new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date()));
        v.setTextViewText(R.id.w_count, l.size() + " left");
        for (int i = 0; i < ROWS.length; i++) {
            if (i < l.size()) {
                JSONObject o = l.get(i);
                int id = o.optInt("id");
                v.setViewVisibility(ROWS[i], View.VISIBLE);
                v.setTextViewText(TITLES[i], o.optString("title"));
                v.setTextViewText(TIMES[i], label(o));
                v.setTextColor(TIMES[i], label(o).equals("Overdue") ? Color.parseColor("#E03A40") : Color.parseColor("#6B7186"));
                try { v.setInt(CHECKS[i], "setColorFilter", Color.parseColor(o.optString("color", "#5B5FEF"))); } catch (Exception e) { }
                Intent t = new Intent(c, WidgetMedium.class).setAction(TICK).putExtra("id", id);
                v.setOnClickPendingIntent(CHECKS[i], PendingIntent.getBroadcast(c, id, t, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                v.setOnClickPendingIntent(TITLES[i], openApp(c));
            } else v.setViewVisibility(ROWS[i], View.GONE);
        }
        v.setViewVisibility(R.id.w_empty, l.isEmpty() ? View.VISIBLE : View.GONE);
        if (l.size() > ROWS.length) { v.setViewVisibility(R.id.w_more, View.VISIBLE); v.setTextViewText(R.id.w_more, "+" + (l.size() - ROWS.length) + " more"); }
        else v.setViewVisibility(R.id.w_more, View.GONE);
        String meet = nextMeeting(c);
        if (meet != null) { v.setViewVisibility(R.id.w_meet, View.VISIBLE); v.setTextViewText(R.id.w_meet, meet); }
        else v.setViewVisibility(R.id.w_meet, View.GONE);
        v.setOnClickPendingIntent(R.id.w_head, openApp(c));
        return v;
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, WidgetSmall.class))) m.updateAppWidget(id, small(c));
        for (int id : m.getAppWidgetIds(new ComponentName(c, WidgetMedium.class))) m.updateAppWidget(id, medium(c));
    }
}
