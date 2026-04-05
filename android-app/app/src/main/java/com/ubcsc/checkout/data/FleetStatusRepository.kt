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
        val craft    = mutableMapOf<String, CraftFleetStatus>()
        craftObj.keys().forEach { key ->
            if (key.startsWith("_")) return@forEach   // skip _comment_* fields
            val c      = craftObj.optJSONObject(key) ?: return@forEach
            val active = c.optBoolean("active", true)
            craft[key] = CraftFleetStatus(
                status = if (active) "active" else "deactivated",
                reason = c.optString("reason").takeIf { it.isNotEmpty() }
            )
        }
        return FleetStatus(fleetGrounded, fleetGroundReason, craft)
    }
}
