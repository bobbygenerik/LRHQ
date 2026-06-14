package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class RealAmbientInfoRepositoryTest {

    @Test
    fun `parses Open-Meteo current and daily weather`() {
        val json = """
            {
              "current": {
                "temperature_2m": 71.8,
                "weather_code": 2
              },
              "daily": {
                "temperature_2m_max": [78.4],
                "temperature_2m_min": [63.1]
              }
            }
        """.trimIndent()

        val weather = RealAmbientInfoRepository.parseOpenMeteoWeather(json)

        assertEquals(72, weather.temperatureF)
        assertEquals(78, weather.highF)
        assertEquals(63, weather.lowF)
        assertEquals("Partly Cloudy", weather.summary)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, weather.condition)
    }

    @Test
    fun `maps storm weather code`() {
        val json = """
            {
              "current": {
                "temperature_2m": 82.0,
                "weather_code": 95
              },
              "daily": {
                "temperature_2m_max": [86.0],
                "temperature_2m_min": [70.0]
              }
            }
        """.trimIndent()

        val weather = RealAmbientInfoRepository.parseOpenMeteoWeather(json)

        assertEquals("Thunderstorm", weather.summary)
        assertEquals(WeatherCondition.RAIN, weather.condition)
    }
}
