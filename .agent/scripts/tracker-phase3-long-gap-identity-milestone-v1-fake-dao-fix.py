from pathlib import Path

path = Path("core/data/src/test/kotlin/io/blueeye/core/data/verify/AppleDeduplicationScenariosTest.kt")
text = path.read_text()
old = '''        override suspend fun moveAlertEvidenceEvents(targetFingerprint: String, sourceFingerprint: String) {}\n        override suspend fun deleteByFingerprint(fingerprint: String) { \n'''
new = '''        override suspend fun moveAlertEvidenceEvents(targetFingerprint: String, sourceFingerprint: String) {}\n        override suspend fun moveIdentityCandidates(targetFingerprint: String, sourceFingerprint: String) {}\n        override suspend fun retargetIdentityCandidates(targetFingerprint: String, sourceFingerprint: String) {}\n        override suspend fun deleteByFingerprint(fingerprint: String) { \n'''
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one fake DAO anchor, got {text.count(old)}")
path.write_text(text.replace(old, new, 1))
