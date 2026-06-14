' Downloads and parses an M3U playlist into an array of channel AAs.
' Mirrors core/data/iptv/M3uParser.kt.

sub init()
    m.top.functionName = "loadPlaylist"
end sub

sub loadPlaylist()
    body = httpGet(m.top.url)
    chans = []
    if body <> invalid and body <> "" then chans = parseM3u(body)
    m.top.channels = chans
end sub

function parseM3u(body as string) as object
    chans = []
    lines = body.Split(chr(10))
    pending = invalid
    maxChannels = 400
    for each raw in lines
        if chans.Count() >= maxChannels then exit for
        line = raw.Trim()
        if line <> "" then
            if Left(line, 8) = "#EXTINF:" then
                pending = parseExtinf(line)
            else if Left(line, 1) <> "#" then
                if pending <> invalid then
                    pending.url = line
                    id = pending.tvgId
                    if id = "" then id = pending.name
                    pending.id = id
                    chans.Push(pending)
                    pending = invalid
                end if
            end if
        end if
    end for
    return chans
end function

function parseExtinf(line as string) as object
    ch = { name: "", url: "", tvgId: "", logo: "", group: "" }
    commaIdx = Instr(1, line, ",")
    if commaIdx > 0 then ch.name = Mid(line, commaIdx + 1).Trim()
    ch.tvgId = extractAttr(line, "tvg-id")
    ch.logo = extractAttr(line, "tvg-logo")
    ch.group = extractAttr(line, "group-title")
    return ch
end function

function extractAttr(line as string, attr as string) as string
    token = attr + "=" + chr(34)
    start = Instr(1, line, token)
    if start = 0 then return ""
    start = start + Len(token)
    endq = Instr(start, line, chr(34))
    if endq = 0 then return ""
    return Mid(line, start, endq - start)
end function
