package com.ubcsc.checkout.data

import com.ubcsc.checkout.viewmodel.CraftFleetStatus
import com.ubcsc.checkout.viewmodel.FleetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object FleetStatusRepository {

    private const val URL =
        "https://raw.githubusercontent.com/ubcsailingclub/digital_checkout/master/fleet_status.json"

    suspend fun fetch(): FleetStatus? = withContext(Dispatchers.IO) {
        try {
            val json = java.net.URL(URL).readText()
            parse(json)
        } catch (_: Exception) {
            null   // network unavailable — keep last known status
        }
    }

    private fun parse(json: String): FleetStatus {
        val obj              = JSONObject(json)
        val fleetGrounded    = obj.optBoolean("fleet_grounded", false)
        val fleetGroundReason = obj.optString(
            "fleet_ground_reason",
            "Conditions have been deemed unsafe. You may still proceed, but sail at your own risk."
        )
        val craftObj = obj.optJSONObject("craft") ?: JSONObject()

        // First pass: derive code→class from _comment_* section headers (JSONObject
        // uses LinkedHashMap on Android so insertion order is preserved).
        val classMap = mutableMapOf<String, String>()
        var currentClass = ""
        craftObj.keys().forEach { key ->
            if (key.startsWith("_")) {
                val raw = craftObj.optString(key)
                currentClass = raw
                    .replace(Regex("^─+\\s*"), "")
                    .replace(Regex("\\s*─+$"), "")
                    .trim()
                    .ifEmpty { key.removePrefix("_comment_").replace('_', ' ') }
            } else {
                classMap[key] = currentClass
            }
        }

        val craft = mutableMapOf<String, CraftFleetStatus>()
        craftObj.keys().forEach { key ->
            if (key.startsWith("_")) return@forEach
            val c        = craftObj.optJSONObject(key) ?: return@forEach
            val active   = c.optBoolean("active", true)
            val grounded = c.optBoolean("grounded", false)
            craft[key] = CraftFleetStatus(
                status = when {
                    !active  -> "deactivated"
                    grounded -> "grounded"
                    else     -> "active"
                },
                reason     = c.optString("reason").takeIf { it.isNotEmpty() },
                name       = c.optString("name").takeIf { it.isNotEmpty() },
                craftClass = c.optString("class").takeIf { it.isNotEmpty() } ?: classMap[key]
            )
        }
        return FleetStatus(fleetGrounded, fleetGroundReason, craft)
    }
}
