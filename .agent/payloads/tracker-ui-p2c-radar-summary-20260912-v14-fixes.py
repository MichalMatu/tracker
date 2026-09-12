from pathlib import Path

# Avoid top-level helper-name collision with DeviceMapper.kt.
mapper = Path('core/data/src/main/java/io/blueeye/core/data/mapper/RadarDeviceMapper.kt')
s = mapper.read_text()
count = s.count('RssiNormalization')
assert count >= 2, count
mapper.write_text(s.replace('RssiNormalization', 'RadarRssiNormalization'))

# Keep DeviceDao test fake compatible and detekt-clean.
test_file = Path('core/data/src/test/kotlin/io/blueeye/core/data/verify/AppleDeduplicationScenariosTest.kt')
s = test_file.read_text()
watchlist_import = 'import io.blueeye.core.data.db.entity.WatchlistEntity\n'
assert watchlist_import in s
if 'import io.blueeye.core.data.db.projection.RadarDeviceProjection\n' not in s:
    s = s.replace(watchlist_import, watchlist_import + 'import io.blueeye.core.data.db.projection.RadarDeviceProjection\n', 1)
needle = '        override fun getRecentDevicesFlow(sinceTimestamp: Long): Flow<List<DeviceEntity>> = flowOf(emptyList())\n'
assert needle in s
s = s.replace(
    needle,
    needle
    + '        override fun getRecentRadarDevicesFlow(\n'
    + '            sinceTimestamp: Long,\n'
    + '        ): Flow<List<RadarDeviceProjection>> = flowOf(emptyList())\n',
    1,
)
test_file.write_text(s)

# RadarViewModel now consumes the lightweight summary end-to-end.
vm = Path('feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarViewModel.kt')
s = vm.read_text()
if 'import io.blueeye.core.model.RadarDeviceSummary\n' not in s:
    anchor = 'import io.blueeye.core.model.DeviceType\n'
    assert anchor in s
    s = s.replace(anchor, anchor + 'import io.blueeye.core.model.RadarDeviceSummary\n', 1)
list_count = s.count('List<Device>')
assert list_count >= 1, list_count
s = s.replace('List<Device>', 'List<RadarDeviceSummary>')
s = s.replace('import io.blueeye.core.model.Device\n', '')
vm.write_text(s)

# Preserve the ordering test contract without the intentionally removed item.device field.
order_test = Path('feature/radar/src/test/java/io/blueeye/feature/radar/presentation/RadarUiCardOrderTest.kt')
s = order_test.read_text()
replacements = {
    'item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW).withLastSeen(NOW + 10_000)': 'item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW, lastSeenAt = NOW + 10_000)',
    'item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000).withLastSeen(NOW)': 'item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000, lastSeenAt = NOW)',
    'item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW).withLastSeen(NOW)': 'item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW, lastSeenAt = NOW)',
    'item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000).withLastSeen(NOW + 20_000)': 'item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000, lastSeenAt = NOW + 20_000)',
}
for old, new in replacements.items():
    assert old in s, old
    s = s.replace(old, new, 1)
needle = '        firstSeenAt: Long = NOW,\n        priority: RadarItemPriority = RadarItemPriority.ORDINARY,\n'
assert needle in s
s = s.replace(
    needle,
    '        firstSeenAt: Long = NOW,\n        lastSeenAt: Long = NOW,\n        priority: RadarItemPriority = RadarItemPriority.ORDINARY,\n',
    1,
)
s = s.replace('                    lastSeenAt = NOW,', '                    lastSeenAt = lastSeenAt,', 1)
helper = '    private fun RadarUiItem.withLastSeen(lastSeenAt: Long): RadarUiItem {\n'
assert helper in s
start = s.index(helper)
end = s.index('    private enum class RadarItemPriority {', start)
s = s[:start] + s[end:]
order_test.write_text(s)

# Existing object gains one extra focused helper; suppress the object-level threshold only.
evidence_factory = Path('core/data/src/main/java/io/blueeye/core/data/evidence/DeviceEvidenceFactory.kt')
s = evidence_factory.read_text()
obj = 'object DeviceEvidenceFactory {\n'
assert obj in s
if '@Suppress("TooManyFunctions")\nobject DeviceEvidenceFactory {' not in s:
    s = s.replace(obj, '@Suppress("TooManyFunctions")\n' + obj, 1)
while '\n\n\n' in s:
    s = s.replace('\n\n\n', '\n\n')
evidence_factory.write_text(s)

# Test fixture deliberately exposes all contract dimensions.
signals_test = Path('core/data/src/test/kotlin/io/blueeye/core/data/evidence/DeviceEvidenceFactoryRadarSignalsTest.kt')
s = signals_test.read_text()
needle = '    private fun device(\n'
assert needle in s
if '    @Suppress("LongParameterList")\n    private fun device(' not in s:
    s = s.replace(needle, '    @Suppress("LongParameterList")\n' + needle, 1)
signals_test.write_text(s)

print(f'P2C_V14_FIXES mapper_occurrences={count} vm_list_device_replacements={list_count}')
