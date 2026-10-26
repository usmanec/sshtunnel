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

    fun getOemInstructions(brand: OemBrand): List<String> {
        return when (brand) {
            OemBrand.XIAOMI -> listOf(
                "1. Автозапуск (Autostart): Включите тумблер для этого приложения.",
                "2. Контроль активности (MIUI Battery Saver): Выберите «Нет ограничений» (No restrictions), иначе MIUI закрывает SSH-туннель после блокировки экрана.",
                "3. Меню недавних (Recents): Зажмите карточку приложения и нажмите значок «Замок», чтобы система не очищала процесс из памяти."
            )
            OemBrand.HUAWEI -> listOf(
                "1. Запуск приложений (App Launch): Переключите управление с «Автоматически» на «Вручную».",
                "2. Разрешите все три пункта: Автозапуск, Косвенный запуск и Работа в фоновом режиме.",
                "3. Закрепите приложение в списке недавних задач (замочек)."
            )
            OemBrand.SAMSUNG -> listOf(
                "1. Батарея -> Ограничения в фоновом режиме: Добавьте приложение в «Приложения, которые никогда не спят» (Never sleeping apps).",
                "2. Отключите пункт «Переводить неиспользуемые приложения в режим сна».",
                "3. Отключите оптимизацию расхода аккумулятора."
            )
            OemBrand.OPPO_REALME -> listOf(
                "1. Управление автозапуском: Разрешите автозапуск приложения.",
                "2. Расход заряда в фоновом режиме: Разрешите неограниченную фоновую активность.",
                "3. Зафиксируйте приложение замком в списке недавних."
            )
            OemBrand.VIVO -> listOf(
                "1. Управление питанием в фоновом режиме: Выберите «Высокое энергопотребление в фоновом режиме».",
                "2. Автозапуск: Разрешите автоматический запуск приложения.",
                "3. Закрепите в недавних."
            )
            OemBrand.GENERIC -> listOf(
                "1. Батарея -> Оптимизация батареи: Выберите «Не оптимизировать» / «Без ограничений».",
                "2. Фоновая передача данных: Разрешите передачу данных без ограничений.",
                "3. Всегда включенный VPN: В настройках Android включите «Постоянная VPN-сеть»."
            )
        }
    }
}
