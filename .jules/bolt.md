## 2025-03-08 - Cache Regex Compilations in Polling Loops
**Learning:** Re-instantiating `Regex` in frequently executed code or polling loops compiles a pattern and allocates matcher objects every time.
**Action:** Always cache `Regex` instances in a companion object or top-level constant when used repeatedly.
