## 2025-05-18 - Pre-compile Regex Patterns in Guide Matcher
**Learning:** Instantiating `Regex` inside frequently called extension functions (such as string normalization during EPG/playlist channel matching) causes repeated regex compilation and memory allocation overhead on every string evaluation.
**Action:** Always extract static regular expressions into top-level or object `private val` pre-compiled `Regex` constants when used in hot path utility functions.
