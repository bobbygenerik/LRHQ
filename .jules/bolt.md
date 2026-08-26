## 2025-05-18 - Hoisting Regex Compilation in Kotlin String Extensions
**Learning:** Instantiating `Regex` instances inline inside frequently called extension functions or loops incurs significant compilation and object allocation overhead.
**Action:** Always extract static regular expressions into package-level or object constants (`private val REGEX = Regex(...)`) to reuse compiled patterns and avoid re-allocation on every call.
