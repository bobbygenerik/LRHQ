## 2025-05-18 - Safe Protobuf Duration Parsing
**Learning:** Google API Protobuf duration strings are returned in seconds with a trailing 's' (e.g. "3.5s"). Trimming whitespace prior to removing the suffix ensures clean string parsing without NumberFormatException or default fallbacks when whitespace is present.
**Action:** Always trim whitespace before removing string suffixes for numerical parsing in network responses.
