#!/bin/bash
# Run this from any machine on your home network to escape TizenTube.
# Adjust IP below to match your Shield's IP.
SHIELD_IP=192.168.1.128
ADB_CMD="$ANDROID_HOME/platform-tools/adb"
if ! command -v "$ADB_CMD" &>/dev/null; then
    ADB_CMD=$(which adb 2>/dev/null)
fi
if ! command -v "$ADB_CMD" &>/dev/null; then
    echo "adb not found — install Android platform tools or adjust the path."
    exit 1
fi
$ADB_CMD connect "$SHIELD_IP":5555 >/dev/null || {
    echo "Could not connect to $SHIELD_IP:5555"
    exit 1
}
$ADB_CMD shell input keyevent KEYCODE_HOME
$ADB_CMD shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.livingroomhq/.MainActivity
echo "LRHQ launched."
