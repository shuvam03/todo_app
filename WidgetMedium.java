package com.me.todoapp;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetMedium extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { Widgets.updateAll(c); }

    @Override
    public void onReceive(Context c, Intent in) {
        if (Widgets.TICK.equals(in.getAction())) { Widgets.tick(c, in.getIntExtra("id", -1)); return; }
        super.onReceive(c, in);
    }
}
