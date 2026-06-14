' LivingRoom HQ palette, mirrored from core/ui/theme/Theme.kt (HqColors).
' Colors are 0xRRGGBBAA for SceneGraph color fields.
function Theme() as object
    return {
        Void:          "0x000000FF"
        Abyss:         "0x05070DFF"
        Slate:         "0x0C1018FF"
        GlassFill:     "0xFFFFFF0C"   ' matches Android HqColors.GlassFill
        GlassStroke:   "0xFFFFFF22"
        Accent:        "0x2BE080FF"   ' default green accent
        AccentDim:     "0x2BE08029"
        TextPrimary:   "0xF2F5FAFF"
        TextSecondary: "0xD7DEE8B3"
        TextTertiary:  "0xC2CBD866"
    }
end function
