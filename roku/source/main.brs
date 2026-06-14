' Entry point. Creates the SceneGraph screen and runs the HomeScene event loop.
' Remote keys are handled only by KeyHandler -> dispatchKey (not roInput) to avoid
' double-processing every press, which breaks rail navigation.
sub Main()
    screen = CreateObject("roSGScreen")
    m.port = CreateObject("roMessagePort")
    screen.setMessagePort(m.port)

    m.scene = screen.CreateScene("HomeScene")
    screen.show()

    focusKeyHandler(m.scene)

    while true
        msg = wait(0, m.port)
        if type(msg) = "roSGScreenEvent"
            if msg.isScreenClosed() then return
        end if
    end while
end sub

sub focusKeyHandler(scene as object)
    if scene = invalid then return
    kh = scene.findNode("keyHandler")
    if kh = invalid then return
    trap = kh.findNode("trap")
    if trap <> invalid then trap.setFocus(true)
end sub
