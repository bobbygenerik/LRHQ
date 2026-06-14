' Fetches a random batch of landscape photos from Unsplash for the ambient overlay.
' The access key is stored in Roku registry via Settings, not hardcoded in source.

sub init()
    m.top.functionName = "loadUnsplash"
end sub

sub loadUnsplash()
    key = m.top.accessKey
    if key = invalid or key = "" then
        m.top.photos = []
        return
    end if

    url = "https://api.unsplash.com/photos/random"
    url = url + "?orientation=landscape"
    url = url + "&count=24"
    url = url + "&query=landscape,nature,cinematic,aerial"
    url = url + "&client_id=" + key

    body = httpGet(url)
    if body = invalid or body = "" then
        m.top.photos = []
        return
    end if

    parsed = ParseJson(body)
    if parsed = invalid then
        m.top.photos = []
        return
    end if

    photos = []
    for each photo in parsed
        if photo <> invalid and photo.urls <> invalid and photo.urls.raw <> invalid then
            photos.Push(photo.urls.raw + "&w=1920&q=80&fm=jpg&fit=crop")
        end if
    end for
    m.top.photos = photos
end sub
