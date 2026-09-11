package dev.weather.providers.openmeteo

import dev.weather.core.ProviderId

data class OpenMeteoModel(
    val provider: ProviderId,
    val apiModel: String,
    val displayName: String,
) {
    companion object {
        val ECMWF = OpenMeteoModel(ProviderId.ECMWF, "ecmwf_ifs", "ECMWF IFS HRES 9 km")
        val ICON = OpenMeteoModel(ProviderId.ICON, "icon_seamless", "DWD ICON Seamless")
        val GFS = OpenMeteoModel(ProviderId.GFS, "ncep_gfs_seamless", "NOAA GFS Seamless")
        val MET_NORDIC = OpenMeteoModel(
            ProviderId.MET_NORWAY,
            "metno_nordic",
            "MET Nordic 1 km (short range)",
        )
        val initialModels = listOf(ECMWF, ICON, GFS, MET_NORDIC)
    }
}
