' Settings screen: edit playlist/guide URLs and Roku-supported appearance controls,
' persisted via Registry helpers in the "lrhq" section.
' Mirrors the Android settings flow, adapted to SceneGraph with an on-screen
' StandardKeyboardDialog for text entry.

sub init()
    m.theme = Theme()

    m.bg = m.top.findNode("bg")
    m.bg.color = m.theme.Abyss
    m.title = m.top.findNode("title")
    m.title.color = m.theme.TextPrimary

    m.rows = [
        { key: "playlist_url",      label: "Playlist URL (M3U)",  kind: "text",   hint: "Enter an M3U URL" }
        { key: "epg_url",           label: "Guide URL (XMLTV)",   kind: "text",   hint: "Enter an XMLTV URL" }
        { key: "show_live_preview", label: "Live Preview Hero",   kind: "toggle", defaultValue: "true" }
        { key: "show_weather",      label: "Show Weather",        kind: "toggle", defaultValue: "true" }
        { key: "idle_seconds",      label: "Idle Overlay Delay",  kind: "number", defaultValue: "300", hint: "Seconds before ambient overlay" }
        { key: "unsplash_access_key", label: "Unsplash Access Key", kind: "secret", hint: "Paste Unsplash access key" }
    ]

    m.rowBorders = [ m.top.findNode("row0Border"), m.top.findNode("row1Border"), m.top.findNode("row2Border"), m.top.findNode("row3Border"), m.top.findNode("row4Border"), m.top.findNode("row5Border") ]
    m.rowCards   = [ m.top.findNode("row0Card"),   m.top.findNode("row1Card"),   m.top.findNode("row2Card"),   m.top.findNode("row3Card"),   m.top.findNode("row4Card"),   m.top.findNode("row5Card") ]
    m.rowLabels  = [ m.top.findNode("row0Label"),  m.top.findNode("row1Label"),  m.top.findNode("row2Label"),  m.top.findNode("row3Label"),  m.top.findNode("row4Label"),  m.top.findNode("row5Label") ]
    m.rowValues  = [ m.top.findNode("row0Value"),  m.top.findNode("row1Value"),  m.top.findNode("row2Value"),  m.top.findNode("row3Value"),  m.top.findNode("row4Value"),  m.top.findNode("row5Value") ]

    for i = 0 to m.rows.Count() - 1
        m.rowCards[i].color = m.theme.GlassFill
        m.rowBorders[i].color = m.theme.Accent
        m.rowLabels[i].color = m.theme.TextPrimary
        m.rowLabels[i].text = m.rows[i].label
    end for

    m.focusIndex = 0
    m.dialog = invalid

    refreshValues()
    highlightRow()
end sub

sub restoreInputFocus()
    scene = m.top.getScene()
    if scene = invalid then return
    kh = scene.findNode("keyHandler")
    if kh = invalid then return
    trap = kh.findNode("trap")
    if trap <> invalid then trap.setFocus(true)
end sub

' Re-read each saved value from the registry and update the value labels.
sub refreshValues()
    for i = 0 to m.rows.Count() - 1
        info = m.rows[i]
        defaultValue = ""
        if info.defaultValue <> invalid then defaultValue = info.defaultValue
        value = regGet(info.key, defaultValue)
        if value = "" then
            m.rowValues[i].text = "(not set)"
            m.rowValues[i].color = m.theme.TextTertiary
        else if info.kind = "toggle" then
            if LCase(value) = "true" then
                m.rowValues[i].text = "On"
            else
                m.rowValues[i].text = "Off"
            end if
            m.rowValues[i].color = m.theme.TextSecondary
        else if info.kind = "number" then
            m.rowValues[i].text = value + " seconds"
            m.rowValues[i].color = m.theme.TextSecondary
        else if info.kind = "secret" then
            m.rowValues[i].text = "Set"
            m.rowValues[i].color = m.theme.TextSecondary
        else
            display = value
            if info.kind = "text" and Len(display) > 72 then
                display = Left(display, 72) + "…"
            end if
            m.rowValues[i].text = display
            m.rowValues[i].color = m.theme.TextSecondary
        end if
    end for
end sub

' Show the accent border on the focused row, hide it on the others.
sub highlightRow()
    for i = 0 to m.rows.Count() - 1
        if i = m.focusIndex then
            m.rowBorders[i].opacity = 1.0
        else
            m.rowBorders[i].opacity = 0.0
        end if
    end for
end sub

' Open the on-screen keyboard pre-filled with the current value of the focused row.
sub openEditor()
    idx = m.focusIndex
    info = m.rows[idx]

    if info.kind = "toggle" then
        toggleRow(idx)
        return
    end if

    dialog = CreateObject("roSGNode", "StandardKeyboardDialog")
    dialog.title = "Edit " + info.label
    dialog.buttons = [ "Save", "Cancel" ]

    current = regGet(info.key, "")
    if current = "" and info.defaultValue <> invalid then current = info.defaultValue
    setDialogText(dialog, current)

    dialog.observeField("buttonSelected", "onDialogButton")
    dialog.observeField("wasClosed", "onDialogClosed")

    m.dialog = dialog
    scene = dialogScene()
    if scene <> invalid then
        scene.dialog = dialog
    end if
end sub

sub onDialogButton()
    if m.dialog = invalid then return
    selected = m.dialog.buttonSelected
    dismissDialog(selected = 0)
end sub

sub toggleRow(idx as integer)
    info = m.rows[idx]
    current = LCase(regGet(info.key, info.defaultValue))
    if current = "true" then
        regSet(info.key, "false")
    else
        regSet(info.key, "true")
    end if
    refreshValues()
    m.top.settingsChanged = not m.top.settingsChanged
end sub

sub onDialogClosed()
    clearDialog()
    restoreInputFocus()
end sub

sub dismissDialog(save as boolean)
    if m.dialog = invalid then return

    if save then
        newText = getDialogText(m.dialog)
        info = m.rows[m.focusIndex]
        if info.kind = "number" then
            parsed = Val(newText)
            if parsed <= 0 then parsed = Val(info.defaultValue)
            newText = parsed.ToStr()
        end if
        regSet(info.key, newText)
        refreshValues()
        m.top.settingsChanged = not m.top.settingsChanged
    end if

    dialog = m.dialog
    clearDialog()
    restoreInputFocus()
end sub

sub clearDialog()
    if m.dialog <> invalid then
        m.dialog.close = true
    end if
    m.dialog = invalid
    scene = dialogScene()
    if scene <> invalid then scene.dialog = invalid
end sub

function dialogScene() as object
    scene = m.top.getScene()
    if scene <> invalid then return scene
    parent = m.top.getParent()
    if parent <> invalid then return parent
    return invalid
end function

sub setDialogText(dialog as object, value as string)
    if dialog = invalid then return
    if dialog.hasField("text") then
        dialog.text = value
    else if dialog.keyboard <> invalid then
        dialog.keyboard.text = value
    end if
end sub

function getDialogText(dialog as object) as string
    if dialog = invalid then return ""
    if dialog.hasField("text") then
        return dialog.text
    else if dialog.keyboard <> invalid then
        return dialog.keyboard.text
    end if
    return ""
end function

function dialogOpen() as boolean
    return m.dialog <> invalid
end function

function proxyKey(key as string) as boolean
    if m.dialog <> invalid then return false

    if key = "up" then
        if m.focusIndex > 0 then
            m.focusIndex = m.focusIndex - 1
            highlightRow()
        end if
        return true
    else if key = "down" then
        if m.focusIndex < m.rows.Count() - 1 then
            m.focusIndex = m.focusIndex + 1
            highlightRow()
        end if
        return true
    else if key = "OK" then
        openEditor()
        return true
    else if key = "back" then
        return false
    end if

    return true
end function

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    return proxyKey(key)
end function
