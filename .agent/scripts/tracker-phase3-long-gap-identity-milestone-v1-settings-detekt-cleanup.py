from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


exporter = Path("feature/settings/src/main/java/io/blueeye/feature/settings/DatabaseExporter.kt")
replace_once(
    exporter,
    '''            put(
                "identityContinuityCandidates",
                JsonArray(session.identityCandidates.map(::mapIdentityCandidate)),
            )
''',
    '''            put("identityContinuityCandidates", SessionIdentityCandidateExportMapper.map(session.identityCandidates))
''',
)
replace_once(
    exporter,
    '''    private fun mapIdentityCandidate(candidate: IdentityContinuityCandidate): JsonObject =
        buildJsonObject {
            put("id", candidate.id)
            put("deviceFingerprint", candidate.deviceFingerprint)
            put("candidateFingerprint", candidate.candidateFingerprint)
            put("timestamp", candidate.timestamp)
            put("reasonCode", candidate.reasonCode)
            put("confidence", candidate.confidence)
            put("featureSummary", candidate.featureSummary)
            put("verdict", candidate.verdict.name)
        }

''',
    '''''',
)
replace_once(
    exporter,
    '''}

private val SessionReviewDeviceQueueDecision.kind: String
''',
    '''}

internal object SessionIdentityCandidateExportMapper {
    fun map(candidates: List<IdentityContinuityCandidate>): JsonArray =
        JsonArray(candidates.map(::mapCandidate))

    private fun mapCandidate(candidate: IdentityContinuityCandidate): JsonObject =
        buildJsonObject {
            put("id", candidate.id)
            put("deviceFingerprint", candidate.deviceFingerprint)
            put("candidateFingerprint", candidate.candidateFingerprint)
            put("timestamp", candidate.timestamp)
            put("reasonCode", candidate.reasonCode)
            put("confidence", candidate.confidence)
            put("featureSummary", candidate.featureSummary)
            put("verdict", candidate.verdict.name)
        }
}

private val SessionReviewDeviceQueueDecision.kind: String
''',
)
