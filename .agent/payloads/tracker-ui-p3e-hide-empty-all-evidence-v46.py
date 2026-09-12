from pathlib import Path

path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt')
text = path.read_text()
old = '''                DetailsEvidenceSection(
                    evidence = dev.evidence,
                    title = "All evidence",
                )
'''
new = '''                if (dev.evidence.isNotEmpty()) {
                    DetailsEvidenceSection(
                        evidence = dev.evidence,
                        title = "All evidence",
                    )
                }
'''
assert text.count(old) == 1
path.write_text(text.replace(old, new, 1))
