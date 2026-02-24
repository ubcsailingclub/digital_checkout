package com.ubcsc.checkout.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ubcsc.checkout.viewmodel.Craft

/**
 * Reads fleet configuration from assets/fleet.json.
 *
 * The JSON file defines which boats exist and their codes/names.
 * Availability (is_available) is NOT stored here — it comes from the API at runtime.
 * Edit fleet.json to add, remove, or rename boats without recompiling.
 */
class FleetRepository(private val context: Context) {

    private data class BoatEntry(
        val code: String,
        val name: String
    )

    private data class FleetEntry(
        @SerializedName("class") val craftClass: String,
        val boats: List<BoatEntry>
    )

    private data class FleetConfig(
        val fleets: List<FleetEntry>
    )

    fun loadCrafts(): List<Craft> {
        return try {
            val json = context.assets.open("fleet.json")
                .bufferedReader()
                .use { it.readText() }
            val config = Gson().fromJson(json, FleetConfig::class.java)
            config.fleets.flatMapIndexed { fleetIdx, fleet ->
                fleet.boats.mapIndexed { boatIdx, boat ->
                    Craft(
                        id          = "${fleetIdx * 100 + boatIdx + 1}",
                        code        = boat.code,
                        displayName = boat.name,
                        craftClass  = fleet.craftClass,
                        isAvailable = true  // overridden by API response at runtime
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
