package com.editortrue.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;

public class MainActivity extends Activity {
    private static final int PICK_APK = 41;
    private WebView web;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(this, "AndroidBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @JavascriptInterface public void chooseApk() {
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive", "application/zip", "application/octet-stream"
            });
            startActivityForResult(i, PICK_APK);
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_APK && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            String name = "ملف مختار";
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try { if (c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }} finally { c.close(); }
            }
            String safe = name.replace("\\", "\\\\").replace("'", "\\'");
            web.evaluateJavascript("window.setSelectedFile('" + safe + "')", null);
        }
    }
}
