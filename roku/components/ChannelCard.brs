' Text-forward channel card for the MarkupGrid: channel name, current programme,
' a live progress bar, and time remaining. No artwork — nothing can fail to load.
' Mirrors OnNowRail.kt's card.

sub init()
    m.theme = Theme()
    m.card = m.top.findNode("card")
    m.border = m.top.findNode("border")
    m.name = m.top.findNode("name")
    m.favorite = m.top.findNode("favorite")
    m.title = m.top.findNode("title")
    m.track = m.top.findNode("track")
    m.fill = m.top.findNode("fill")
    m.timeLeft = m.top.findNode("timeLeft")

    m.card.color = m.theme.GlassFill
    m.border.color = m.theme.Accent
    m.fill.color = m.theme.Accent
    m.name.color = m.theme.TextTertiary
    m.favorite.color = m.theme.Accent
    m.title.color = m.theme.TextSecondary
    m.timeLeft.color = m.theme.TextTertiary
end sub

sub onContentSet()
    c = m.top.itemContent
    if c = invalid then return
    render(c)
    ' EPG arrives after the grid is populated, so react to later field updates.
    c.observeField("nowTitle", "onEpgUpdated")
    c.observeField("progress", "onEpgUpdated")
end sub

sub onEpgUpdated()
    c = m.top.itemContent
    if c <> invalid then render(c)
end sub

sub render(c as object)
    m.name.text = c.title
    m.favorite.visible = c.favorite
    if c.nowTitle <> "" then
        m.title.text = c.nowTitle
    else
        m.title.text = "No programme info"
    end if
    p = c.progress
    if p < 0 then p = 0
    if p > 1 then p = 1
    m.fill.width = 164 * p
    m.timeLeft.text = c.timeLeft
end sub

sub onFocusChanged()
    if m.top.focusPercent > 0.5 then
        m.border.opacity = 1.0
        m.card.color = m.theme.AccentDim
        m.name.color = m.theme.Accent
        m.title.color = m.theme.TextPrimary
    else
        m.border.opacity = 0.0
        m.card.color = m.theme.GlassFill
        m.name.color = m.theme.TextTertiary
        m.title.color = m.theme.TextSecondary
    end if
end sub
