' Shared blocking HTTP GET for Task threads. Returns the body string or invalid.
function httpGet(url as string) as dynamic
    if url = "" then return invalid
    xfer = CreateObject("roUrlTransfer")
    xfer.setUrl(url)
    xfer.setCertificatesFile("common:/certs/ca-bundle.crt")
    xfer.initClientCertificates()
    ' Many IPTV/EPG hosts have self-signed or mismatched certs; be lenient.
    xfer.enableHostVerification(false)
    xfer.enablePeerVerification(false)
    port = CreateObject("roMessagePort")
    xfer.setMessagePort(port)
    if xfer.asyncGetToString() then
        msg = wait(20000, port)
        if type(msg) = "roUrlEvent" then
            if msg.getResponseCode() = 200 then return msg.getString()
        else
            xfer.asyncCancel()
        end if
    end if
    return invalid
end function
