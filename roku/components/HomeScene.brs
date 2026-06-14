' Home screen: hero live preview, Recents / On Now rails, and a clock.
' Unlike the Android launcher, Roku is a normal channel — preview stays on at home.

sub init()
    m.theme = Theme()

    m.buildTag = m.top.findNode("buildTag")
    m.keyHandler = m.top.findNode("keyHandler")
    m.keyTrap = invalid
    if m.keyHandler <> invalid then m.keyTrap = m.keyHandler.findNode("trap")
    m.overlays = m.top.findNode("overlays")
    m.tasks = m.top.findNode("tasks")

    m.bg = m.top.findNode("bg")
    m.bg.color = m.theme.Abyss
    m.status = m.top.findNode("status")
    m.status.color = m.theme.TextSecondary

    m.content = m.top.findNode("content")

    m.heroBackdrop = m.top.findNode("heroBackdrop")
    m.heroBackdrop.uri = "pkg:/images/ambient_01.jpg"
    m.heroPreviewClip = m.top.findNode("heroPreviewClip")
    m.heroPreview = m.top.findNode("heroPreview")
    m.heroPreview.observeField("state", "onHeroPreviewState")
    m.heroPreviewUrl = ""
    m.heroPreviewWasPlaying = false
    m.pendingHeroPreview = invalid
    m.heroCurrentItem = invalid
    m.heroFocused = false
    m.lastDispatchKey = ""
    m.lastDispatchTick = 0
    m.heroPreviewRetry = 0
    m.heroOverlaysVisible = true
    m.heroOverlays = m.top.findNode("heroOverlays")
    m.heroFocusBar = m.top.findNode("heroFocusBar")
    if m.heroFocusBar <> invalid then m.heroFocusBar.color = m.theme.Accent
    m.liveBadge = m.top.findNode("liveBadge")
    m.clock = m.top.findNode("clock")
    m.clockShadow = m.top.findNode("clockShadow")
    m.clockDate = m.top.findNode("clockDate")
    m.clockDateShadow = m.top.findNode("clockDateShadow")
    m.weatherTemp = m.top.findNode("weatherTemp")
    m.weatherTempShadow = m.top.findNode("weatherTempShadow")
    m.heroNowLabel = m.top.findNode("heroNowLabel")
    m.heroNowShadow = m.top.findNode("heroNowShadow")
    m.heroNowLabel.color = m.theme.Accent
    m.heroChannel = m.top.findNode("heroChannel")
    m.heroChannelShadow = m.top.findNode("heroChannelShadow")
    m.heroChannel.color = "0xFFFFFFFF"
    m.heroProgramme = m.top.findNode("heroProgramme")
    m.heroProgrammeShadow = m.top.findNode("heroProgrammeShadow")
    m.heroProgramme.color = "0xFFFFFFD9"
    m.heroProgressTrack = m.top.findNode("heroProgressTrack")
    m.heroProgressFill = m.top.findNode("heroProgressFill")
    m.heroProgressFill.color = m.theme.Accent
    m.upNextPanel = m.top.findNode("upNextPanel")
    m.upNextBg = m.top.findNode("upNextBg")
    m.upNextBorder = m.top.findNode("upNextBorder")
    m.upNextLabel = m.top.findNode("upNextLabel")
    m.upNextLabel.color = m.theme.TextTertiary
    m.heroNext = m.top.findNode("heroNext")
    m.heroNext.color = m.theme.TextPrimary

    m.recentHeader = m.top.findNode("recentHeader")
    m.recentHeader.color = m.theme.TextTertiary
    m.onNowHeader = m.top.findNode("onNowHeader")
    m.onNowHeader.color = m.theme.TextTertiary
    m.groupsHeader = m.top.findNode("groupsHeader")
    m.groupsHeader.color = m.theme.TextTertiary
    m.liveBrowseTitle = m.top.findNode("liveBrowseTitle")
    if m.liveBrowseTitle <> invalid then m.liveBrowseTitle.color = m.theme.TextPrimary
    m.heroGroup = m.top.findNode("hero")
    m.allChannelsHeader = m.top.findNode("allChannelsHeader")
    m.allChannelsHeader.color = m.theme.TextTertiary
    m.zoneOverlay = m.top.findNode("zoneOverlay")
    m.zoneBg = m.top.findNode("zoneBg")
    m.zoneTitle = m.top.findNode("zoneTitle")
    m.zoneBody = m.top.findNode("zoneBody")
    m.zoneTitle.color = m.theme.TextPrimary
    m.zoneBody.color = m.theme.TextSecondary

    m.sidebarBg = m.top.findNode("sidebarBg")
    m.sidebarBg.color = "0x04060AFF"
    m.sidebarTitle = m.top.findNode("sidebarTitle")
    m.sidebarTitle.color = m.theme.TextPrimary
    m.sidebarCollapsedWidth = 92
    m.sidebarExpandedWidth = 260
    m.navActive = [
        m.top.findNode("nav0Active")
        m.top.findNode("nav1Active")
        m.top.findNode("nav2Active")
        m.top.findNode("nav3Active")
    ]
    m.navFocus = [
        m.top.findNode("nav0Focus")
        m.top.findNode("nav1Focus")
        m.top.findNode("nav2Focus")
        m.top.findNode("nav3Focus")
    ]
    m.navIcons = [
        m.top.findNode("nav0Icon")
        m.top.findNode("nav1Icon")
        m.top.findNode("nav2Icon")
        m.top.findNode("nav3Icon")
    ]
    m.navLabelNodes = [
        m.top.findNode("nav0Label")
        m.top.findNode("nav1Label")
        m.top.findNode("nav2Label")
        m.top.findNode("nav3Label")
    ]
    m.navZones = [ "HOME", "LIVE", "COMMAND", "SETTINGS" ]
    m.navIndex = 0
    m.activeNavIndex = 0
    m.sidebarFocused = false
    m.zoneMode = "HOME"
    m.safeLeft = 24
    m.contentWidth = 1804
    m.heroProgressWidth = 760
    m.sidebarGroup = m.top.findNode("sidebar")
    m.clockWeather = m.top.findNode("clockWeather")

    m.recentGrid = m.top.findNode("recentGrid")
    m.recentGrid.observeField("itemSelected", "onRecentSelected")
    m.onNowGrid = m.top.findNode("onNowGrid")
    m.onNowGrid.observeField("itemSelected", "onOnNowSelected")
    m.groupsGrid = m.top.findNode("groupsGrid")
    m.groupsGrid.observeField("itemSelected", "onGroupSelected")
    m.allChannelsGrid = m.top.findNode("allChannelsGrid")
    m.allChannelsGrid.observeField("itemSelected", "onAllChannelsSelected")

    m.video = m.top.findNode("video")
    m.video.observeField("state", "onVideoState")

    m.ambient = m.top.findNode("ambient")
    m.ambient.images = ambientUnsplashImages()

    m.idleTimer = m.top.findNode("idleTimer")
    m.idleTimer.observeField("fire", "onIdle")
    m.clockTimer = m.top.findNode("clockTimer")
    m.clockTimer.observeField("fire", "onClockTick")
    m.clockTimer.control = "start"
    onClockTick()
    applyUserSettings()

    m.channels = []
    m.byId = {}
    m.epg = {}
    m.settings = invalid
    m.search = invalid
    m.selectedGroup = "All"
    m.railIdx = 0
    m.itemIdx = 0
    m.keyCount = 0
    m.loadingChannels = false
    m.showLiveBrowse = false
    m.pendingLiveBrowseFocus = false
    m.rebuildPhase = 0
    m.indexCursor = 0
    m.groupList = []
    m.groupSeen = {}

    m.focusTimer = m.top.findNode("focusTimer")
    if m.focusTimer <> invalid then m.focusTimer.observeField("fire", "onFocusTimer")

    m.loadTimer = m.top.findNode("loadTimer")
    if m.loadTimer <> invalid then m.loadTimer.observeField("fire", "onLoadTimeout")

    m.deferLoadTimer = m.top.findNode("deferLoadTimer")
    if m.deferLoadTimer <> invalid then m.deferLoadTimer.observeField("fire", "onDeferLoad")

    m.rebuildTimer = m.top.findNode("rebuildTimer")
    if m.rebuildTimer <> invalid then m.rebuildTimer.observeField("fire", "onRebuildTimer")

    m.heroPreviewPlayTimer = m.top.findNode("heroPreviewPlayTimer")
    if m.heroPreviewPlayTimer <> invalid then m.heroPreviewPlayTimer.observeField("fire", "onHeroPreviewPlayTimer")

    m.heroOverlayTimer = m.top.findNode("heroOverlayTimer")
    if m.heroOverlayTimer <> invalid then m.heroOverlayTimer.observeField("fire", "onHeroOverlayTimer")
    m.heroOverlayFadeOut = m.top.findNode("heroOverlayFadeOut")
    m.heroOverlayFadeIn = m.top.findNode("heroOverlayFadeIn")

    m.epgDeferTimer = m.top.findNode("epgDeferTimer")
    if m.epgDeferTimer <> invalid then m.epgDeferTimer.observeField("fire", "onEpgDefer")

    hideAllSections()
    url = getPlaylistUrl()
    if url = "" then
        showStatus("No playlist. * = Settings. Left = menu.")
    else
        showStatus("Loading channels…")
    end if

    m.idleTimer.control = "start"
    applySafeLayout()
    keepInputFocus()
    if m.deferLoadTimer <> invalid then m.deferLoadTimer.control = "start"
end sub

sub onEpgDefer()
    loadEpg()
end sub

sub scheduleRebuild()
    if m.rebuildTimer <> invalid then
        m.rebuildTimer.control = "stop"
        m.rebuildTimer.control = "start"
    end if
end sub

sub onDeferLoad()
    loadWeather()
    loadUnsplashAmbientImages()
    if getPlaylistUrl() <> "" then loadChannels()
end sub

function heroPreviewIsPlaying() as boolean
    if m.heroPreview = invalid then return false
    if not m.heroPreview.visible then return false
    return m.heroPreview.state = "playing"
end function

function shouldAutoHideHeroOverlays() as boolean
    if m.heroFocused then return false
    if not shouldPlayHeroPreview() then return false
    return heroPreviewIsPlaying()
end function

sub cancelHeroOverlayTimer()
    if m.heroOverlayTimer <> invalid then m.heroOverlayTimer.control = "stop"
end sub

sub syncHeroOverlayLiveBadge()
    if m.liveBadge = invalid then return
    m.liveBadge.visible = heroPreviewIsPlaying()
end sub

sub showHeroOverlays()
    cancelHeroOverlayTimer()
    if m.heroOverlayFadeOut <> invalid then m.heroOverlayFadeOut.control = "stop"
    m.heroOverlaysVisible = true
    if m.heroOverlays = invalid then return
    if m.heroOverlayFadeIn <> invalid and m.heroOverlays.opacity < 0.99 then
        m.heroOverlays.visible = true
        m.heroOverlayFadeIn.control = "start"
    else
        if m.heroOverlayFadeIn <> invalid then m.heroOverlayFadeIn.control = "stop"
        m.heroOverlays.opacity = 1.0
        m.heroOverlays.visible = true
    end if
    syncHeroOverlayLiveBadge()
end sub

sub scheduleHeroOverlayHide()
    cancelHeroOverlayTimer()
    if not shouldAutoHideHeroOverlays() then return
    if not m.heroOverlaysVisible then return
    if m.heroOverlayTimer <> invalid then m.heroOverlayTimer.control = "start"
end sub

sub hideHeroOverlays()
    cancelHeroOverlayTimer()
    if m.heroOverlays = invalid then return
    m.heroOverlaysVisible = false
    if m.heroOverlayFadeOut <> invalid then
        m.heroOverlayFadeOut.control = "start"
    else
        m.heroOverlays.opacity = 0
    end if
end sub

sub onHeroOverlayTimer()
    if shouldAutoHideHeroOverlays() then hideHeroOverlays()
end sub

sub syncHeroOverlayVisibility()
    if m.heroFocused or not shouldAutoHideHeroOverlays() then
        showHeroOverlays()
    end if
end sub

sub onHeroUnfocused()
    renderHeroFocus()
    scheduleHeroOverlayHide()
end sub

sub applyHeroPreviewLayout()
    w = m.contentWidth
    if w = invalid or w <= 0 then w = 1804
    h = 560
    ' Let the Roku Video node scale to fit inside the hero rect. Manual
    ' oversizing + clip used to crop top/bottom on top of the player's own padding.
    if m.heroPreviewClip <> invalid then m.heroPreviewClip.clippingRect = [0, 0, w, h]
    if m.heroPreview <> invalid then
        m.heroPreview.width = w
        m.heroPreview.height = h
        m.heroPreview.translation = [0, 0]
    end if
end sub

function ambientUnsplashImages() as object
    return [
        "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1920&q=80"
        "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1920&q=80"
        "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1920&q=80"
        "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1920&q=80"
        "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=1920&q=80"
        "https://images.unsplash.com/photo-1426604966848-d7adac402bff?w=1920&q=80"
        "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=1920&q=80"
        "https://images.unsplash.com/photo-1518173946687-a4c8892bbd9f?w=1920&q=80"
    ]
end function

sub renderSidebar()
    expanded = m.sidebarFocused
    sidebarWidth = m.sidebarCollapsedWidth
    if expanded then sidebarWidth = m.sidebarExpandedWidth

    m.sidebarBg.width = sidebarWidth
    m.sidebarTitle.visible = expanded

    focusWidth = 52
    if expanded then focusWidth = sidebarWidth - 16

    for i = 0 to m.navIcons.Count() - 1
        m.navLabelNodes[i].visible = expanded
        m.navFocus[i].width = focusWidth

        if expanded and i = m.navIndex then
            m.navIcons[i].opacity = 1.0
        else
            m.navIcons[i].opacity = 0.72
        end if

        if i = m.activeNavIndex then
            m.navActive[i].opacity = 1.0
            m.navLabelNodes[i].color = m.theme.Accent
        else
            m.navActive[i].opacity = 0.0
            m.navLabelNodes[i].color = m.theme.TextSecondary
        end if

        if expanded and i = m.navIndex then
            m.navFocus[i].opacity = 1.0
            m.navLabelNodes[i].color = m.theme.TextPrimary
        else
            m.navFocus[i].opacity = 0.0
        end if
    end for

    applyContentLayout()
end sub

sub applySafeLayout()
    if m.sidebarGroup <> invalid then m.sidebarGroup.translation = [m.safeLeft, 0]
    if m.sidebarBg <> invalid then m.sidebarBg.height = 1080
    renderSidebar()
end sub

sub applyContentLayout()
    sidebarWidth = m.sidebarCollapsedWidth
    if m.sidebarFocused then sidebarWidth = m.sidebarExpandedWidth

    ' Shift right for left overscan, but keep the right edge flush with the screen.
    w = 1920 - m.safeLeft - sidebarWidth
    m.contentWidth = w
    railW = w - 100
    if railW < 400 then railW = 400

    m.content.translation = [m.safeLeft + sidebarWidth, 0]
    if m.zoneOverlay <> invalid then m.zoneOverlay.translation = [m.safeLeft + sidebarWidth, 0]
    if m.zoneBg <> invalid then m.zoneBg.width = w

    if m.heroBackdrop <> invalid then m.heroBackdrop.width = w
    if m.heroFocusBar <> invalid then m.heroFocusBar.width = w
    if m.recentGrid <> invalid then m.recentGrid.width = railW
    if m.onNowGrid <> invalid then m.onNowGrid.width = railW

    m.heroProgressWidth = Int(w * 760 / 1828)
    if m.heroProgressWidth < 200 then m.heroProgressWidth = 200
    if m.heroProgressTrack <> invalid then m.heroProgressTrack.width = m.heroProgressWidth

    clockBlockW = 400
    clockMargin = 48
    clockX = w - clockMargin - clockBlockW
    if clockX < 200 then clockX = 200
    if m.clockWeather <> invalid then m.clockWeather.translation = [clockX, 28]

    if m.upNextPanel <> invalid then m.upNextPanel.translation = [w - 388, 432]

    if m.buildTag <> invalid then
        m.buildTag.translation = [1680, 1044]
    end if

    applyHeroPreviewLayout()
end sub

sub focusSidebar()
    m.sidebarFocused = true
    m.navIndex = m.activeNavIndex
    m.heroFocused = false
    renderHeroFocus()
    renderSidebar()
    keepInputFocus()
end sub

sub leaveSidebar()
    m.sidebarFocused = false
    renderSidebar()
    m.railIdx = 0
    m.itemIdx = 0
    focusHome()
end sub

sub selectNav()
    zone = m.navZones[m.navIndex]
    m.activeNavIndex = m.navIndex
    renderSidebar()

    if zone = "HOME" then
        m.showLiveBrowse = false
        m.pendingLiveBrowseFocus = false
        showHomeZone()
        leaveSidebar()
    else if zone = "LIVE" then
        focusLiveBrowse()
    else if zone = "COMMAND" then
        m.sidebarFocused = false
        renderSidebar()
        showInfoZone("Command Center", "System monitoring and launcher controls are Android-specific in the current app. Roku limits third-party access to those APIs, so this zone is informational here.")
    else if zone = "SETTINGS" then
        showHomeZone()
        m.sidebarFocused = false
        renderSidebar()
        openSettings()
    end if
end sub

sub showHomeZone()
    m.zoneMode = "HOME"
    m.zoneOverlay.visible = false
    updateSectionVisibility()
    resumeHomePreview()
end sub

sub focusLiveBrowse()
    m.heroFocused = false
    m.showLiveBrowse = true
    m.activeNavIndex = m.navIndex
    m.zoneMode = "LIVE"
    m.zoneOverlay.visible = false
    stopHeroPreview()
    m.sidebarFocused = false
    renderSidebar()

    if m.channels.Count() = 0 then
        m.pendingLiveBrowseFocus = true
        showHint("Loading channels…")
        updateSectionVisibility()
        if not m.loadingChannels and getPlaylistUrl() <> "" then loadChannels()
        keepInputFocus()
        return
    end if

    if m.byId.Count() = 0 then
        m.pendingLiveBrowseFocus = true
        showHint("Preparing channel list…")
        scheduleBrowseRebuild()
        m.railIdx = 0
        m.itemIdx = 0
        updateSectionVisibility()
        syncGridFocus()
        keepInputFocus()
        return
    end if

    rebuildBrowseRails()
    updateSectionVisibility()
    focusAllChannelsRail()
    showStatus("")
    keepInputFocus()
end sub

sub focusAllChannelsRail()
    rails = visibleRails()
    for i = 0 to rails.Count() - 1
        if rails[i] = m.allChannelsGrid then
            m.railIdx = i
            m.itemIdx = 0
            syncGridFocus()
            return
        end if
    end for
    for i = 0 to rails.Count() - 1
        if rails[i] = m.groupsGrid then
            m.railIdx = i
            m.itemIdx = 0
            syncGridFocus()
            return
        end if
    end for
    m.railIdx = 0
    m.itemIdx = 0
    syncGridFocus()
end sub

sub finishLiveBrowseFocus()
    if not m.pendingLiveBrowseFocus then return
    if not m.showLiveBrowse then
        m.pendingLiveBrowseFocus = false
        return
    end if
    m.pendingLiveBrowseFocus = false
    showStatus("")
    focusAllChannelsRail()
end sub

sub resumeHomePreview()
    if m.heroCurrentItem <> invalid then
        if shouldPlayHeroPreview() then startHeroPreview(m.heroCurrentItem)
    else
        refreshHeroBillboard()
    end if
end sub

sub showInfoZone(title as string, body as string)
    stopHeroPreview()
    m.zoneMode = title
    m.zoneTitle.text = title
    m.zoneBody.text = body
    m.zoneOverlay.visible = true
end sub

sub applyUserSettings()
    m.showLivePreview = getShowLivePreview()
    m.showWeather = getShowWeather()
    m.clockDate.visible = m.showWeather
    m.clockDateShadow.visible = m.showWeather
    m.weatherTemp.visible = m.showWeather
    m.weatherTempShadow.visible = m.showWeather
    if not m.showWeather then
        m.ambient.weatherText = ""
        m.ambient.weatherDetail = ""
    end if
    m.idleTimer.duration = getIdleSeconds()
    if not m.showLivePreview then stopHeroPreview()
    syncAmbientWeather()
end sub

sub setHeroMetaVisible(show as boolean)
    m.heroNowLabel.visible = show
    m.heroNowShadow.visible = show
    m.heroChannel.visible = show
    m.heroChannelShadow.visible = show
    m.heroProgramme.visible = show
    m.heroProgrammeShadow.visible = show
    m.heroProgressTrack.visible = show
    m.heroProgressFill.visible = show
    if not show then m.upNextPanel.visible = false
end sub

sub focusHome()
    if m.settings <> invalid or m.search <> invalid then return
    if m.video.visible then
        m.video.setFocus(true)
        return
    end if
    syncGridFocus()
    refreshHeroBillboard()
    keepInputFocus()
end sub

sub keepInputFocus()
    if m.keyTrap <> invalid then m.keyTrap.setFocus(true)
end sub

sub showHint(msg as string)
    m.status.visible = true
    m.status.text = msg
end sub

sub showLoadingStatus(msg as string)
    m.status.visible = true
    m.status.text = msg + "  (* Settings, Left menu)"
end sub

sub showStatus(msg as string)
    if msg = "" then
        m.status.visible = false
        m.status.text = ""
        setHeroMetaVisible(true)
    else
        m.status.visible = true
        m.status.text = msg
        setHeroMetaVisible(false)
    end if
end sub

' ---- Loading ----

sub loadChannels()
    if m.loadingChannels then return
    url = getPlaylistUrl()
    if url = "" then
        m.loadTimer.control = "stop"
        hideAllSections()
        showStatus("No playlist. * = Settings. Left = menu.")
        focusHome()
        return
    end if
    m.loadingChannels = true
    showLoadingStatus("Loading channels")
    stopLoader(m.loader)
    if m.tasks = invalid then
        m.loadingChannels = false
        m.loadTimer.control = "stop"
        showStatus("App error: task host missing.")
        focusHome()
        return
    end if
    m.loader = m.tasks.createChild("PlaylistLoader")
    m.loader.url = url
    m.loader.observeField("channels", "onChannelsLoaded")
    m.loader.control = "RUN"
    m.loadTimer.control = "stop"
    m.loadTimer.control = "start"
end sub

sub stopLoader(loader as object)
    if loader = invalid then return
    loader.control = "STOP"
    if m.tasks <> invalid then m.tasks.removeChild(loader)
end sub

sub onLoadTimeout()
    m.loadingChannels = false
    stopLoader(m.loader)
    m.loader = invalid
    showStatus("Channel load timed out. * = Settings, Left = menu, OK = retry.")
    focusHome()
end sub

sub hideAllSections()
    m.recentHeader.visible = false
    m.recentGrid.visible = false
    m.onNowHeader.visible = false
    m.onNowGrid.visible = false
    m.groupsHeader.visible = false
    m.groupsGrid.visible = false
    m.allChannelsHeader.visible = false
    m.allChannelsGrid.visible = false
end sub

sub onChannelsLoaded()
    m.loadTimer.control = "stop"
    m.loadingChannels = false
    if m.loader = invalid then return
    m.loader.unobserveField("channels")
    chans = m.loader.channels
    stopLoader(m.loader)
    m.loader = invalid
    if chans = invalid or chans.Count() = 0 then
        hideAllSections()
        showStatus("No channels found. * = Settings, OK = retry.")
        return
    end if

    ' Store only — no grids, no index, no EPG on this callback. UI stays responsive.
    m.channels = chans
    m.railIdx = 0
    m.itemIdx = 0
    m.byId = {}
    m.indexCursor = 0
    m.groupList = []
    m.groupSeen = {}
    showStatus("Ready")

    m.rebuildPhase = 20
    scheduleRebuild()
end sub

sub onRebuildTimer()
    ' Phase 20: paint the small recent row (deferred so load callback returns fast).
    if m.rebuildPhase = 20 then
        recentChans = channelsForIds(getRecents())
        if recentChans.Count() = 0 then recentChans = capList(m.channels, 6)
        setRecentGridContent(recentChans)
        updateSectionVisibility()
        refreshHeroBillboard()
        syncGridFocus()
        showStatus("")
        m.rebuildPhase = 0
        if m.showLiveBrowse then scheduleBrowseRebuild()
        if m.epgDeferTimer <> invalid then
            m.epgDeferTimer.control = "stop"
            m.epgDeferTimer.control = "start"
        end if
        return
    end if

    ' Phase 12: build channel index in background ticks (only when Live TV opened).
    if m.rebuildPhase = 12 then
        done = buildChannelIndexChunk(30)
        if not done then
            scheduleRebuild()
            return
        end if
        m.rebuildPhase = 3
        scheduleRebuild()
        return
    end if

    if m.rebuildPhase = 3 then
        rebuildBrowseRails()
        updateSectionVisibility()
        m.rebuildPhase = 0
        finishLiveBrowseFocus()
        syncGridFocus()
        return
    end if

    rebuildContent()
    showStatus("")
    syncGridFocus()
end sub

sub scheduleBrowseRebuild()
    if m.channels.Count() = 0 then return
    if m.byId.Count() = 0 then
        m.indexCursor = 0
        m.groupList = []
        m.groupSeen = {}
        m.rebuildPhase = 12
    else
        m.rebuildPhase = 3
    end if
    scheduleRebuild()
end sub

sub rebuildBrowseRails()
    setGroupContent()
    allChans = channelsForGroup(m.selectedGroup)
    if m.selectedGroup = "All" then
        m.allChannelsHeader.text = "ALL CHANNELS"
    else
        m.allChannelsHeader.text = UCase(m.selectedGroup)
    end if
    setGridContent(m.allChannelsGrid, capList(allChans, 40))
end sub

sub buildChannelIndex()
    m.byId = {}
    m.indexCursor = 0
    m.groupList = []
    m.groupSeen = {}
    done = false
    while not done
        done = buildChannelIndexChunk(50)
    end while
end sub

function buildChannelIndexChunk(batch as integer) as boolean
    total = m.channels.Count()
    if total = 0 then return true
    startAt = m.indexCursor
    endAt = startAt + batch
    if endAt > total then endAt = total
    for i = startAt to endAt - 1
        ch = m.channels[i]
        id = ch.tvgId
        if id = "" then id = ch.name
        ch.id = id
        m.byId[id] = ch
        groupName = ch.group
        if groupName <> invalid and groupName <> "" and m.groupList.Count() < 30 then
            if m.groupSeen[groupName] = invalid then
                m.groupSeen[groupName] = true
                m.groupList.Push(groupName)
            end if
        end if
    end for
    m.indexCursor = endAt
    return m.indexCursor >= total
end function

sub loadWeather()
    if m.tasks = invalid then return
    stopLoader(m.weatherLoader)
    m.weatherLoader = m.tasks.createChild("WeatherLoader")
    m.weatherLoader.observeField("weather", "onWeatherLoaded")
    m.weatherLoader.control = "RUN"
end sub

sub loadUnsplashAmbientImages()
    if m.tasks = invalid then return
    key = getUnsplashAccessKey()
    if key = "" then return
    stopLoader(m.unsplashLoader)
    m.unsplashLoader = m.tasks.createChild("UnsplashLoader")
    m.unsplashLoader.accessKey = key
    m.unsplashLoader.observeField("photos", "onUnsplashLoaded")
    m.unsplashLoader.control = "RUN"
end sub

sub loadEpg()
    if m.tasks = invalid then return
    url = getEpgUrl()
    if url = "" then return
    stopLoader(m.epgLoader)
    m.epgLoader = m.tasks.createChild("EpgLoader")
    m.epgLoader.url = url
    m.epgLoader.observeField("epg", "onEpgLoaded")
    m.epgLoader.control = "RUN"
end sub

sub onUnsplashLoaded()
    photos = m.unsplashLoader.photos
    if photos <> invalid and photos.Count() > 0 then
        m.ambient.images = photos
    end if
end sub

sub onWeatherLoaded()
    weather = m.weatherLoader.weather
    if weather = invalid or weather.temperatureF = invalid then
        setShadowedText(m.weatherTemp, m.weatherTempShadow, "--")
        syncAmbientWeather()
        return
    end if
    setShadowedText(m.weatherTemp, m.weatherTempShadow, weather.temperatureF.ToStr() + "F")
    syncAmbientWeather()
end sub

sub syncAmbientWeather()
    if not m.showWeather then
        m.ambient.weatherText = ""
        m.ambient.weatherDetail = ""
        return
    end if
    m.ambient.weatherText = m.weatherTemp.text
    m.ambient.weatherDetail = ""
    weather = invalid
    if m.weatherLoader <> invalid then weather = m.weatherLoader.weather
    if weather <> invalid then
        detail = ""
        if weather.summary <> invalid then detail = weather.summary
        if weather.highF <> invalid and weather.lowF <> invalid then
            range = "H " + weather.highF.ToStr() + "F  L " + weather.lowF.ToStr() + "F"
            if detail <> "" then
                detail = detail + "  " + range
            else
                detail = range
            end if
        end if
        m.ambient.weatherDetail = detail
    end if
end sub

sub onEpgLoaded()
    if m.epgLoader = invalid then return
    epg = m.epgLoader.epg
    if epg = invalid then return
    m.epg = epg
    setGridContent(m.onNowGrid, onNowChannels())
    updateSectionVisibility()
    refreshHeroBillboard()
    syncGridFocus()
    keepInputFocus()
end sub

' ---- Row building ----

sub rebuildContent()
    recentChans = channelsForIds(getRecents())
    if recentChans.Count() = 0 then recentChans = capList(m.channels, 6)
    setRecentGridContent(recentChans)
    setGridContent(m.onNowGrid, onNowChannels())
    if m.showLiveBrowse then rebuildBrowseRails()
    updateSectionVisibility()
    clampRailSelection()
    refreshHeroBillboard()
end sub

function visibleRails() as object
    out = []
    if railFocusable(m.recentGrid) then out.Push(m.recentGrid)
    if railFocusable(m.onNowGrid) then out.Push(m.onNowGrid)
    if m.showLiveBrowse then
        if railFocusable(m.groupsGrid) then out.Push(m.groupsGrid)
        if railFocusable(m.allChannelsGrid) then out.Push(m.allChannelsGrid)
    end if
    return out
end function

sub clampRailSelection()
    rails = visibleRails()
    if rails.Count() = 0 then
        m.railIdx = 0
        m.itemIdx = 0
        return
    end if
    if m.railIdx >= rails.Count() then m.railIdx = rails.Count() - 1
    if m.railIdx < 0 then m.railIdx = 0
    grid = rails[m.railIdx]
    maxIdx = 0
    if grid.content <> invalid and grid.content.getChildCount() > 0 then
        maxIdx = grid.content.getChildCount() - 1
    end if
    if m.itemIdx > maxIdx then m.itemIdx = maxIdx
    if m.itemIdx < 0 then m.itemIdx = 0
end sub

sub onFocusTimer()
    keepInputFocus()
end sub

sub onRemoteKey()
    key = m.top.remoteKey
    if key <> invalid and key <> "" then dispatchKey(key)
end sub

sub highlightRailItem(grid as object, idx as integer)
    if grid = invalid or grid.content = invalid then return
    for i = 0 to grid.content.getChildCount() - 1
        item = grid.content.getChild(i)
        if item <> invalid then
            if i = idx then
                item.focusPercent = 1.0
            else
                item.focusPercent = 0.0
            end if
        end if
    end for
    grid.jumpToItem = idx
    if grid.hasField("jumpToRowItem") then grid.jumpToRowItem = [0, idx]
end sub

sub clearRailHighlights()
    for each grid in [ m.recentGrid, m.onNowGrid, m.groupsGrid, m.allChannelsGrid ]
        if grid <> invalid and grid.content <> invalid then
            for i = 0 to grid.content.getChildCount() - 1
                item = grid.content.getChild(i)
                if item <> invalid then item.focusPercent = 0.0
            end for
        end if
    end for
end sub

sub syncGridFocus()
    rails = visibleRails()
    if rails.Count() = 0 then
        renderHeroFocus()
        return
    end if
    clampRailSelection()
    if m.heroFocused then
        clearRailHighlights()
        renderHeroFocus()
        return
    end if
    grid = rails[m.railIdx]
    clearRailHighlights()
    highlightRailItem(grid, m.itemIdx)
    renderHeroFocus()
end sub

sub renderHeroFocus()
    if m.heroFocusBar <> invalid then m.heroFocusBar.visible = m.heroFocused and not m.sidebarFocused
    syncHeroOverlayVisibility()
end sub

sub focusHero()
    rails = visibleRails()
    if rails.Count() = 0 then return
    m.heroFocused = true
    clearRailHighlights()
    refreshHeroBillboard()
    renderHeroFocus()
    keepInputFocus()
end sub

sub leaveHero()
    m.heroFocused = false
    onHeroUnfocused()
    syncGridFocus()
end sub

function heroChannelId() as string
    ids = getRecents()
    if ids <> invalid and ids.Count() > 0 then return ids[0]
    if m.channels <> invalid and m.channels.Count() > 0 then
        ch = m.channels[0]
        if ch <> invalid and ch.id <> invalid and ch.id <> "" then return ch.id
    end if
    return ""
end function

sub refreshHeroBillboard()
    id = heroChannelId()
    if id = "" then
        updateHero(invalid)
        return
    end if
    ch = invalid
    if m.byId <> invalid and m.byId.Count() > 0 then ch = m.byId[id]
    if ch = invalid then ch = findChannelById(id)
    if ch = invalid then return
    root = CreateObject("roSGNode", "ContentNode")
    addItem(root, ch)
    item = root.getChild(0)
    if item <> invalid then updateHero(item)
end sub

function currentFocusGrid() as object
    rails = visibleRails()
    if rails.Count() = 0 then return invalid
    clampRailSelection()
    return rails[m.railIdx]
end function

sub activateRailItem()
    grid = currentFocusGrid()
    if grid = invalid then return
    if grid = m.groupsGrid then
        item = grid.content.getChild(m.itemIdx)
        if item = invalid then return
        m.selectedGroup = item.groupName
        rebuildBrowseRails()
        updateSectionVisibility()
        syncGridFocus()
        keepInputFocus()
        return
    end if
    playGridItem(grid, m.itemIdx)
end sub

sub playGridItem(grid as object, idx as integer)
    if grid = invalid or grid.content = invalid then return
    if idx < 0 or idx >= grid.content.getChildCount() then return
    item = grid.content.getChild(idx)
    if item = invalid then return
    addRecent(item.chId)
    playChannel(item)
end sub

function handleHomeKey(key as string) as boolean
    rails = visibleRails()
    if rails.Count() = 0 then
        if m.showLiveBrowse and m.channels.Count() > 0 then
            if key = "left" or key = "back" then
                focusSidebar()
                return true
            end if
            return true
        end if
        if key = "left" then
            focusSidebar()
            return true
        end if
        return false
    end if

    if m.heroFocused then
        if key = "down" then
            leaveHero()
            return true
        else if key = "left" then
            m.heroFocused = false
            onHeroUnfocused()
            focusSidebar()
            return true
        else if key = "right" or key = "up" then
            return true
        else if key = "OK" then
            if m.heroCurrentItem <> invalid then
                addRecent(m.heroCurrentItem.chId)
                playChannel(m.heroCurrentItem)
            end if
            return true
        end if
        return true
    end if

    m.heroFocused = false
    renderHeroFocus()

    clampRailSelection()
    grid = rails[m.railIdx]
    maxIdx = 0
    if grid.content <> invalid and grid.content.getChildCount() > 0 then
        maxIdx = grid.content.getChildCount() - 1
    end if

    if key = "left" then
        if m.itemIdx > 0 then
            m.itemIdx = m.itemIdx - 1
            syncGridFocus()
        else
            focusSidebar()
        end if
        return true
    else if key = "right" then
        if m.itemIdx < maxIdx then
            m.itemIdx = m.itemIdx + 1
            syncGridFocus()
        end if
        return true
    else if key = "down" then
        if m.railIdx < rails.Count() - 1 then
            m.railIdx = m.railIdx + 1
            m.itemIdx = 0
            syncGridFocus()
        end if
        return true
    else if key = "up" then
        if m.railIdx = 0 then
            if m.showLiveBrowse then
                return true
            else
                focusHero()
            end if
        else
            m.railIdx = m.railIdx - 1
            m.itemIdx = 0
            syncGridFocus()
        end if
        return true
    else if key = "OK" then
        activateRailItem()
        return true
    end if

    return false
end function

sub updateSectionVisibility()
    liveMode = m.showLiveBrowse
    liveActive = liveMode and m.channels.Count() > 0
    recentVisible = (not liveMode) and hasGridItems(m.recentGrid)
    onNowVisible = (not liveMode) and hasGridItems(m.onNowGrid)
    browseVisible = liveActive
    groupsVisible = browseVisible and hasGridItems(m.groupsGrid)
    allVisible = browseVisible and hasGridItems(m.allChannelsGrid)

    if m.heroGroup <> invalid then m.heroGroup.visible = not liveMode
    if m.liveBrowseTitle <> invalid then m.liveBrowseTitle.visible = liveMode

    m.recentHeader.visible = recentVisible
    m.recentGrid.visible = recentVisible
    m.onNowHeader.visible = onNowVisible
    m.onNowGrid.visible = onNowVisible
    m.groupsHeader.visible = groupsVisible
    m.groupsGrid.visible = groupsVisible
    m.allChannelsHeader.visible = allVisible
    m.allChannelsGrid.visible = allVisible

    applyBrowseModeLayout(liveMode)
end sub

sub applyBrowseModeLayout(liveMode as boolean)
    if liveMode then
        if m.groupsHeader <> invalid then m.groupsHeader.translation = [40, 72]
        if m.groupsGrid <> invalid then m.groupsGrid.translation = [40, 104]
        if m.allChannelsHeader <> invalid then m.allChannelsHeader.translation = [40, 168]
        if m.allChannelsGrid <> invalid then m.allChannelsGrid.translation = [40, 200]
    else
        if m.groupsHeader <> invalid then m.groupsHeader.translation = [40, 834]
        if m.groupsGrid <> invalid then m.groupsGrid.translation = [40, 866]
        if m.allChannelsHeader <> invalid then m.allChannelsHeader.translation = [40, 930]
        if m.allChannelsGrid <> invalid then m.allChannelsGrid.translation = [40, 962]
    end if
end sub

sub setGridContent(grid as object, chans as object)
    root = CreateObject("roSGNode", "ContentNode")
    if chans <> invalid then
        for each ch in chans
            addItem(root, ch)
        end for
    end if
    grid.content = root
end sub

function recentChipWidth(name as string) as integer
    if name = invalid then name = ""
    w = 80 + Len(name) * 12
    if w < 168 then w = 168
    if w > 360 then w = 360
    return w
end function

sub setRecentGridContent(chans as object)
    if chans = invalid then chans = []
    chans = capList(chans, 6)
    root = CreateObject("roSGNode", "ContentNode")
    sizes = []
    for each ch in chans
        addItem(root, ch)
        w = recentChipWidth(ch.name)
        idx = root.getChildCount() - 1
        if idx >= 0 then
            item = root.getChild(idx)
            if item <> invalid then item.width = w
        end if
        sizes.Push([w, 44])
    end for
    m.recentGrid.content = root
    if sizes.Count() > 0 then
        m.recentGrid.rowItemSize = [ sizes ]
    end if
end sub

sub setGroupContent()
    root = CreateObject("roSGNode", "ContentNode")
    addGroupItem(root, "All")
    groups = m.groupList
    if groups = invalid or groups.Count() = 0 then groups = groupNames()
    for each groupName in groups
        addGroupItem(root, groupName)
    end for
    m.groupsGrid.content = root
end sub

sub addGroupItem(root as object, title as string)
    item = root.CreateChild("ContentNode")
    item.title = title
    item.addFields({ groupName: title, focusPercent: 0.0 })
end sub

sub focusFirstRail()
    m.railIdx = 0
    m.itemIdx = 0
    syncGridFocus()
end sub

function railFocusable(grid as object) as boolean
    if grid = invalid or not grid.visible then return false
    return hasGridItems(grid)
end function

function hasGridItems(grid as object) as boolean
    if grid = invalid or grid.content = invalid then return false
    return grid.content.getChildCount() > 0
end function

sub addItem(root as object, ch as object)
    item = root.CreateChild("ContentNode")
    item.title = ch.name
    nowTitle = ""
    progress = 0.0
    timeLeft = ""
    nextTitle = ""
    if m.epg <> invalid and m.epg.Count() > 0 then
        info = epgFor(ch)
        if info <> invalid then
            nowTitle = info.nowTitle
            progress = info.progress
            timeLeft = info.timeLeft
            if info.nextTitle <> invalid then nextTitle = info.nextTitle
        end if
    end if
    item.addFields({
        streamUrl: ch.url
        channelId: ch.tvgId
        chId: ch.id
        logo: ch.logo
        favorite: isFavorite(ch.id)
        nowTitle: nowTitle
        progress: progress
        timeLeft: timeLeft
        nextTitle: nextTitle
        focusPercent: 0.0
    })
end sub

function channelsForIds(ids as object) as object
    out = []
    for each id in ids
        ch = invalid
        if m.byId.Count() > 0 then ch = m.byId[id]
        if ch = invalid then ch = findChannelById(id)
        if ch <> invalid then out.Push(ch)
    end for
    return out
end function

function findChannelById(id as string) as object
    if id = "" then return invalid
    for each ch in m.channels
        cid = ch.tvgId
        if cid = "" then cid = ch.name
        if ch.id <> invalid and ch.id <> "" then cid = ch.id
        if cid = id then return ch
    end for
    return invalid
end function

function onNowChannels() as object
    out = []
    if m.epg = invalid or m.epg.Count() = 0 then return out
    scanned = 0
    for each ch in m.channels
        scanned = scanned + 1
        if scanned > 200 then exit for
        info = epgFor(ch)
        if info <> invalid and info.nowTitle <> "" then
            out.Push(ch)
            if out.Count() >= 20 then return out
        end if
    end for
    return out
end function

function groupNames() as object
    out = []
    seen = {}
    scanned = 0
    for each ch in m.channels
        scanned = scanned + 1
        if scanned > 400 then exit for
        groupName = ch.group
        if groupName <> invalid and groupName <> "" and seen[groupName] = invalid then
            seen[groupName] = true
            out.Push(groupName)
            if out.Count() >= 30 then return out
        end if
    end for
    return out
end function

function channelsForGroup(groupName as string) as object
    if groupName = "" or groupName = "All" then return m.channels
    out = []
    for each ch in m.channels
        if ch.group = groupName then out.Push(ch)
    end for
    return out
end function

function capList(list as object, maxN as integer) as object
    if list.Count() <= maxN then return list
    out = []
    for i = 0 to maxN - 1
        out.Push(list[i])
    end for
    return out
end function

function epgFor(ch as object) as dynamic
    id = ch.tvgId
    if id = "" then id = ch.name
    if ch.id <> invalid and ch.id <> "" then id = ch.id
    info = m.epg[id]
    if info <> invalid then return info
    return m.epg[normId(id)]
end function

function normId(s as dynamic) as string
    if s = invalid then return ""
    lower = LCase(s)
    out = ""
    for i = 1 to Len(lower)
        c = Mid(lower, i, 1)
        if c <> " " and c <> "." then out = out + c
    end for
    return out
end function

' ---- Focus + selection ----

sub onGroupSelected()
    if m.groupsGrid = invalid or m.groupsGrid.content = invalid then return
    idx = m.groupsGrid.itemSelected
    if idx = invalid or idx < 0 or idx >= m.groupsGrid.content.getChildCount() then return
    item = m.groupsGrid.content.getChild(idx)
    if item = invalid then return
    m.selectedGroup = item.groupName
    rebuildBrowseRails()
    updateSectionVisibility()
    rails = visibleRails()
    for i = 0 to rails.Count() - 1
        if rails[i] = m.allChannelsGrid then
            m.railIdx = i
            m.itemIdx = 0
            focusHome()
            return
        end if
    end for
    syncGridFocus()
end sub

function focusedGridItem(grid as object) as dynamic
    if grid = invalid or grid.content = invalid then return invalid
    idx = grid.itemFocused
    if idx = invalid or idx < 0 or idx >= grid.content.getChildCount() then return invalid
    return grid.content.getChild(idx)
end function

sub updateHero(item as object)
    if item = invalid then
        m.heroCurrentItem = invalid
        setShadowedText(m.heroChannel, m.heroChannelShadow, "")
        setShadowedText(m.heroProgramme, m.heroProgrammeShadow, "")
        stopHeroPreview()
        return
    end if
    m.heroCurrentItem = item
    setShadowedText(m.heroChannel, m.heroChannelShadow, item.title)
    if item.nowTitle <> "" then
        prog = item.nowTitle
    else
        prog = "No programme info"
    end if
    setShadowedText(m.heroProgramme, m.heroProgrammeShadow, prog)
    if item.nextTitle <> "" then
        m.heroNext.text = "Next: " + item.nextTitle
        m.upNextPanel.visible = true
    else
        m.heroNext.text = ""
        m.upNextPanel.visible = false
    end if
    p = item.progress
    if p < 0 then p = 0
    if p > 1 then p = 1
    m.heroProgressFill.width = m.heroProgressWidth * p
    previewPlaying = heroPreviewIsPlaying()
    if shouldPlayHeroPreview() then
        startHeroPreview(item)
        if previewPlaying or heroPreviewIsPlaying() then
            showHeroOverlays()
            scheduleHeroOverlayHide()
        end if
    else
        stopHeroPreview()
    end if
end sub

function shouldPlayHeroPreview() as boolean
    if not m.showLivePreview then return false
    if m.heroCurrentItem = invalid then return false
    if m.video.visible then return false
    if m.zoneOverlay.visible then return false
    if m.settings <> invalid or m.search <> invalid then return false
    return m.zoneMode = "HOME"
end function

function iptvHttpHeaders() as object
    return {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
end function

function buildStreamContent(url as string, title as string, format as string) as object
    content = CreateObject("roSGNode", "ContentNode")
    content.url = url
    if format <> "" then content.streamFormat = format
    if title <> "" then content.title = title
    content.HttpHeaders = iptvHttpHeaders()
    return content
end function

function streamFormatForUrl(url as string) as string
    lower = LCase(url)
    if Instr(1, lower, ".mp4") > 0 then return "mp4"
    if Instr(1, lower, ".ism") > 0 then return "ism"
    if Instr(1, lower, ".mpd") > 0 then return "dash"
    return "hls"
end function

sub startHeroPreview(item as object)
    if item = invalid then
        stopHeroPreview()
        return
    end if

    streamUrl = item.streamUrl
    if streamUrl = invalid or streamUrl = "" then
        stopHeroPreview()
        return
    end if

    if m.video.visible then return
    if not m.showLivePreview then return
    if not shouldPlayHeroPreview() then return
    if m.heroPreviewUrl = streamUrl and m.heroPreview.visible and m.heroPreview.state = "playing" then return

    if m.heroPreviewPlayTimer <> invalid then m.heroPreviewPlayTimer.control = "stop"

    m.pendingHeroPreview = buildStreamContent(streamUrl, item.title, streamFormatForUrl(streamUrl))
    m.heroPreviewUrl = streamUrl
    m.heroPreviewWasPlaying = false
    m.heroPreviewRetry = 0

    m.heroPreview.control = "stop"
    if m.heroPreviewPlayTimer <> invalid then
        m.heroPreviewPlayTimer.control = "start"
    else
        applyPendingHeroPreview()
    end if
end sub

sub onHeroPreviewPlayTimer()
    applyPendingHeroPreview()
end sub

sub applyPendingHeroPreview()
    if m.pendingHeroPreview = invalid then return
    if not shouldPlayHeroPreview() then
        m.pendingHeroPreview = invalid
        return
    end if
    if m.video.visible then
        m.pendingHeroPreview = invalid
        return
    end if

    m.heroPreview.content = m.pendingHeroPreview
    m.pendingHeroPreview = invalid
    m.heroPreview.visible = true
    syncHeroOverlayLiveBadge()
    if m.heroBackdrop <> invalid then m.heroBackdrop.visible = false
    m.heroPreview.control = "play"
    showHeroOverlays()
    scheduleHeroOverlayHide()
end sub

sub stopHeroPreview()
    if m.heroPreviewPlayTimer <> invalid then m.heroPreviewPlayTimer.control = "stop"
    m.pendingHeroPreview = invalid
    if m.heroPreview = invalid then return
    m.heroPreview.control = "stop"
    m.heroPreview.visible = false
    if m.heroBackdrop <> invalid then m.heroBackdrop.visible = true
    m.heroPreviewUrl = ""
    m.heroPreviewWasPlaying = false
    m.heroPreviewRetry = 0
    showHeroOverlays()
end sub

sub onHeroPreviewState()
    st = m.heroPreview.state
    if st = "playing" then
        m.heroPreviewWasPlaying = true
        showHeroOverlays()
        scheduleHeroOverlayHide()
        return
    end if
    if st = "error" then
        m.heroPreviewWasPlaying = false
        if m.heroPreviewRetry = 0 and m.heroPreviewUrl <> "" and shouldPlayHeroPreview() then
            m.heroPreviewRetry = 1
            title = ""
            if m.heroCurrentItem <> invalid then title = m.heroCurrentItem.title
            m.pendingHeroPreview = buildStreamContent(m.heroPreviewUrl, title, "")
            if m.heroPreviewPlayTimer <> invalid then
                m.heroPreviewPlayTimer.control = "start"
            else
                applyPendingHeroPreview()
            end if
            return
        end if
        if shouldPlayHeroPreview() then stopHeroPreview()
        return
    end if
    if st = "finished" then
        if not m.heroPreviewWasPlaying then return
        m.heroPreviewWasPlaying = false
        if shouldPlayHeroPreview() then stopHeroPreview()
    end if
end sub

sub onRecentSelected()
    playSelectedGridItem(m.recentGrid)
end sub

sub onOnNowSelected()
    playSelectedGridItem(m.onNowGrid)
end sub

sub onAllChannelsSelected()
    playSelectedGridItem(m.allChannelsGrid)
end sub

sub playSelectedGridItem(grid as object)
    item = selectedGridItem(grid)
    if item = invalid then return
    addRecent(item.chId)
    playChannel(item)
end sub

function focusedChannelItem() as dynamic
    grid = currentFocusGrid()
    if grid = invalid or grid.content = invalid then return invalid
    if m.itemIdx < 0 or m.itemIdx >= grid.content.getChildCount() then return invalid
    return grid.content.getChild(m.itemIdx)
end function

sub toggleFocusedFavorite()
    item = focusedChannelItem()
    if item = invalid then return
    toggleFavorite(item.chId)
    rebuildContent()
    restoreFocusAfterFavoriteToggle(item.chId)
    refreshHeroBillboard()
end sub

sub restoreFocusAfterFavoriteToggle(channelId as string)
    rails = visibleRails()
    for ri = 0 to rails.Count() - 1
        grid = rails[ri]
        if grid.content <> invalid then
            for i = 0 to grid.content.getChildCount() - 1
                item = grid.content.getChild(i)
                if item <> invalid and item.chId = channelId then
                    m.railIdx = ri
                    m.itemIdx = i
                    focusHome()
                    return
                end if
            end for
        end if
    end for
    focusFirstRail()
end sub

function focusGridItemById(grid as object, channelId as string) as boolean
    if grid = invalid or grid.content = invalid then return false
    for i = 0 to grid.content.getChildCount() - 1
        item = grid.content.getChild(i)
        if item <> invalid and item.chId = channelId then
            rails = visibleRails()
            for ri = 0 to rails.Count() - 1
                if rails[ri] = grid then
                    m.railIdx = ri
                    m.itemIdx = i
                    focusHome()
                    return true
                end if
            end for
        end if
    end for
    return false
end function

sub updateHeroById(channelId as string)
    refreshHeroBillboard()
end sub

function selectedGridItem(grid as object) as dynamic
    if grid = invalid or grid.content = invalid then return invalid
    idx = grid.itemSelected
    if idx = invalid or idx < 0 or idx >= grid.content.getChildCount() then return invalid
    return grid.content.getChild(idx)
end function

sub playChannel(item as object)
    stopHeroPreview()
    content = buildStreamContent(item.streamUrl, item.title, streamFormatForUrl(item.streamUrl))
    m.video.content = content
    m.video.visible = true
    m.video.control = "play"
    m.video.setFocus(true)
end sub

sub onVideoState()
    st = m.video.state
    if st = "error" or st = "finished" then stopVideo()
end sub

sub stopVideo()
    m.video.control = "stop"
    m.video.visible = false
    rebuildContent()
    focusHome()
end sub

' ---- Settings ----

sub openSettings()
    if m.settings <> invalid then return
    if m.overlays = invalid then return
    stopHeroPreview()
    m.settings = m.overlays.createChild("SettingsScene")
    m.settings.observeField("settingsChanged", "onSettingsChanged")
    keepInputFocus()
end sub

sub openSearch()
    if m.search <> invalid then return
    if m.overlays = invalid then return
    stopHeroPreview()
    m.search = m.overlays.createChild("SearchScene")
    m.search.channels = m.channels
    m.search.observeField("chosenUrl", "onSearchChosen")
    m.search.observeField("closed", "onSearchClosed")
    m.search.setFocus(true)
end sub

sub onSearchChosen()
    if m.search = invalid then return
    item = CreateObject("roSGNode", "ContentNode")
    item.title = m.search.chosenName
    item.addFields({
        streamUrl: m.search.chosenUrl
        chId: m.search.chosenId
        nowTitle: ""
        progress: 0.0
        timeLeft: ""
        nextTitle: ""
    })
    closeSearch()
    addRecent(item.chId)
    playChannel(item)
end sub

sub onSearchClosed()
    closeSearch()
end sub

sub closeSearch()
    if m.search = invalid then return
    if m.overlays <> invalid then m.overlays.removeChild(m.search)
    m.search = invalid
    m.activeNavIndex = 0
    m.navIndex = 0
    renderSidebar()
    focusHome()
end sub

sub onSettingsChanged()
    applyUserSettings()
    loadUnsplashAmbientImages()
    keepInputFocus()
    loadChannels()
end sub

sub closeSettings()
    if m.settings = invalid then return
    if m.overlays <> invalid then m.overlays.removeChild(m.settings)
    m.settings = invalid
    m.activeNavIndex = 0
    m.navIndex = 0
    renderSidebar()
    focusHome()
end sub

' ---- Idle / clock ----

sub onIdle()
    ' Keep live preview running on Home — ambient slideshow is Android launcher behavior.
    if m.zoneMode = "HOME" then return
    m.heroFocused = false
    renderHeroFocus()
    stopHeroPreview()
    m.ambient.active = true
end sub

sub onClockTick()
    now = CreateObject("roDateTime")
    now.ToLocalTime()
    h = now.GetHours()
    suffix = "AM"
    if h >= 12 then suffix = "PM"
    h12 = h mod 12
    if h12 = 0 then h12 = 12
    txt = h12.ToStr() + ":" + Right("0" + now.GetMinutes().ToStr(), 2) + " " + suffix
    dateTxt = dateLabel(now)
    setShadowedText(m.clock, m.clockShadow, txt)
    setShadowedText(m.clockDate, m.clockDateShadow, dateTxt)
    m.ambient.clockText = txt
    m.ambient.dateText = dateTxt
end sub

sub setShadowedText(main as object, shadow as object, text as string)
    if main <> invalid then main.text = text
    if shadow <> invalid then shadow.text = text
end sub

function dateLabel(now as object) as string
    days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
    months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
    dayIdx = now.GetDayOfWeek()
    if dayIdx < 0 or dayIdx >= days.Count() then dayIdx = 0
    monthIdx = now.GetMonth() - 1
    if monthIdx < 0 or monthIdx >= months.Count() then monthIdx = 0
    return days[dayIdx] + ", " + months[monthIdx] + " " + now.GetDayOfMonth().ToStr()
end function

' ---- Keys ----

function normKey(key as string) as string
    k = LCase(key)
    if k = "ok" then return "OK"
    return k
end function

function processSidebarKey(key as string) as boolean
    k = normKey(key)

    if k = "up" then
        if m.navIndex > 0 then
            m.navIndex = m.navIndex - 1
            renderSidebar()
        end if
        keepInputFocus()
        return true
    else if k = "down" then
        if m.navIndex < m.navZones.Count() - 1 then
            m.navIndex = m.navIndex + 1
            renderSidebar()
        end if
        keepInputFocus()
        return true
    else if k = "OK" then
        selectNav()
        keepInputFocus()
        return true
    else if k = "right" then
        selectNav()
        keepInputFocus()
        return true
    else if k = "left" or k = "back" then
        leaveSidebar()
        return true
    end if

    keepInputFocus()
    return true
end function

function settingsDialogOpen() as boolean
    if m.settings = invalid then return false
    return m.settings.callFunc("dialogOpen")
end function

function settingsProxyKey(key as string) as boolean
    if m.settings = invalid then return false
    return m.settings.callFunc("proxyKey", key)
end function

function dispatchKey(key as string) as boolean
    if key = invalid or key = "" then return false

    tick = CreateObject("roTimespan").TotalMilliseconds()
    if key = m.lastDispatchKey and (tick - m.lastDispatchTick) < 180 then return true
    m.lastDispatchKey = key
    m.lastDispatchTick = tick

    k = normKey(key)
    m.keyCount = m.keyCount + 1

    if m.settings <> invalid and settingsDialogOpen() then return false
    if m.search <> invalid then return false
    if m.sidebarFocused then return processSidebarKey(key)
    return processKey(key, true)
end function

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    return dispatchKey(key)
end function

function processKey(key as string, press as boolean) as boolean
    if not press then return false

    k = normKey(key)

    if k = "options" or k = "info" then
        if m.video.visible then return false
        if m.settings = invalid then openSettings()
        return true
    end if

    if m.ambient.active then
        m.ambient.active = false
        if not m.sidebarFocused then
            m.activeNavIndex = 0
            m.navIndex = 0
            renderSidebar()
            focusHome()
        end if
    end if
    m.idleTimer.control = "start"

    if m.settings <> invalid then
        if settingsDialogOpen() then return false
        if settingsProxyKey(key) then return true
        if k = "back" then
            closeSettings()
            return true
        end if
        return true
    end if

    if m.search <> invalid then
        if k = "back" then
            closeSearch()
            return true
        end if
        return false
    end if

    if m.zoneOverlay.visible then
        if k = "back" then
            m.activeNavIndex = 0
            m.navIndex = 0
            m.showLiveBrowse = false
            m.pendingLiveBrowseFocus = false
            showHomeZone()
            renderSidebar()
            focusHome()
            return true
        else if k = "left" then
            focusSidebar()
            return true
        else if k = "search" then
            openSearch()
            return true
        end if
        return true
    end if

    if k = "back" and m.video.visible then
        stopVideo()
        return true
    end if

    if k = "search" then
        if m.video.visible then return false
        openSearch()
        return true
    else if k = "play" then
        if m.video.visible then return false
        toggleFocusedFavorite()
        return true
    end if

    if m.video.visible then return false

    if k = "OK" and visibleRails().Count() = 0 then
        if getPlaylistUrl() = "" then
            openSettings()
        else if not m.loadingChannels then
            loadChannels()
        end if
        return true
    end if

    if k = "left" then
        if handleHomeKey("left") then return true
        focusSidebar()
        return true
    end if

    if handleHomeKey(k) then return true

    return false
end function
