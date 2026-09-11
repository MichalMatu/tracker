from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


scan_context = Path("core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/ScanDataContext.kt")
replace_once(
    scan_context,
    '''import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.data.tracker.model.IdentityCandidateMatch
import io.blueeye.core.model.MacAddressType
''',
    '''import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.tracker.model.IdentityCandidateMatch
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
''',
)

history = Path("core/data/src/main/java/io/blueeye/core/data/repository/DeviceHistoryDataSource.kt")
replace_once(
    history,
    '''import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.SignalSample
''',
    '''import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
''',
)
