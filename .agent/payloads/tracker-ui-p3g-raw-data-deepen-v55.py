from pathlib import Path

screen = Path("feature/details/src/main/java/io/blueeye/feature/details/DetailsScreen.kt")
text = screen.read_text()

old_top = '''                    IconButton(onClick = { showRawDataDialog.value = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Raw Data")
                    }
'''
assert text.count(old_top) == 1
text = text.replace(old_top, "")

old_call = '''                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() },
                )
'''
new_call = '''                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() },
                    onOpenRawData = { showRawDataDialog.value = true },
                )
'''
assert text.count(old_call) == 1
text = text.replace(old_call, new_call)
screen.write_text(text)

technical = Path("feature/details/src/main/java/io/blueeye/feature/details/DetailsTechnicalState.kt")
text = technical.read_text()

old_import = '''import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
'''
new_import = '''import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
'''
assert text.count(old_import) == 1
text = text.replace(old_import, new_import)

old_signature = '''    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
'''
new_signature = '''    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenRawData: () -> Unit,
    modifier: Modifier = Modifier,
'''
assert text.count(old_signature) == 1
text = text.replace(old_signature, new_signature)

old_tail = '''        if (state.services.isNotEmpty()) {
            InfoSection(
                title = "Services (${state.services.size})",
                items = state.services.map { service -> service.uuid to service.name },
            )
        }
    }
}
'''
new_tail = '''        if (state.services.isNotEmpty()) {
            InfoSection(
                title = "Services (${state.services.size})",
                items = state.services.map { service -> service.uuid to service.name },
            )
        }

        OutlinedButton(
            onClick = onOpenRawData,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Raw Data")
        }
    }
}
'''
assert text.count(old_tail) == 1
text = text.replace(old_tail, new_tail)
technical.write_text(text)
