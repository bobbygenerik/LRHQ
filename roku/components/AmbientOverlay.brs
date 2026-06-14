' In-app ambient / idle full-screen overlay. Roku channels cannot register a real
' system screensaver, so this is an in-channel idle overlay instead: a slow
' crossfading slideshow of full-screen images with a clock.
'
' Two Poster nodes alternate as the "front" (visible) and "back" (incoming) image.
' A repeating Timer drives the crossfade; an Animation with two
' FloatFieldInterpolators fades the incoming poster up and the outgoing poster
' down on the same timeline.

sub init()
    m.theme = Theme()

    m.bg = m.top.findNode("bg")
    m.posterA = m.top.findNode("posterA")
    m.posterB = m.top.findNode("posterB")
    m.scrim = m.top.findNode("scrim")
    m.clock = m.top.findNode("clock")
    m.date = m.top.findNode("date")
    m.weather = m.top.findNode("ambientWeather")
    m.weatherDetail = m.top.findNode("ambientWeatherDetail")
    m.credit = m.top.findNode("credit")
    m.cycleTimer = m.top.findNode("cycleTimer")
    m.fadeAnim = m.top.findNode("fadeAnim")
    m.fadeInInterp = m.top.findNode("fadeInInterp")
    m.fadeOutInterp = m.top.findNode("fadeOutInterp")

    m.bg.color = m.theme.Abyss
    m.scrim.color = "0x000000FF"
    m.clock.color = m.theme.TextPrimary
    m.date.color = m.theme.TextSecondary
    m.weather.color = m.theme.TextPrimary
    m.weatherDetail.color = m.theme.TextSecondary
    m.credit.color = m.theme.TextTertiary

    ' Index of the image currently shown in the front poster.
    m.currentIndex = -1
    ' Which poster is the front (visible) one: true = posterA, false = posterB.
    m.frontIsA = true

    m.cycleTimer.observeField("fire", "onCycleTick")
    m.fadeAnim.observeField("state", "onFadeState")

    m.top.visible = false
end sub

sub onClockChanged()
    m.clock.text = m.top.clockText
end sub

sub onDateChanged()
    m.date.text = m.top.dateText
end sub

sub onWeatherChanged()
    m.weather.text = m.top.weatherText
end sub

sub onWeatherDetailChanged()
    m.weatherDetail.text = m.top.weatherDetail
end sub

sub onActiveChanged()
    if m.top.active then
        startAmbient()
    else
        stopAmbient()
    end if
end sub

sub startAmbient()
    m.top.visible = true

    imgs = m.top.images
    if imgs <> invalid and imgs.Count() > 0 then
        ' Begin at a random image, shown immediately on the front poster.
        m.currentIndex = Rnd(imgs.Count()) - 1
        m.frontIsA = true
        m.posterA.uri = imgs[m.currentIndex]
        m.posterA.opacity = 1.0
        m.posterB.uri = ""
        m.posterB.opacity = 0.0
        m.cycleTimer.control = "start"
    else
        ' No images: just the Abyss background plus clock.
        m.posterA.opacity = 0.0
        m.posterB.opacity = 0.0
        m.cycleTimer.control = "stop"
    end if
end sub

sub stopAmbient()
    m.top.visible = false
    m.cycleTimer.control = "stop"
    m.fadeAnim.control = "stop"
end sub

sub onCycleTick()
    imgs = m.top.images
    if imgs = invalid or imgs.Count() <= 1 then return

    ' Pick the next image (wrap around).
    nextIndex = m.currentIndex + 1
    if nextIndex >= imgs.Count() then nextIndex = 0
    m.currentIndex = nextIndex
    nextUri = imgs[nextIndex]

    ' Load the next image into whichever poster is currently the back one,
    ' then crossfade: back fades in, front fades out.
    if m.frontIsA then
        m.posterB.uri = nextUri
        m.fadeInInterp.fieldToInterp = "posterB.opacity"
        m.fadeOutInterp.fieldToInterp = "posterA.opacity"
    else
        m.posterA.uri = nextUri
        m.fadeInInterp.fieldToInterp = "posterA.opacity"
        m.fadeOutInterp.fieldToInterp = "posterB.opacity"
    end if

    m.fadeAnim.control = "start"
end sub

sub onFadeState()
    ' When the crossfade finishes, the back poster has become the new front.
    if m.fadeAnim.state = "stopped" then
        m.frontIsA = not m.frontIsA
    end if
end sub
