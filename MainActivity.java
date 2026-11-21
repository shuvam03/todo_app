package com.me.todoapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private WebView web;
    private String pendingExport;
    static final int REQ_VOICE = 7, REQ_EXPORT = 8, REQ_IMPORT = 9;

    void js(final String code) { web.post(() -> web.evaluateJavascript(code, null)); }

    public class Bridge {
        @JavascriptInterface public void sync(String json) { Reminders.sync(getApplicationContext(), json); }
        @JavascriptInterface public void widget(String json) { Widgets.save(getApplicationContext(), json); }
        @JavascriptInterface public String takePending() { return Widgets.takePending(getApplicationContext()); }
        @JavascriptInterface public void haptic() {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                else if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(18);
            } catch (Exception e) { }
        }
        @JavascriptInterface public void voice() {
            runOnUiThread(() -> {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your task, e.g. \"Call Dana tomorrow at 3 pm\"");
                try { startActivityForResult(i, REQ_VOICE); }
                catch (Exception e) { js("window.__onVoiceEnd&&window.__onVoiceEnd('unavailable')"); }
            });
        }
        @JavascriptInterface public void exportBackup(String json, String name) {
            pendingExport = json;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { js("window.__onExported&&window.__onExported(false)"); }
            });
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "TodoNative");
        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    @Override
    protected void onResume() { super.onResume(); if (web != null) js("window.__onResume&&window.__onResume()"); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VOICE) {
            ArrayList<String> r = (res == RESULT_OK && data != null) ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
            if (r != null && !r.isEmpty()) js("window.__onVoice&&window.__onVoice(" + JSONObject.quote(r.get(0)) + ")");
            else js("window.__onVoiceEnd&&window.__onVoiceEnd()");
        } else if (req == REQ_EXPORT) {
            boolean ok = false;
            if (res == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
                try (OutputStream o = getContentResolver().openOutputStream(data.getData(), "wt")) { o.write(pendingExport.getBytes("UTF-8")); ok = true; } catch (Exception e) { }
            }
            pendingExport = null;
            js("window.__onExported&&window.__onExported(" + ok + ")");
        } else if (req == REQ_IMPORT && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try (InputStream in = getContentResolver().openInputStream(u); BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                js("window.__onImport&&window.__onImport(" + JSONObject.quote(sb.toString()) + ")");
            } catch (Exception e) { }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__onBack&&window.__onBack())?'1':'0'", r -> {
            if (r == null || !r.contains("1")) moveTaskToBack(true);
        });
    }
}
