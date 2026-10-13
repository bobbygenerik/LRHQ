## 2025-05-24 - Pre-compiled Regex in Hot Paths
**Learning:** Instantiating `Regex` instances inside repeatedly invoked functions causes unnecessary regex parsing and compilation overhead on every execution.
**Action:** Extract regular expressions into top-level or object constants (`private val REGEX = Regex(...)`) when used in utility or parsing functions to compile the pattern once and reuse it across calls.
