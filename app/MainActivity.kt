package com.hamza.kasrssl

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var selectedName = "لم يتم اختيار ملف"
    private var selectedUri: Uri? = null

    private val blue = Color.rgb(0, 174, 255)
    private val red = Color.rgb(255, 35, 55)
    private val bg = Color.rgb(5, 9, 20)
    private val panel = Color.rgb(13, 23, 42)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildHome()
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 24)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val title = TextView(this).apply {
            text = "كسر SSL"
            textSize = 32f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 4)
        }
        content.addView(title)
        val subtitle = TextView(this).apply {
            text = "أدوات فحص وتحليل ملفات APK"
            textSize = 14f
            setTextColor(blue)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        content.addView(subtitle)

        val banner = ImageView(this).apply {
            setImageResource(com.hamza.kasrssl.R.drawable.header_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "شعار كسر SSL"
        }
        content.addView(banner, LinearLayout.LayoutParams(-1, 190))

        addCard(content, "اختيار ملف APK", "حدد تطبيقاً من هاتفك لفحص معلوماته") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
            }
            startActivityForResult(intent, 100)
        }
        addCard(content, "فحص معلومات APK", "اسم الحزمة والإصدار والحجم والتوقيع") {
            inspectApk()
        }
        addCard(content, "فحص إعدادات الشبكة", "عرض مؤشرات إعدادات الشبكة المتاحة") {
            showInfo("فحص إعدادات SSL", "هذه النسخة تعرض معلومات الحزمة الأساسية فقط. لا تقوم بتجاوز SSL Pinning أو تعطيل التحقق من الشهادات.")
        }
        addCard(content, "أدوات APK", "معلومات الملف وإدارة النسخة المختارة") {
            showInfo("أدوات APK", "اختر ملف APK أولاً، ثم استخدم فحص معلومات APK. لا يتم تعديل حماية التطبيقات أو إعادة توقيعها في هذه النسخة.")
        }
        addCard(content, "سجل العمليات", "عرض آخر نشاط داخل التطبيق") {
            showInfo("سجل العمليات", "آخر ملف: $selectedName")
        }

        status = TextView(this).apply {
            text = "الحالة: جاهز"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 8)
        }
        root.addView(status)
        setContentView(root)
    }

    private fun addCard(parent: LinearLayout, heading: String, detail: String, action: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 15, 18, 15)
            setBackgroundColor(panel)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val h = TextView(this).apply {
            text = heading
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (heading.contains("فحص")) blue else Color.WHITE)
        }
        val d = TextView(this).apply {
            text = detail
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 5, 0, 0)
        }
        box.addView(h); box.addView(d)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 10, 0, 0)
        parent.addView(box, lp)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedUri = uri
                selectedName = queryName(uri) ?: "APK مختار"
                status.text = "تم اختيار: $selectedName"
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    private fun inspectApk() {
        val uri = selectedUri
        if (uri == null) {
            showInfo("لا يوجد ملف", "اختر ملف APK من هاتفك أولاً.")
            return
        }
        try {
            val apk = File(cacheDir, "selected-analysis.apk")
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw Exception("تعذر قراءة الملف")
                FileOutputStream(apk).use { output -> input.copyTo(output) }
            }
            val flags = if (Build.VERSION.SDK_INT >= 33)
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            else @Suppress("DEPRECATION") PackageManager.GET_PERMISSIONS
            val info = if (Build.VERSION.SDK_INT >= 33)
                packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            else @Suppress("DEPRECATION") packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_PERMISSIONS)

            if (info == null) throw Exception("الملف غير صالح أو غير قابل للقراءة كحزمة APK")
            val appInfo = info.applicationInfo
            appInfo.sourceDir = apk.absolutePath
            appInfo.publicSourceDir = apk.absolutePath
            val appName = appInfo.loadLabel(packageManager).toString()
            val perms = info.requestedPermissions?.joinToString("\n") ?: "لا توجد صلاحيات معلنة"
            val report = """
اسم الملف: $selectedName
اسم التطبيق: $appName
اسم الحزمة: ${info.packageName}
الإصدار: ${info.versionName ?: "غير معروف"}
حجم الملف: ${apk.length() / 1024} KB

الصلاحيات:
$perms

ملاحظة: هذا فحص معلومات أساسي، وليس حكماً على أمان التطبيق.
            """.trimIndent()
            showInfo("نتيجة فحص APK", report)
            status.text = "اكتمل فحص: $selectedName"
        } catch (e: Exception) {
            showInfo("تعذر الفحص", e.message ?: "حدث خطأ غير معروف")
        }
    }

    private fun showInfo(title: String, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("حسناً", null)
            .show()
    }
}
