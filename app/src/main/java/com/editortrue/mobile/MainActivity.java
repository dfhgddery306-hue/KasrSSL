

package com.editortrue.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive",
                "application/zip",
                "application/octet-stream"
            });
            startActivityForResult(i, PICK_APK);
        });
    }

    @Override
    protected void onActivityResult(
        int req, int res, Intent data
    ) {
        super.onActivityResult(req, res, data);

        if (req == PICK_APK && res == RESULT_OK
                && data != null) {
            selectedUri = data.getData();

            String name = "Selected file";

            Cursor c = getContentResolver().query(
                selectedUri, null, null, null, null
            );

            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int idx = c.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        );
                        if (idx >= 0) name = c.getString(idx);
                    }
                } finally {
                    c.close();
                }
            }

            web.evaluateJavascript(
                "window.setSelectedFile && window.setSelectedFile("
                    + JSONObject.quote(name) + ")",
                null
            );

            analyzeSelected();
        }
    }

    @JavascriptInterface
    public void analyzeSelected() {
        final Uri uri = selectedUri;

        if (uri == null) {
            sendResult(false, "اختر ملف APK أولاً");
            return;
        }

        executor.execute(() -> {
            File temp = null;

            try {
                temp = File.createTempFile(
                    "apk_analysis_", ".zip", getCacheDir()
                );

                try (InputStream in =
                    getContentResolver().openInputStream(uri);
                     OutputStream out =
                    new FileOutputStream(temp)) {

                    if (in == null) throw new Exception(
                        "تعذر قراءة الملف"
                    );

                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("fileSize", temp.length());

                boolean hasManifest = false;
                boolean hasDex = false;
                int entries = 0;
                int nestedApks = 0;

                try (ZipFile zip = new ZipFile(temp)) {
                    Enumeration<? extends java.util.zip.ZipEntry> e =
                        zip.entries();

                    while (e.hasMoreElements()) {
                        String name = e.nextElement().getName();
                        entries++;

                        if (name.equals("AndroidManifest.xml"))
                            hasManifest = true;

                        if (name.matches("classes([0-9]+)?\\.dex"))
                            hasDex = true;

                        if (name.toLowerCase().endsWith(".apk"))
                            nestedApks++;
                    }
                }

                result.put("entries", entries);
                result.put("hasManifest", hasManifest);
                result.put("hasDex", hasDex);
                result.put("nestedApks", nestedApks);

                PackageManager pm = getPackageManager();

                PackageInfo info = pm.getPackageArchiveInfo(
                    temp.getAbsolutePath(),
                    PackageManager.GET_ACTIVITIES |
                    PackageManager.GET_PERMISSIONS
                );

                if (info != null) {
                    result.put("packageName", info.packageName);
                    result.put("versionName",
                        info.versionName == null
                            ? "Unknown" : info.versionName
                    );
                    result.put("versionCode",
                        info.getLongVersionCode()
                    );

                    if (info.applicationInfo != null) {
                        info.applicationInfo.sourceDir =
                            temp.getAbsolutePath();
                        info.applicationInfo.publicSourceDir =
                            temp.getAbsolutePath();

                        result.put("appName",
                            pm.getApplicationLabel(
                                info.applicationInfo
                            ).toString()
                        );
                    }
                } else {
                    result.put("packageName", "غير متاح");
                    result.put("appName", "غير معروف");
                }

                result.put("validZip", true);

                if (hasManifest && hasDex) {
                    result.put("message",
                        "تم فحص بنية APK بنجاح"
                    );
                } else if (nestedApks > 0) {
                    result.put("message",
                        "يبدو أنه أرشيف يحتوي على ملفات APK"
                    );
                } else {
                    result.put("message",
                        "الأرشيف لا يحتوي على بنية APK كاملة"
                    );
                }

                sendResult(true, result.toString());

            } catch (Exception ex) {
                sendResult(false,
                    "فشل الفحص: " + ex.getMessage()
                );
            } finally {
                if (temp != null) temp.delete();
            }
        });
    }

    private void sendResult(boolean success, String data) {
        runOnUiThread(() -> {
            try {
                JSONObject obj = success
                    ? new JSONObject(data)
                    : new JSONObject().put("success", false)
                        .put("message", data);

                web.evaluateJavascript(
                    "window.onAnalysisResult && " +
                    "window.onAnalysisResult(" + obj + ")",
                    null
                );
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}

