from pathlib import Path

section_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsEvidenceSection.kt')
section = section_path.read_text()
old_blank = 'import io.blueeye.core.ui.theme.extendedColors\n\n\n@Composable'
new_blank = 'import io.blueeye.core.ui.theme.extendedColors\n\n@Composable'
if old_blank not in section:
    raise SystemExit('expected ktlint blank-line pattern not found')
section_path.write_text(section.replace(old_blank, new_blank, 1))

screen_path = Path('feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt')
screen = screen_path.read_text()
old_state = '''    val showEditDialog = remember { mutableStateOf(false) }
    val showRawDataDialog = remember { mutableStateOf(false) }
'''
new_state = '''    val showEditDialog = remember(fingerprint) { mutableStateOf(false) }
    val showRawDataDialog = remember(fingerprint) { mutableStateOf(false) }
'''
if old_state not in screen:
    raise SystemExit('expected Details dialog state pattern not found')
screen_path.write_text(screen.replace(old_state, new_state, 1))
