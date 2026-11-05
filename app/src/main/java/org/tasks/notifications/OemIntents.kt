package org.tasks.notifications

import android.content.ComponentName
import android.content.Intent

fun oemCandidates(id: ReminderCheckId, packageName: String, appLabel: String): List<Intent> =
    when (id) {
        ReminderCheckId.AUTOSTART -> autostart()
        ReminderCheckId.BACKGROUND -> background(packageName, appLabel)
        ReminderCheckId.POPUPS -> popups(packageName)
        else -> emptyList()
    }

private fun autostart(): List<Intent> = listOf(
    component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
)

private fun background(packageName: String, appLabel: String): List<Intent> = listOf(
    Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST"),
    component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity").apply {
        putExtra("package_name", packageName)
        putExtra("package_label", appLabel)
    },
    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
)

private fun popups(packageName: String): List<Intent> = listOf(
    Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", packageName),
    component("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity").apply {
        putExtra("extra_pkgname", packageName)
    },
)

private fun component(packageName: String, className: String): Intent =
    Intent().setComponent(ComponentName(packageName, className))
