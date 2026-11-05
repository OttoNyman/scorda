package org.tasks.notifications

const val TEST_NOTIFICATION_ID = -2

enum class OemFamily { STOCK, XIAOMI, HUAWEI, OPPO, VIVO }

enum class ReminderCheckId {
    NOTIFICATIONS, EXACT_ALARMS, BATTERY, POWER_SAVE,
    AUTOSTART, BACKGROUND, POPUPS, RECENTS_LOCK,
}

enum class CheckState { OK, PROBLEM, UNKNOWN, MANUAL }

data class ReminderCheck(val id: ReminderCheckId, val state: CheckState)

data class ReminderFacts(
    val sdk: Int,
    val oem: OemFamily,
    val notificationsEnabled: Boolean,
    val channelBlocked: Boolean,
    val canExact: Boolean,
    val batteryUnrestricted: Boolean,
    val powerSaveOn: Boolean,
)

enum class ScheduleMode { EXACT, INEXACT }

data class SchedulePlan(val mode: ScheduleMode, val safetyPoll: Boolean)

enum class ModeLine { ON_TIME, INEXACT, OEM_SAFETY, OEM_AND_INEXACT }

private val aggressive = setOf(OemFamily.XIAOMI, OemFamily.HUAWEI, OemFamily.OPPO, OemFamily.VIVO)

fun oemFamily(manufacturer: String): OemFamily = when (manufacturer.lowercase()) {
    "xiaomi", "redmi", "poco" -> OemFamily.XIAOMI
    "huawei", "honor" -> OemFamily.HUAWEI
    "oppo", "realme", "oneplus" -> OemFamily.OPPO
    "vivo", "iqoo" -> OemFamily.VIVO
    else -> OemFamily.STOCK
}

fun visibleChecks(facts: ReminderFacts): List<ReminderCheck> = buildList {
    add(
        ReminderCheck(
            ReminderCheckId.NOTIFICATIONS,
            if (!facts.notificationsEnabled || facts.channelBlocked) CheckState.PROBLEM else CheckState.OK,
        )
    )
    if (facts.sdk >= 31) {
        add(
            ReminderCheck(
                ReminderCheckId.EXACT_ALARMS,
                if (facts.canExact) CheckState.OK else CheckState.PROBLEM,
            )
        )
    }
    add(
        ReminderCheck(
            ReminderCheckId.BATTERY,
            if (facts.batteryUnrestricted) CheckState.OK else CheckState.PROBLEM,
        )
    )
    if (facts.powerSaveOn) {
        add(ReminderCheck(ReminderCheckId.POWER_SAVE, CheckState.PROBLEM))
    }
    if (facts.oem in aggressive) {
        add(ReminderCheck(ReminderCheckId.AUTOSTART, CheckState.UNKNOWN))
        add(ReminderCheck(ReminderCheckId.BACKGROUND, CheckState.UNKNOWN))
    }
    if (facts.oem == OemFamily.XIAOMI) {
        add(ReminderCheck(ReminderCheckId.POPUPS, CheckState.UNKNOWN))
    }
    if (facts.oem in aggressive) {
        add(ReminderCheck(ReminderCheckId.RECENTS_LOCK, CheckState.MANUAL))
    }
}

fun schedulePlan(facts: ReminderFacts): SchedulePlan {
    val mode = if (facts.sdk < 31 || facts.canExact) ScheduleMode.EXACT else ScheduleMode.INEXACT
    return SchedulePlan(
        mode = mode,
        safetyPoll = mode == ScheduleMode.INEXACT || facts.oem != OemFamily.STOCK,
    )
}

fun modeLine(facts: ReminderFacts): ModeLine {
    val plan = schedulePlan(facts)
    val stock = facts.oem == OemFamily.STOCK
    return when {
        plan.mode == ScheduleMode.EXACT && stock -> ModeLine.ON_TIME
        plan.mode == ScheduleMode.INEXACT && stock -> ModeLine.INEXACT
        plan.mode == ScheduleMode.EXACT -> ModeLine.OEM_SAFETY
        else -> ModeLine.OEM_AND_INEXACT
    }
}
