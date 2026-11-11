
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
            FileOutputStream(apk).use { output ->
                input.copyTo(output)
            }
        }

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_PERMISSIONS
            )
        }

        if (info == null) {
            throw Exception("الملف غير صالح أو غير قابل للقراءة كحزمة APK")
        }

        val appInfo = info.applicationInfo
            ?: throw Exception("تعذر قراءة معلومات التطبيق")

        appInfo.sourceDir = apk.absolutePath
        appInfo.publicSourceDir = apk.absolutePath

        val appName = appInfo.loadLabel(packageManager).toString()

        val perms = info.requestedPermissions?.joinToString("\n")
            ?: "لا توجد صلاحيات معلنة"

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
