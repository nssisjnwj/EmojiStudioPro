
package app.emojistudio.pro

import android.os.Bundle
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

private val Purple = Color(0xFF9B5CFF)
private val Dark = Color(0xFF0C0B12)
private val Panel = Color(0xFF191722)

private const val EMPTY_JSON =
    """{"v":"5.7.4","fr":30,"ip":0,"op":1,"w":1,"h":1,"nm":"empty","ddd":0,"assets":[],"layers":[]}"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EmojiStudioApp() }
    }
}

data class Language(
    val code: String,
    val title: String,
    val flag: String
)

private val languages = listOf(
    Language("fa", "پارسی", "🇮🇷"),
    Language("en", "English", "🇬🇧"),
    Language("ar", "العربية", "🇸🇦"),
    Language("fr", "Français", "🇫🇷"),
    Language("es", "Español", "🇪🇸"),
    Language("tr", "Türkçe", "🇹🇷")
)

private val translations = mapOf(
    "fa" to listOf(
        "خانه", "ویرایشگر", "تنظیمات", "ایموجی‌های من",
        "وارد کردن ایموجی", "انتخاب فایل", "فایل انتخاب شد",
        "هنوز ایموجی وارد نکرده‌اید", "پخش انیمیشن",
        "توقف", "زبان برنامه", "ذخیره پروژه",
        "فایل با موفقیت وارد شد", "خطا در خواندن فایل",
        "تایم‌لاین", "لایه‌ها", "نام فایل"
    ),
    "en" to listOf(
        "Home", "Editor", "Settings", "My Emojis",
        "Import Emoji", "Choose File", "File selected",
        "No emoji imported yet", "Play Animation",
        "Pause", "App Language", "Save Project",
        "File imported successfully", "Error reading file",
        "Timeline", "Layers", "File name"
    ),
    "ar" to listOf(
        "الرئيسية", "المحرر", "الإعدادات", "إيموجياتي",
        "استيراد إيموجي", "اختر ملفاً", "تم اختيار الملف",
        "لم يتم استيراد إيموجي بعد", "تشغيل الحركة",
        "إيقاف", "لغة التطبيق", "حفظ المشروع",
        "تم استيراد الملف بنجاح", "خطأ في قراءة الملف",
        "الخط الزمني", "الطبقات", "اسم الملف"
    ),
    "fr" to listOf(
        "Accueil", "Éditeur", "Paramètres", "Mes emojis",
        "Importer un emoji", "Choisir un fichier", "Fichier sélectionné",
        "Aucun emoji importé", "Lire l'animation",
        "Pause", "Langue", "Enregistrer",
        "Import réussi", "Erreur de lecture",
        "Chronologie", "Calques", "Nom du fichier"
    ),
    "es" to listOf(
        "Inicio", "Editor", "Ajustes", "Mis emojis",
        "Importar emoji", "Elegir archivo", "Archivo seleccionado",
        "Aún no hay emojis", "Reproducir animación",
        "Pausar", "Idioma", "Guardar proyecto",
        "Archivo importado", "Error al leer archivo",
        "Línea de tiempo", "Capas", "Nombre del archivo"
    ),
    "tr" to listOf(
        "Ana Sayfa", "Düzenleyici", "Ayarlar", "Emojilerim",
        "Emoji İçe Aktar", "Dosya Seç", "Dosya seçildi",
        "Henüz emoji yok", "Animasyonu oynat",
        "Duraklat", "Uygulama dili", "Projeyi kaydet",
        "Dosya başarıyla aktarıldı", "Dosya okuma hatası",
        "Zaman çizelgesi", "Katmanlar", "Dosya adı"
    )
)

@Composable
fun EmojiStudioApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("emoji_studio", Context.MODE_PRIVATE)
    }

    var language by remember {
        mutableStateOf(prefs.getString("language", "fa") ?: "fa")
    }

    var page by remember { mutableIntStateOf(0) }
    var animationJson by remember {
        mutableStateOf(prefs.getString("last_json", null))
    }
    var fileName by remember {
        mutableStateOf(prefs.getString("last_name", "") ?: "")
    }
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showLanguages by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val t = translations[language] ?: translations.getValue("en")

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val resolver = context.contentResolver

            // File reading is performed asynchronously below.
            kotlinx.coroutines.MainScope().launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw Exception("Cannot open file")
                    }

                    val decoded = if (
                        bytes.size >= 2 &&
                        bytes[0] == 0x1f.toByte() &&
                        bytes[1] == 0x8b.toByte()
                    ) {
                        GZIPInputStream(ByteArrayInputStream(bytes))
                            .bufferedReader(Charsets.UTF_8)
                            .use { it.readText() }
                    } else {
                        bytes.toString(Charsets.UTF_8)
                    }

                    if (!decoded.trimStart().startsWith("{")) {
                        throw Exception("Invalid animation JSON")
                    }

                    animationJson = decoded
                    fileName = uri.lastPathSegment ?: "emoji.tgs"
                    prefs.edit()
                        .putString("last_json", decoded)
                        .putString("last_name", fileName)
                        .apply()

                    Toast.makeText(
                        context, t[12], Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context, t[13] + ": " + (e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    importing = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Dark,
            surface = Panel
        )
    ) {
        Scaffold(
            containerColor = Dark,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Emoji Studio Pro",
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "CREATE • CUSTOMIZE • ANIMATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Purple
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Panel
                    ),
                    actions = {
                        IconButton(onClick = { page = 2 }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(
                        selected = page == 0,
                        onClick = { page = 0 },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text(t[0]) }
                    )
                    NavigationBarItem(
                        selected = page == 1,
                        onClick = { page = 1 },
                        icon = { Icon(Icons.Default.Edit, null) },
                        label = { Text(t[1]) }
                    )
                    NavigationBarItem(
                        selected = page == 2,
                        onClick = { page = 2 },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(t[2]) }
                    )
                }
            }
        ) { padding ->
            when (page) {
                0 -> HomePage(
                    Modifier.padding(padding),
                    t,
                    fileName,
                    animationJson != null,
                    importing,
                    onImport = {
                        picker.launch(
                            arrayOf(
                                "application/json",
                                "application/gzip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onEditor = { page = 1 }
                )

                1 -> EditorPage(
                    Modifier.padding(padding),
                    t,
                    animationJson,
                    fileName,
                    playing,
                    progress,
                    onPlay = { playing = !playing },
                    onProgress = { progress = it },
                    onImport = {
                        picker.launch(arrayOf("*/*"))
                    }
                )

                2 -> SettingsPage(
                    Modifier.padding(padding),
                    t,
                    language,
                    showLanguages,
                    onShowLanguages = { showLanguages = true },
                    onLanguage = {
                        language = it
                        prefs.edit().putString("language", it).apply()
                        showLanguages = false
                    }
                )
            }
        }

        if (showLanguages) {
            AlertDialog(
                onDismissRequest = { showLanguages = false },
                title = { Text(t[10]) },
                text = {
                    Column {
                        languages.forEach { item ->
                            TextButton(
                                onClick = { 
                                    language = item.code
                                    prefs.edit().putString(
                                        "language", item.code
                                    ).apply()
                                    showLanguages = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${item.flag}   ${item.title}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLanguages = false
                    }) { Text("OK") }
                }
            )
        }
    }
}

@Composable
fun HomePage(
    modifier: Modifier,
    t: List<String>,
    fileName: String,
    hasFile: Boolean,
    importing: Boolean,
    onImport: () -> Unit,
    onEditor: () -> Unit
) {
    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            "✨ Emoji Studio Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Create and customize Telegram animations",
            color = Color.LightGray
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Animation,
                    null,
                    tint = Purple,
                    modifier = Modifier.size(65.dp)
                )
                Text(
                    if (hasFile) fileName else t[7],
                    fontWeight = FontWeight.Bold
                )
                if (hasFile) {
                    Text("TGS / Lottie", color = Purple)
                }
            }
        }

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            enabled = !importing
        ) {
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White
                )
            } else {
                Icon(Icons.Default.FileOpen, null)
                Spacer(Modifier.width(10.dp))
                Text(t[4])
            }
        }

        OutlinedButton(
            onClick = onEditor,
            modifier = Modifier.fillMaxWidth().height(55.dp)
        ) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text(t[1])
        }
    }
}

@Composable
fun EditorPage(
    modifier: Modifier,
    t: List<String>,
    json: String?,
    fileName: String,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onProgress: (Float) -> Unit,
    onImport: () -> Unit
) {
    val spec = LottieCompositionSpec.JsonString(json ?: EMPTY_JSON)
    val composition by rememberLottieComposition(spec)
    val animatedProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playing && json != null
    )

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            t[1],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Box(
            Modifier.fillMaxWidth()
                .weight(1f)
                .background(Panel, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (json != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Animation,
                        null,
                        tint = Purple,
                        modifier = Modifier.size(60.dp)
                    )
                    Text(t[7])
                }
            }
        }

        Text(t[14], color = Purple)
        Slider(
            value = if (playing) animatedProgress else progress,
            onValueChange = onProgress
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlay, enabled = json != null) {
                Icon(
                    if (playing) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    null
                )
                Spacer(Modifier.width(6.dp))
                Text(if (playing) t[9] else t[8])
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Default.FileOpen, null)
                Text(t[5])
            }
        }

        Text("${t[15]}: $fileName", color = Color.LightGray)
    }
}

@Composable
fun SettingsPage(
    modifier: Modifier,
    t: List<String>,
    language: String,
    showLanguages: Boolean,
    onShowLanguages: () -> Unit,
    onLanguage: (String) -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            t[2],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(t[10], color = Purple)
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onShowLanguages,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selected = languages.first {
                        it.code == language
                    }
                    Text(
                        "${selected.flag}   ${selected.title}",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
        }

        Text(
            "Emoji Studio Pro",
            color = Color.LightGray
        )
        Text("Version 1.1", color = Purple)
    }
}
