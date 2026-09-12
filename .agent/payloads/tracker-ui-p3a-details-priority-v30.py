from pathlib import Path

screen_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt')
evidence_section_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsEvidenceSection.kt')
formatter_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsEvidenceUiFormatter.kt')
test_path = Path('feature/details/src/test/java/io/blueeye/feature/details/DetailsEvidenceUiFormatterTest.kt')

screen = screen_path.read_text()
old_title = '''                title = {
                    Column {
                        Text(
                            text = device?.getDisplayName() ?: "Unknown Device",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = fingerprint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },'''
new_title = '''                title = {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },'''
if screen.count(old_title) != 1:
    raise SystemExit('unexpected TopAppBar title block')
screen = screen.replace(old_title, new_title, 1)

old_body = '''                // Header Info
                HeaderCard(dev)

                DetailsEvidenceSection(evidence = dev.evidence)

                if (alertEvidenceEvents.isNotEmpty()) {
                    DetailsAlertHistoryCard(events = alertEvidenceEvents)
                }

                CalibrationCard(
                    device = dev,
                    onSelectLabel = { viewModel.updateCalibrationLabel(it) },
                )

                // Connection Control
                ConnectionCard(
                    connectionState = connectionState,
                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() }
                )

                if (signalSamples.isNotEmpty()) {
                    DetailsSignalHistoryCard(samples = signalSamples)
                }

                if (followMeHistory.isNotEmpty()) {
                    DetailsFollowMeHistoryCard(samples = followMeHistory)
                }

                // Sensor Data
                if (sensorData != null) {
                    SensorDataCard(sensorData!!)
                }

                // Info Sections
                InfoSection("Identity",
                    listOf(
                        "Vendor" to (dev.vendorName ?: "Unknown"),
                        "Technology" to dev.technology,
                        "Type" to dev.deviceType.name
                    )
                )

                InfoSection("Activity",
                    listOf(
                        "First Seen" to DetailsUiFormatter.formatFriendlyTimestamp(dev.firstSeenAt),
                        "Last Seen" to DetailsUiFormatter.formatFriendlyTimestamp(dev.lastSeenAt),
                        "Encounters" to dev.encounterCount.toString()
                    )
                )
'''
new_body = '''                // Decision-first content: summary, tracking/signal, key evidence, identity, then review/actions.
                HeaderCard(dev)

                if (signalSamples.isNotEmpty()) {
                    DetailsSignalHistoryCard(samples = signalSamples)
                }

                if (followMeHistory.isNotEmpty()) {
                    DetailsFollowMeHistoryCard(samples = followMeHistory)
                }

                DetailsKeyEvidenceSection(evidence = dev.evidence)

                InfoSection("Identity",
                    listOf(
                        "Vendor" to (dev.vendorName ?: "Unknown"),
                        "Technology" to dev.technology,
                        "Type" to dev.deviceType.name
                    )
                )

                CalibrationCard(
                    device = dev,
                    onSelectLabel = { viewModel.updateCalibrationLabel(it) },
                )

                if (alertEvidenceEvents.isNotEmpty()) {
                    DetailsAlertHistoryCard(events = alertEvidenceEvents)
                }

                // Technical and diagnostic actions stay available, but below decision content.
                ConnectionCard(
                    connectionState = connectionState,
                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() }
                )

                if (sensorData != null) {
                    SensorDataCard(sensorData!!)
                }

                InfoSection("Activity",
                    listOf(
                        "First Seen" to DetailsUiFormatter.formatFriendlyTimestamp(dev.firstSeenAt),
                        "Last Seen" to DetailsUiFormatter.formatFriendlyTimestamp(dev.lastSeenAt),
                        "Encounters" to dev.encounterCount.toString()
                    )
                )
'''
if screen.count(old_body) != 1:
    raise SystemExit('unexpected Details content block')
screen = screen.replace(old_body, new_body, 1)

old_services_tail = '''                if (services.isNotEmpty()) {
                    InfoSection("Services (${services.size})", services.map { it.uuid to it.name })
                }

                Spacer(Modifier.height(Dimens.PaddingExtraLarge * 2)) // Spacing for FAB
'''
new_services_tail = '''                if (services.isNotEmpty()) {
                    InfoSection("Services (${services.size})", services.map { it.uuid to it.name })
                }

                DetailsEvidenceSection(
                    evidence = dev.evidence,
                    title = "All evidence",
                )

                Spacer(Modifier.height(Dimens.PaddingExtraLarge * 2)) // Spacing for FAB
'''
if screen.count(old_services_tail) != 1:
    raise SystemExit('unexpected services tail')
screen = screen.replace(old_services_tail, new_services_tail, 1)
screen_path.write_text(screen)

section = evidence_section_path.read_text()
old_signature = '''fun DetailsEvidenceSection(
    evidence: List<DetectionEvidence>,
    modifier: Modifier = Modifier,
) {'''
new_signature = '''fun DetailsEvidenceSection(
    evidence: List<DetectionEvidence>,
    modifier: Modifier = Modifier,
    title: String = "Evidence",
) {'''
if section.count(old_signature) != 1:
    raise SystemExit('unexpected DetailsEvidenceSection signature')
section = section.replace(old_signature, new_signature, 1)
old_title_text = '''            Text(
                text = "Evidence",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )'''
new_title_text = '''            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )'''
if section.count(old_title_text) != 1:
    raise SystemExit('unexpected evidence title')
section = section.replace(old_title_text, new_title_text, 1)

key_section = '''
@Composable
fun DetailsKeyEvidenceSection(
    evidence: List<DetectionEvidence>,
    modifier: Modifier = Modifier,
) {
    val evidenceItems = remember(evidence) { DetailsEvidenceUiFormatter.formatKeyEvidence(evidence) }
    if (evidenceItems.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth().stableLiveHeight()) {
        Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
            Text(
                text = "Key evidence",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.PaddingSmall))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                evidenceItems.forEach { item ->
                    DetailsKeyEvidenceItem(item)
                }
            }
        }
    }
}

@Composable
private fun DetailsKeyEvidenceItem(item: DetailsEvidenceUiItem) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = item.sourceText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DetailsEvidenceConfidencePill(item)
        }
        Text(
            text = item.reasonText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = EvidenceLayout.REASON_TEXT_LINES,
            maxLines = EvidenceLayout.REASON_TEXT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

'''
marker = '@Composable\nfun DetailsEvidenceSection('
if section.count(marker) != 1:
    raise SystemExit('unexpected evidence section marker')
section = section.replace(marker, key_section + marker, 1)
evidence_section_path.write_text(section)

formatter = formatter_path.read_text()
marker = '    fun format(\n'
key_formatter = '''    fun formatKeyEvidence(
        evidence: List<DetectionEvidence>,
        timestampFormatter: (Long) -> String = DetailsUiFormatter::formatFriendlyTimestamp,
    ): List<DetailsEvidenceUiItem> =
        format(
            evidence = evidence.filter(DetectionEvidenceClassifier::isAttentionEvidence),
            timestampFormatter = timestampFormatter,
        ).take(KEY_EVIDENCE_LIMIT)

'''
if formatter.count(marker) != 1:
    raise SystemExit('unexpected formatter marker')
formatter = formatter.replace(marker, key_formatter + marker, 1)
const_marker = '    private const val EMPTY_TECHNICAL_VALUE = "None"\n'
if formatter.count(const_marker) != 1:
    raise SystemExit('unexpected formatter constants')
formatter = formatter.replace(const_marker, const_marker + '    private const val KEY_EVIDENCE_LIMIT = 3\n', 1)
formatter_path.write_text(formatter)

test = test_path.read_text()
insert_before = '''    private fun evidence(
'''
new_tests = '''    @Test
    fun `key evidence keeps only attention evidence and caps default list at three`() {
        val items =
            DetailsEvidenceUiFormatter.formatKeyEvidence(
                evidence =
                    listOf(
                        evidence(EvidenceSource.MODEL, DetectionConfidence.LOW, "Low model context"),
                        evidence(EvidenceSource.NAME, DetectionConfidence.MEDIUM, "Medium name match"),
                        evidence(EvidenceSource.SERVICE_UUID, DetectionConfidence.HIGH, "High service match"),
                        evidence(EvidenceSource.WATCHLIST, DetectionConfidence.CRITICAL, "Watchlist match"),
                        evidence(EvidenceSource.GATT_PROBE, DetectionConfidence.MEDIUM, "Probe result"),
                    ),
                timestampFormatter = { it.toString() },
            )

        assertEquals(3, items.size)
        assertEquals("Watchlist", items[0].sourceText)
        assertEquals("Service UUID", items[1].sourceText)
        assertFalse(items.any { item -> item.sourceText == "Model" })
    }

'''
if test.count(insert_before) != 1:
    raise SystemExit('unexpected test insertion marker')
test = test.replace(insert_before, new_tests + insert_before, 1)
test_path.write_text(test)
