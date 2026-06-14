' Compact category filter pill for the Roku home CATEGORIES rail.

sub init()
    m.theme = Theme()
    m.border = m.top.findNode("border")
    m.pill = m.top.findNode("pill")
    m.name = m.top.findNode("name")

    m.border.color = m.theme.Accent
    m.pill.color = m.theme.GlassFill
    m.name.color = m.theme.TextSecondary
end sub

sub onContentSet()
    c = m.top.itemContent
    if c = invalid then return
    m.name.text = c.title
end sub

sub onFocusChanged()
    if m.top.focusPercent > 0.5 then
        m.border.opacity = 1.0
        m.pill.color = m.theme.AccentDim
        m.name.color = m.theme.TextPrimary
    else
        m.border.opacity = 0.0
        m.pill.color = m.theme.GlassFill
        m.name.color = m.theme.TextSecondary
    end if
end sub
