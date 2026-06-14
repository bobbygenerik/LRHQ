' Full-screen focus trap that forwards remote keys to HomeScene.dispatchKey().

sub init()
    m.trap = m.top.findNode("trap")
end sub

sub forwardKey(key as string)
    scene = m.top.getScene()
    if scene = invalid then return
    if scene.hasFunc("dispatchKey") then
        scene.callFunc("dispatchKey", key)
    else
        scene.remoteKey = key
    end if
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    forwardKey(key)
    return true
end function
