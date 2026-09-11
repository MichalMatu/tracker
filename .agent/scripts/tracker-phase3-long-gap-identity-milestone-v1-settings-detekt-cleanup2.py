from pathlib import Path

path = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
text = path.read_text()
old = '            put("identityCandidateCount", session.identityCandidates.size)\n'
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one identityCandidateCount line: {text.count(old)}")
path.write_text(text.replace(old, "", 1))
