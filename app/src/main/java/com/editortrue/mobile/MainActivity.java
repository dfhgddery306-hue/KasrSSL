
package com.editortrue.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.*;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_APK = 41;
    private WebView web;
    private Uri selectedUri;
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(this, "AndroidBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @JavascriptInterface
    public void chooseApk() {
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_APK);
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_APK && res == RESULT_OK && data != null) {
            selectedUri = data.getData();
            String name = "APK selected";

            try (Cursor c = getContentResolver().query(
                selectedUri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            } catch (Exception ignored) {}

            web.evaluateJavascript(
                "window.setSelectedFile && window.setSelectedFile("
                + JSONObject.quote(name) + ")", null);
        }
    }

    @JavascriptInterface
    public void analyzeSelected() {
        Uri uri = selectedUri;
        if (uri == null) {
            report("اختر ملف APK أولاً");
            return;
        }

        executor.execute(() -> {
            File temp = null;
            try {
                temp = File.createTempFile(
                    "apk_", ".zip", getCacheDir());

                try (InputStream in =
                    getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(temp)) {

                    if (in == null) throw new Exception("تعذر قراءة الملف");
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) != -1) out.write(b, 0, n);
                }

                int entries = 0;
                boolean manifest = false;
                boolean dex = false;

                try (ZipFile zip = new ZipFile(temp)) {
                    Enumeration<? extends java.util.zip.ZipEntry> e =
                        zip.entries();
                    while (e.hasMoreElements()) {
                        String name = e.nextElement().getName();
                        entries++;
                        if (name.equals("AndroidManifest.xml"))
                            manifest = true;
                        if (name.matches("classes([0-9]+)?\\.dex"))
                            dex = true;
                    }
                }

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("fileSize", temp.length());
                result.put("entries", entries);
                result.put("hasManifest", manifest);
                result.put("hasDex", dex);
                result.put("message",
                    manifest && dex
                    ? "تم فحص بنية APK بنجاح"
                    : "لم يتم العثور على بنية APK كاملة");

                String js = "window.onAnalysisResult && window.onAnalysisResult("
                    + result.toString() + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));

            } catch (Exception ex) {
                report("فشل الفحص: " + ex.getMessage());
            } finally {
                if (temp != null) temp.delete();
            }
        });
    }

    private void report(String message) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.onAnalysisResult && window.onAnalysisResult("
            + JSONObject.quote(message) + ")", null));
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}

