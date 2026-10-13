## 2026-08-17 - Avoid Repeated Regex Compilations in High-Frequency Extension Functions
**Learning:** Instantiating `Regex` inside extension functions called repeatedly during batch processing (such as channel guide normalization) causes significant overhead from recompiling regex patterns on every call.
**Action:** Extract repeated `Regex` patterns to private top-level constants or companion object constants so they are compiled once at initialization time.
