package dev.weather.core

data class NotificationPreferences(
    val enabled: Boolean = false,
    val intervalHours: Int = 3,
    val startHour: Int = 7,
    val endHour: Int = 22,
    val auroraAlertsEnabled: Boolean = false,
    val auroraProbabilityThreshold: Int = 10,
) {
    init {
        require(intervalHours in 1..24)
        require(startHour in 0..23)
        require(endHour in 0..23)
        require(auroraProbabilityThreshold in 1..100)
    }

    /** End is exclusive. Equal start/end means notifications are allowed all day. */
    fun allowsHour(hour: Int): Boolean {
        require(hour in 0..23)
        return when {
            startHour == endHour -> true
            startHour < endHour -> hour in startHour until endHour
            else -> hour >= startHour || hour < endHour
        }
    }
}
