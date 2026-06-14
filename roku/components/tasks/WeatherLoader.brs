' Detects location and fetches current weather.
' Mirrors core/data/repo/AmbientInfoRepository.kt weather logic exactly.

sub init()
    m.top.functionName = "loadWeather"
end sub

sub loadWeather()
    loc = detectLocation()
    result = fetchWeather(loc.latitude, loc.longitude)
    m.top.weather = result
end sub

' Returns an AA { latitude, longitude }. Falls back to a fixed location
' if the IP lookup fails or lacks coordinates.
function detectLocation() as object
    fallback = { latitude: 41.8240, longitude: -71.4128 }
    body = httpGet("https://ipapi.co/json/")
    if body = invalid or body = "" then return fallback
    j = ParseJson(body)
    if j = invalid then return fallback
    if j["latitude"] = invalid or j["longitude"] = invalid then return fallback
    return { latitude: j["latitude"], longitude: j["longitude"] }
end function

' Fetches and parses open-meteo weather. Returns the weather AA or {} on failure.
function fetchWeather(latitude as dynamic, longitude as dynamic) as object
    url = "https://api.open-meteo.com/v1/forecast"
    url = url + "?latitude=" + Str(latitude).Trim()
    url = url + "&longitude=" + Str(longitude).Trim()
    url = url + "&current=temperature_2m,weather_code"
    url = url + "&daily=temperature_2m_max,temperature_2m_min"
    url = url + "&temperature_unit=fahrenheit"
    url = url + "&timezone=auto"
    url = url + "&forecast_days=1"
    body = httpGet(url)
    if body = invalid or body = "" then return {}
    j = ParseJson(body)
    if j = invalid then return {}

    current = j["current"]
    daily = j["daily"]
    if current = invalid or daily = invalid then return {}

    maxArr = daily["temperature_2m_max"]
    minArr = daily["temperature_2m_min"]
    if maxArr = invalid or minArr = invalid then return {}
    if maxArr.Count() = 0 or minArr.Count() = 0 then return {}

    if current["temperature_2m"] = invalid or current["weather_code"] = invalid then return {}

    code = Int(current["weather_code"])
    return {
        temperatureF: roundNum(current["temperature_2m"]),
        summary: codeToSummary(code),
        highF: roundNum(maxArr[0]),
        lowF: roundNum(minArr[0])
    }
end function

' Rounds to nearest integer; handles negative values.
function roundNum(x as dynamic) as integer
    if x >= 0 then
        return Int(x + 0.5)
    else
        return -Int(-x + 0.5)
    end if
end function

' Maps an open-meteo weather code to a summary string.
' Copied exactly from the Kotlin toSummary mapping.
function codeToSummary(code as integer) as string
    if code = 0 then return "Clear"
    if code = 1 then return "Mostly Clear"
    if code = 2 then return "Partly Cloudy"
    if code = 3 then return "Cloudy"
    if code = 45 or code = 48 then return "Fog"
    if code = 51 or code = 53 or code = 55 then return "Drizzle"
    if code = 56 or code = 57 then return "Freezing Drizzle"
    if code = 61 or code = 63 or code = 65 then return "Rain"
    if code = 66 or code = 67 then return "Freezing Rain"
    if code = 71 or code = 73 or code = 75 then return "Snow"
    if code = 77 then return "Snow Grains"
    if code = 80 or code = 81 or code = 82 then return "Rain Showers"
    if code = 85 or code = 86 then return "Snow Showers"
    if code = 95 or code = 96 or code = 99 then return "Thunderstorm"
    return "Weather"
end function
