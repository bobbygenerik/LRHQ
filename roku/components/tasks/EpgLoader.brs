' Downloads and parses an XMLTV guide into a map of channelId -> now-playing info.
' Only the currently-airing programme per channel is kept (memory-bounded), which
' is all the home grid needs. Mirrors core/data/iptv/XmltvParser.kt (now/next).

sub init()
    m.top.functionName = "loadEpg"
end sub

sub loadEpg()
    body = httpGet(m.top.url)
    result = {}
    if body <> invalid and body <> "" then result = parseXmltv(body)
    m.top.epg = result
end sub

function parseXmltv(body as string) as object
    result = {}
    xml = CreateObject("roXMLElement")
    if not xml.Parse(body) then return result

    nowSec = CreateObject("roDateTime").AsSeconds()
    nextMap = {}
    programmes = xml.GetNamedElements("programme")
    for each p in programmes
        attrs = p.GetAttributes()
        if attrs <> invalid then
            chId = attrs.channel
            startSec = parseXmltvTime(attrs.start)
            stopSec = parseXmltvTime(attrs.stop)
            if chId <> invalid and startSec > 0 and stopSec > startSec then
                title = ""
                for each t in p.GetNamedElements("title")
                    title = t.GetText()
                    exit for
                end for
                if startSec <= nowSec and nowSec < stopSec then
                    progress = (nowSec - startSec) / (stopSec - startSec)
                    minsLeft = Int((stopSec - nowSec) / 60)
                    result[chId] = {
                        nowTitle: title
                        progress: progress
                        timeLeft: timeLeftLabel(minsLeft)
                        nextTitle: ""
                    }
                else if startSec >= nowSec then
                    existing = nextMap[chId]
                    if existing = invalid or startSec < existing.startSec then
                        nextMap[chId] = { title: title, startSec: startSec }
                    end if
                end if
            end if
        end if
    end for

    ' Attach the next programme title to each channel's now-playing entry.
    for each chId in result
        nextInfo = nextMap[chId]
        if nextInfo <> invalid then result[chId].nextTitle = nextInfo.title
    end for

    ' Index each entry under a normalized id too, so channels whose tvg-id differs
    ' only by case/spacing/punctuation still match the guide.
    aliased = {}
    for each chId in result
        norm = normalizeGuideId(chId)
        if norm <> "" and norm <> chId and result[norm] = invalid then aliased[norm] = result[chId]
    end for
    for each norm in aliased
        result[norm] = aliased[norm]
    end for

    return result
end function

' Lowercase and strip spaces/dots so "BBC One.uk" and "bbcone uk" match.
function normalizeGuideId(s as dynamic) as string
    if s = invalid then return ""
    lower = LCase(s)
    out = ""
    for i = 1 to Len(lower)
        c = Mid(lower, i, 1)
        if c <> " " and c <> "." then out = out + c
    end for
    return out
end function

' XMLTV timestamps look like "20260614090000 +0100". Convert to epoch seconds.
function parseXmltvTime(s as dynamic) as integer
    if s = invalid then return 0
    str = s.Trim()
    if Len(str) < 14 then return 0

    iso = Mid(str,1,4) + "-" + Mid(str,5,2) + "-" + Mid(str,7,2) + "T" + Mid(str,9,2) + ":" + Mid(str,11,2) + ":" + Mid(str,13,2) + "Z"
    dt = CreateObject("roDateTime")
    dt.FromISO8601String(iso)
    secs = dt.AsSeconds()

    ' Apply the timezone offset so "now" comparisons are in true UTC.
    sign = 0
    opos = 0
    plusIdx = Instr(15, str, "+")
    minusIdx = Instr(15, str, "-")
    if plusIdx > 0 then
        sign = -1 : opos = plusIdx
    else if minusIdx > 0 then
        sign = 1 : opos = minusIdx
    end if
    if opos > 0 and Len(str) >= opos + 4 then
        offH = Val(Mid(str, opos + 1, 2), 10)
        offM = Val(Mid(str, opos + 3, 2), 10)
        secs = secs + sign * (offH * 3600 + offM * 60)
    end if
    return secs
end function

function timeLeftLabel(mins as integer) as string
    if mins <= 0 then return "Ending"
    if mins = 1 then return "1 min left"
    if mins < 60 then return Str(mins).Trim() + " min left"
    h = Int(mins / 60)
    remMins = mins mod 60
    if remMins = 0 then return Str(h).Trim() + "h left"
    return Str(h).Trim() + "h " + Str(remMins).Trim() + "m left"
end function
