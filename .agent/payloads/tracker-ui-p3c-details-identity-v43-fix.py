from pathlib import Path

path = Path('feature/details/src/test/java/io/blueeye/feature/details/DetailsIdentityUiFormatterTest.kt')
text = path.read_text()
old = '''    private fun List<Pair<String, String>>.value(label: String): String = first { (itemLabel, _) -> itemLabel == label }.second
'''
new = '''    private fun List<Pair<String, String>>.value(
        label: String,
    ): String = first { (itemLabel, _) -> itemLabel == label }.second
'''
assert old in text
path.write_text(text.replace(old, new, 1))
