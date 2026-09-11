package dev.weather.core

enum class ProviderId(val displayName: String) {
    ECMWF("ECMWF IFS"),
    ICON("DWD ICON"),
    GFS("NOAA GFS"),
    SMHI("SMHI"),
    MET_NORWAY("MET Norway"),
}
