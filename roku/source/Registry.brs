' Persistent settings, mirroring the Android DataStore prefs (playlist + EPG URLs).
' Roku has no DataStore; roRegistrySection is the equivalent key/value store.

function regGet(key as string, default = "" as string) as string
    sec = CreateObject("roRegistrySection", "lrhq")
    if sec.Exists(key) then return sec.Read(key)
    return default
end function

sub regSet(key as string, value as string)
    sec = CreateObject("roRegistrySection", "lrhq")
    sec.Write(key, value)
    sec.Flush()
end sub

function getPlaylistUrl() as string
    return regGet("playlist_url", "")
end function

function getEpgUrl() as string
    return regGet("epg_url", "")
end function

function getUnsplashAccessKey() as string
    return regGet("unsplash_access_key", "")
end function

function regGetBool(key as string, defaultValue as boolean) as boolean
    raw = LCase(regGet(key, ""))
    if raw = "true" then return true
    if raw = "false" then return false
    return defaultValue
end function

function regGetInt(key as string, defaultValue as integer) as integer
    raw = regGet(key, "")
    if raw = "" then return defaultValue
    value = Val(raw)
    if value <= 0 then return defaultValue
    return value
end function

function getShowLivePreview() as boolean
    return regGetBool("show_live_preview", true)
end function

function getShowWeather() as boolean
    return regGetBool("show_weather", true)
end function

function getIdleSeconds() as integer
    return regGetInt("idle_seconds", 300)
end function

' --- Recents + favorites: stored as newline-delimited channel-id lists, ---
' --- mirroring the Android repo's recents/favorites behavior.            ---

function getIdList(key as string) as object
    raw = regGet(key, "")
    if raw = "" then return []
    out = []
    for each part in raw.Split(chr(10))
        if part <> "" then out.Push(part)
    end for
    return out
end function

sub setIdList(key as string, ids as object)
    s = ""
    for each id in ids
        if s <> "" then s = s + chr(10)
        s = s + id
    end for
    regSet(key, s)
end sub

function getRecents() as object
    return getIdList("recents")
end function

' Most-recent-first, deduped, capped.
sub addRecent(channelId as string)
    if channelId = "" then return
    ids = getRecents()
    out = [channelId]
    for each id in ids
        if id <> channelId then out.Push(id)
    end for
    while out.Count() > 12
        out.Pop()
    end while
    setIdList("recents", out)
end sub

function getFavorites() as object
    return getIdList("favorites")
end function

function isFavorite(channelId as string) as boolean
    for each id in getFavorites()
        if id = channelId then return true
    end for
    return false
end function

sub toggleFavorite(channelId as string)
    if channelId = "" then return
    ids = getFavorites()
    out = []
    found = false
    for each id in ids
        if id = channelId then
            found = true
        else
            out.Push(id)
        end if
    end for
    if not found then out.Unshift(channelId)
    setIdList("favorites", out)
end sub
