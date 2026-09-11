from pathlib import Path

path = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
text = path.read_text()
old = '''    fun map(candidates: List<IdentityContinuityCandidate>): JsonArray =
        JsonArray(candidates.map(::mapCandidate))
'''
new = '''    fun map(candidates: List<IdentityContinuityCandidate>): JsonArray = JsonArray(candidates.map(::mapCandidate))
'''
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one mapper expression: {text.count(old)}")
path.write_text(text.replace(old, new, 1))
