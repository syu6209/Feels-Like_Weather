package com.zozi.feels_likeweather.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 한 지점의 날씨 예보 전체. 모든 시각은 해당 지역의 현지 시각(LocalDateTime)이다. */
data class WeatherForecast(
    val latitude: Double,
    val longitude: Double,
    val utcOffsetSeconds: Int,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    val fetchedAtMillis: Long,
) {
    val zoneOffset: ZoneOffset get() = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)

    /** 지역 기준 현재 시각. */
    fun localNow(): LocalDateTime = LocalDateTime.now(zoneOffset)

    fun hourlyAt(time: LocalDateTime): HourlyWeather? {
        val hour = time.withMinute(0).withSecond(0).withNano(0)
        return hourly.firstOrNull { it.time == hour }
    }

    fun today(): DailyWeather? = daily.firstOrNull { it.date == localNow().toLocalDate() }

    /**
     * 저장해 둔 예보를 나중에 볼 때, 받을 당시의 "현재 날씨" 대신 지금 시각의 시간별 예보로 현재 날씨를 바꾼다.
     * 같은 시간대이거나 지금 시각의 예보가 없으면 그대로 둔다.
     */
    fun currentAt(now: LocalDateTime): WeatherForecast {
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        if (!current.time.isBefore(hour)) return this
        val h = hourlyAt(hour) ?: return this
        return copy(
            current = CurrentWeather(
                time = h.time,
                temperature = h.temperature,
                apparentTemperature = h.apparentTemperature,
                humidity = h.humidity,
                weatherCode = h.weatherCode,
                windSpeed = h.windSpeed,
                precipitation = h.precipitation,
                isDay = h.isDay,
            )
        )
    }
}

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val windSpeed: Double,
    val precipitation: Double,
    val isDay: Boolean,
)

data class HourlyWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val precipitationProbability: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val uvIndex: Double,
    val isDay: Boolean,
)

data class DailyWeather(
    val date: LocalDate,
    val weatherCode: Int,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val apparentMax: Double,
    val apparentMin: Double,
    val precipitationProbabilityMax: Int,
    val precipitationSum: Double,
    val uvIndexMax: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
)

/** 지역 검색 결과 / 저장된 지역. */
data class Place(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
) {
    val id: String get() = "%.3f,%.3f".format(latitude, longitude)
}

/** WMO weather interpretation code 분류. https://open-meteo.com/en/docs */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

fun weatherKindOf(code: Int): WeatherKind = when (code) {
    0, 1 -> WeatherKind.CLEAR
    2 -> WeatherKind.PARTLY_CLOUDY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
    95, 96, 99 -> WeatherKind.THUNDER
    else -> WeatherKind.CLOUDY
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "맑음"
    1 -> "대체로 맑음"
    2 -> "구름 조금"
    3 -> "흐림"
    45, 48 -> "안개"
    51, 53, 55 -> "이슬비"
    56, 57 -> "어는 이슬비"
    61 -> "약한 비"
    63 -> "비"
    65 -> "강한 비"
    66, 67 -> "어는 비"
    71 -> "약한 눈"
    73 -> "눈"
    75 -> "강한 눈"
    77 -> "싸락눈"
    80 -> "약한 소나기"
    81 -> "소나기"
    82 -> "강한 소나기"
    85 -> "약한 눈보라"
    86 -> "강한 눈보라"
    95 -> "뇌우"
    96, 99 -> "우박을 동반한 뇌우"
    else -> "알 수 없음"
}

/** 우산 등을 챙길 정도로 젖는 날씨인지. */
fun isWet(code: Int): Boolean = weatherKindOf(code).let {
    it == WeatherKind.DRIZZLE || it == WeatherKind.RAIN || it == WeatherKind.THUNDER
}

fun isHeavyRain(code: Int): Boolean = code in setOf(65, 67, 81, 82, 95, 96, 99)

fun isSnow(code: Int): Boolean = weatherKindOf(code) == WeatherKind.SNOW
