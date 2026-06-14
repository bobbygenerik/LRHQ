' Recent channel pill — mirrors RecentChannelsRow.kt / RecentChannelChip composable:
' rounded glass chip, 32px logo block, full channel name, accent border on focus.

sub init()
    m.theme = Theme()
    m.border = m.top.findNode("border")
    m.pill = m.top.findNode("pill")
    m.logoBox = m.top.findNode("logoBox")
    m.logo = m.top.findNode("logo")
    m.initial = m.top.findNode("initial")
    m.name = m.top.findNode("name")

    m.initial.color = m.theme.TextSecondary
    m.name.color = m.theme.TextSecondary
    m.top.observeField("width", "layoutChip")
    setUnfocusedArt()
    layoutChip()
end sub

sub onContentSet()
    c = m.top.itemContent
    if c = invalid then return

    m.name.text = c.title
    initial = "?"
    if c.title <> invalid and c.title <> "" then initial = UCase(Left(c.title, 1))
    m.initial.text = initial

    if c.logo <> invalid and c.logo <> "" then
        m.logo.uri = c.logo
        m.logo.visible = true
        m.initial.visible = false
    else
        m.logo.visible = false
        m.initial.visible = true
    end if
    layoutChip()
end sub

sub layoutChip()
    w = m.top.width
    if w < 100 then w = 240
    h = 44
    m.top.height = h
    m.border.width = w
    m.border.height = h
    m.pill.width = w - 2
    m.pill.height = h - 2
    nameW = w - 56
    if nameW < 40 then nameW = 40
    m.name.width = nameW
end sub

sub setUnfocusedArt()
    m.border.uri = "pkg:/images/chip_border.png"
    m.pill.uri = "pkg:/images/chip_bg.png"
    m.logoBox.uri = "pkg:/images/chip_logo_bg.png"
end sub

sub setFocusedArt()
    m.border.uri = "pkg:/images/chip_border_focus.png"
    m.pill.uri = "pkg:/images/chip_bg_focus.png"
    m.logoBox.uri = "pkg:/images/chip_logo_bg_focus.png"
end sub

sub onFocusChanged()
    if m.top.focusPercent > 0.5 then
        setFocusedArt()
        m.initial.color = m.theme.Accent
        m.name.color = m.theme.TextPrimary
    else
        setUnfocusedArt()
        m.initial.color = m.theme.TextSecondary
        m.name.color = m.theme.TextSecondary
    end if
end sub
