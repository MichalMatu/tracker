package io.blueeye.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Task B: The "Chat Simulation" Visual Proof
@Composable
fun ThemeShowcaseScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Semantic Chat Proof",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )

        // Mock Chat Stream
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. User Message (Standard M3)
            UserMessageBubble(text = "System check. Status report?")

            // 2. Agent Message (Semantic Safe)
            AgentMessageBubble(text = "All systems nominal. Environment scan complete.")

            // 3. User Message
            UserMessageBubble(text = "Any signals needing review?")

            // 4. System Alert (Semantic Dangerous)
            SystemAlertBubble(text = "REVIEW: 2 unidentified signals need evidence review.")

            // 5. Action Suggestion (Semantic Suspicious)
            ActionSuggestionBubble(text = "RECOMMENDATION: Check evidence for Signal #A7-22")

            // 6. Agent Message
            AgentMessageBubble(text = "Awaiting your command, Operator.")
        }
    }
}

@Composable
fun UserMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.extendedColors.userBubbleContainer,
                        shape = RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = text,
                color = MaterialTheme.extendedColors.onUserBubble,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun AgentMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.extendedColors.agentBubbleContainer,
                        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
                    )
                    .padding(12.dp)
        ) {
            Text(
                text = text,
                color = MaterialTheme.extendedColors.onAgentBubble,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun SystemAlertBubble(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.extendedColors.dangerous.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.extendedColors.dangerous,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun ActionSuggestionBubble(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.extendedColors.suspicious.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.extendedColors.suspicious,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// -----------------------------------------------------------------------------
// The "Matrix" Preview (Multi-Theme Variance)
// -----------------------------------------------------------------------------

@Preview(widthDp = 400, heightDp = 900)
@Composable
fun MasterpieceShowcaseMatrix() {
    Column(Modifier.fillMaxSize()) {
        // LIGHT Mode Frame
        Box(Modifier.weight(1f)) {
            BlueEyeTheme(darkTheme = false) {
                ThemeShowcaseScreen()
            }
            Text(
                "LIGHT",
                Modifier
                    .padding(4.dp)
                    .background(androidx.compose.ui.graphics.Color.White),
                fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
            )
        }

        // DARK Mode Frame
        Box(Modifier.weight(1f)) {
            BlueEyeTheme(darkTheme = true) {
                ThemeShowcaseScreen()
            }
            Text("DARK", Modifier.padding(4.dp).background(androidx.compose.ui.graphics.Color.LightGray))
        }

        // FOREST Mode Frame
        Box(Modifier.weight(1f)) {
            BlueEyeTheme(darkTheme = true, colorSchemeName = "Forest") {
                ThemeShowcaseScreen()
            }
            Text("FOREST", Modifier.padding(4.dp).background(androidx.compose.ui.graphics.Color.Green))
        }
    }
}
