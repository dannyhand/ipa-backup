package us.dannyhand.ipabackup.device

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns lockdown values and diagnostics into a readable text report. */
object DeviceReport {
    /** ProductType → marketing name, for the devices this app is likely to meet. */
    private val MODELS = mapOf(
        "iPod1,1" to "iPod touch (1st generation)", "iPod2,1" to "iPod touch (2nd generation)",
        "iPod3,1" to "iPod touch (3rd generation)", "iPod4,1" to "iPod touch (4th generation)",
        "iPod5,1" to "iPod touch (5th generation)", "iPod7,1" to "iPod touch (6th generation)",
        "iPod9,1" to "iPod touch (7th generation)",
        "iPhone1,1" to "iPhone", "iPhone1,2" to "iPhone 3G", "iPhone2,1" to "iPhone 3GS",
        "iPhone3,1" to "iPhone 4", "iPhone3,2" to "iPhone 4", "iPhone3,3" to "iPhone 4 (CDMA)", "iPhone4,1" to "iPhone 4S",
        "iPhone5,1" to "iPhone 5", "iPhone5,2" to "iPhone 5", "iPhone5,3" to "iPhone 5c", "iPhone5,4" to "iPhone 5c",
        "iPhone6,1" to "iPhone 5s", "iPhone6,2" to "iPhone 5s",
        "iPad1,1" to "iPad", "iPad2,1" to "iPad 2", "iPad2,2" to "iPad 2", "iPad2,3" to "iPad 2", "iPad2,4" to "iPad 2",
        "iPad2,5" to "iPad mini", "iPad2,6" to "iPad mini", "iPad2,7" to "iPad mini",
        "iPad3,1" to "iPad (3rd generation)", "iPad3,2" to "iPad (3rd generation)", "iPad3,3" to "iPad (3rd generation)",
        "iPad3,4" to "iPad (4th generation)", "iPad3,5" to "iPad (4th generation)", "iPad3,6" to "iPad (4th generation)",
    )

    fun modelName(productType: String?) = productType?.let { MODELS[it] ?: it } ?: "?"

    fun size(bytes: Long?): String = when {
        bytes == null || bytes < 0 -> "?"
        bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
        else -> "%.1f MB".format(bytes / 1048576.0)
    }

    private fun num(v: Any?): Long? = (v as? Number)?.toLong()

    class Input(
        val info: Map<String, Any?>,
        val disk: Map<String, Any?>?,
        val battery: Map<String, Any?>?,
        val gasGauge: Map<String, Any?>?,
        val powerSource: Map<String, Any?>?,
        val jailbreak: String,
        val deviceTime: Long?,
    )

    fun text(d: Input, generatedMs: Long = System.currentTimeMillis()): String = buildString {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun line(label: String, value: Any?) {
            val v = value?.toString()?.takeIf { it.isNotBlank() } ?: return
            append(label.padEnd(24)).append(v).append('\n')
        }
        fun section(t: String) { append('\n').append(t).append('\n').append("-".repeat(t.length)).append('\n') }
        val i = d.info
        append("Device report: ").append(i["DeviceName"] ?: "device").append('\n')
        append("Generated ").append(fmt.format(Date(generatedMs))).append(" by IPA Backup\n")

        section("Device")
        line("Model", modelName(i["ProductType"] as? String) + " (${i["ProductType"] ?: "?"}, ${i["HardwareModel"] ?: "?"})")
        line("Model number", listOfNotNull(i["ModelNumber"], i["RegionInfo"]).joinToString(""))
        line("iOS", "${i["ProductVersion"] ?: "?"} (${i["BuildVersion"] ?: "?"})")
        line("Baseband", i["BasebandVersion"])
        line("Serial number", i["SerialNumber"])
        line("UDID", i["UniqueDeviceID"])
        line("Wi-Fi address", i["WiFiAddress"])
        line("Bluetooth address", i["BluetoothAddress"])
        line("Color", listOfNotNull(i["DeviceColor"], i["DeviceEnclosureColor"]).joinToString(" / "))
        line("Activation", i["ActivationState"])
        line("Jailbreak", d.jailbreak)
        line("Time zone", i["TimeZone"])
        d.deviceTime?.let { line("Device clock", fmt.format(Date(it * 1000)) + if (kotlin.math.abs(it * 1000 - generatedMs) > 2 * 86_400_000L) "  ⚠ wrong date" else "") }

        section("Storage")
        val disk = d.disk
        if (disk == null) append("(not available)\n") else {
            val total = num(disk["TotalDiskCapacity"])
            val dataCap = num(disk["TotalDataCapacity"])
            val dataFree = num(disk["TotalDataAvailable"]) ?: num(disk["AmountDataAvailable"])
            val sysCap = num(disk["TotalSystemCapacity"])
            val sysFree = num(disk["TotalSystemAvailable"])
            line("Capacity", size(total))
            if (dataCap != null) line("Data partition", "${size(dataCap)}, ${size(dataFree)} free, ${size(dataCap - (dataFree ?: 0))} used")
            if (sysCap != null) line("System partition", "${size(sysCap)}, ${size(sysFree)} free")
            line("Apps", num(disk["MobileApplicationUsage"])?.let(::size))
            line("Camera roll", num(disk["CameraUsage"])?.let(::size))
            line("Photos (synced)", num(disk["PhotoUsage"])?.let(::size))
            line("Voice memos", num(disk["VoiceMemoUsage"])?.let(::size))
        }

        section("Battery")
        val b = d.battery
        val g = d.gasGauge
        val p = d.powerSource
        line("Charge", num(b?.get("BatteryCurrentCapacity"))?.let { "$it%" } ?: num(p?.get("CurrentCapacity"))?.let { "$it%" })
        line("Charging", (b?.get("BatteryIsCharging") ?: p?.get("IsCharging"))?.let { if (it == true) "yes" else "no" })
        line("Plugged in", (b?.get("ExternalConnected") ?: p?.get("ExternalConnected"))?.let { if (it == true) "yes" else "no" })
        val cycles = num(g?.get("CycleCount")) ?: num(p?.get("CycleCount"))
        val design = num(g?.get("DesignCapacity")) ?: num(p?.get("DesignCapacity"))
        val full = num(g?.get("FullChargeCapacity")) ?: num(p?.get("AppleRawMaxCapacity")) ?: num(p?.get("MaxCapacity"))?.takeIf { it > 100 }
        line("Cycle count", cycles)
        if (design != null) line("Design capacity", "$design mAh")
        if (full != null) line("Full-charge capacity", "$full mAh")
        if (design != null && full != null && design > 0) line("Health", "%d%% of design capacity".format(full * 100 / design))
        num(p?.get("Temperature"))?.let { line("Temperature", "%.1f °C".format(it / 100.0)) }
        num(g?.get("Voltage") ?: p?.get("Voltage"))?.let { line("Voltage", "%.2f V".format(it / 1000.0)) }
        if (cycles == null && design == null) append("(cycle count and capacity aren't reported by this iOS version)\n")
    }
}
