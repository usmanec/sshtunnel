package top.rootu.ssh.tunnel.ui.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import top.rootu.ssh.tunnel.R

object OemBatteryOptimizationHelper {

    enum class OemBrand(val displayName: String) {
        XIAOMI("Xiaomi / MIUI / HyperOS"),
        HUAWEI("Huawei / EMUI"),
        SAMSUNG("Samsung / OneUI"),
        OPPO_REALME("Oppo / Realme / OnePlus"),
        VIVO("Vivo / Funtouch / OriginOS"),
        GENERIC("Standard Android")
    }

    fun getDetectedBrand(): OemBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemBrand.XIAOMI
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemBrand.HUAWEI
            manufacturer.contains("samsung") -> OemBrand.SAMSUNG
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> OemBrand.OPPO_REALME
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> OemBrand.VIVO
            else -> OemBrand.GENERIC
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            // Fallback
        }

        return try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openOemAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intentsToTry = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                // MIUI / HyperOS Autostart activities
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    )
                )
                intentsToTry.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    ).putExtra("package_name", context.packageName)
                        .putExtra("package_label", context.getString(R.string.app_name))
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                    )
                )
            }
            manufacturer.contains("samsung") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                    )
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                    )
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                    )
                )
                intentsToTry.add(
                    Intent().setComponent(
                        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    )
                )
            }
        }

        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        return openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Step-by-step instructions in the device language (string arrays in resources). */
    fun getOemInstructions(context: Context, brand: OemBrand): List<String> {
        val arrayRes = when (brand) {
            OemBrand.XIAOMI -> R.array.oem_steps_xiaomi
            OemBrand.HUAWEI -> R.array.oem_steps_huawei
            OemBrand.SAMSUNG -> R.array.oem_steps_samsung
            OemBrand.OPPO_REALME -> R.array.oem_steps_oppo
            OemBrand.VIVO -> R.array.oem_steps_vivo
            OemBrand.GENERIC -> R.array.oem_steps_generic
        }
        return context.resources.getStringArray(arrayRes).toList()
    }

    /** Brand names are not translated, only the generic one. */
    fun brandName(context: Context, brand: OemBrand): String =
        if (brand == OemBrand.GENERIC) context.getString(R.string.oem_generic) else brand.displayName
}
