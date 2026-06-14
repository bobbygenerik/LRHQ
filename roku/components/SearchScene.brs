' Search overlay: an on-screen Keyboard on the left for entering a query and a
' LabelList on the right showing matching channel names. Filtering is
' case-insensitive substring matching against each channel's name. Selecting a
' result resolves the channel and exposes its url/name/id so the parent can play
' it. Backing out toggles the "closed" output field.

sub init()
    m.theme = Theme()

    m.bg = m.top.findNode("bg")
    m.bg.color = m.theme.Abyss

    m.title = m.top.findNode("title")
    m.title.color = m.theme.TextPrimary

    m.keyboard = m.top.findNode("keyboard")
    m.results = m.top.findNode("results")

    ' Style the results list text with theme colors.
    m.results.color = m.theme.TextSecondary
    m.results.focusedColor = m.theme.Accent

    ' Parallel array mapping a result row index to its channel AA.
    m.resultChannels = []

    m.keyboard.observeField("text", "onQueryChanged")
    m.results.observeField("itemSelected", "onResultSelected")

    ' Initial population (empty query shows the first 50 channels).
    rebuildResults()

    m.keyboard.setFocus(true)
end sub

' Re-filter the channels against the current keyboard text and repopulate the
' results LabelList plus the parallel resultChannels array.
sub rebuildResults()
    channels = m.top.channels
    if channels = invalid then channels = []

    query = ""
    if m.keyboard <> invalid then query = m.keyboard.text
    if query = invalid then query = ""
    needle = LCase(query)

    content = CreateObject("roSGNode", "ContentNode")
    matches = []

    count = 0
    for i = 0 to channels.Count() - 1
        ch = channels[i]
        if ch <> invalid then
            chName = ch.name
            if chName = invalid then chName = ""

            include = false
            if needle = "" then
                include = true
            else
                haystack = LCase(chName)
                if Instr(1, haystack, needle) > 0 then include = true
            end if

            if include then
                item = content.CreateChild("ContentNode")
                item.title = chName
                matches.Push(ch)
                count = count + 1
                if count >= 50 then exit for
            end if
        end if
    end for

    m.resultChannels = matches
    m.results.content = content
end sub

sub onQueryChanged()
    rebuildResults()
end sub

sub onChannelsSet()
    rebuildResults()
end sub

sub onResultSelected()
    idx = m.results.itemSelected
    if idx = invalid then return
    if idx < 0 or idx >= m.resultChannels.Count() then return

    ch = m.resultChannels[idx]
    if ch = invalid then return

    url = ch.url
    if url = invalid then url = ""
    nm = ch.name
    if nm = invalid then nm = ""
    cid = ch.id
    if cid = invalid then cid = ""

    m.top.chosenName = nm
    m.top.chosenId = cid
    ' Set the url last so a single observer on chosenUrl fires after the other
    ' fields are already populated.
    m.top.chosenUrl = url
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false

    if key = "back" then
        m.top.closed = not m.top.closed
        return true
    end if

    if key = "right" then
        if m.keyboard.hasFocus() then
            m.results.setFocus(true)
            return true
        end if
    else if key = "left" then
        if m.results.hasFocus() then
            m.keyboard.setFocus(true)
            return true
        end if
    end if

    return false
end function
