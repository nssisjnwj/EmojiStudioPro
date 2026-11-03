
package app.emojistudio.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmojiStudioApp()
        }
    }
}

@Composable
fun EmojiStudioApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB69AFF),
            background = Color(0xFF0C0B12),
            surface = Color(0xFF191722)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFF0C0B12),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("EMOJI STUDIO", fontWeight = FontWeight.Black)
                            Text(
                                "PRO • Animation Workspace",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB69AFF)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF14121D)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF191722)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.EmojiEmotions, null) },
                        label = { Text("Emojis") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Edit, null) },
                        label = { Text("Editor") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Folder, null) },
                        label = { Text("Templates") }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> HomeScreen(Modifier.padding(padding))
                1 -> EditorScreen(Modifier.padding(padding))
                2 -> TemplatesScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            "My Emoji Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Manage and create your Telegram premium emojis.",
            color = Color.LightGray
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF211D30)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Animation,
                    null,
                    tint = Color(0xFFB69AFF),
                    modifier = Modifier.size(55.dp)
                )
                Text("No emojis imported yet")
                Text(
                    "Import your first TGS animation.",
                    color = Color.Gray
                )
            }
        }

        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Import Emoji")
        }
    }
}

@Composable
fun EditorScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(
            "Animation Editor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Color(0xFF211D30),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Animation,
                    null,
                    modifier = Modifier.size(65.dp),
                    tint = Color(0xFFB69AFF)
                )
                Text("Animation Preview")
                Text("Import an emoji to begin", color = Color.Gray)
            }
        }

        Text("TIMELINE", color = Color(0xFFB69AFF))
        Slider(value = 0f, onValueChange = {})

        Text("LAYERS", color = Color(0xFFB69AFF))
        Text("No layers loaded", color = Color.Gray)

        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Text Layer")
        }
    }
}

@Composable
fun TemplatesScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(
            "My Templates",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF211D30)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Folder,
                    null,
                    tint = Color(0xFFB69AFF)
                )
                Spacer(Modifier.width(12.dp))
                Text("My Templates")
            }
        }
    }
}
