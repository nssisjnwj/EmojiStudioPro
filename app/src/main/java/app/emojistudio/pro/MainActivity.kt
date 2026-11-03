
package app.emojistudio.pro

import android.os.Bundle
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
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
        setContent {
            EmojiStudioApp()
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiStudioApp() {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(
            "emoji_studio",
            Context.MODE_PRIVATE
        )
    }

    val scope = rememberCoroutineScope()

    var language by remember {
        mutableStateOf(
            prefs.getString("language", "fa") ?: "fa"
        )
    }

    var page by remember { mutableIntStateOf(0) }

    var animationJson by remember {
        mutableStateOf(prefs.getString("last_json", null))
    }

    var fileName by remember {
        mutableStateOf(prefs.getString("last_name", "") ?: "")
    }

    var playing by remember { mutableStateOf(true) }
    var showLanguages by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val t = translations[language] ?: translations.getValue("en")

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->

        if (uri != null) {
            importing = true

            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            scope.launch {
                try {
                    val resolver = context.contentResolver

                    val bytes = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use {
                            it.readBytes()
                        } ?: throw Exception("Cannot open file")
                    }

                    val decoded = withContext(Dispatchers.IO) {
                        val isGzip =
                            bytes.size >= 2 &&
                            bytes[0] == 0x1f.toByte() &&
                            bytes[1] == 0x8b.toByte()

                        if (isGzip) {
                            GZIPInputStream(
                                ByteArrayInputStream(bytes)
                            ).bufferedReader(Charsets.UTF_8).use {
                                it.readText()
                            }
                        } else {
                            bytes.toString(Charsets.UTF_8)
                        }
                    }

                    if (!decoded.trimStart().startsWith("{")) {
                        throw Exception("Invalid animation JSON")
                    }

                    animationJson = decoded
                    fileName = uri.lastPathSegment
                        ?.substringAfterLast("/")
                        ?: "emoji.tgs"

                    prefs.edit()
                        .putString("last_json", decoded)
                        .putString("last_name", fileName)
                        .apply()

                    Toast.makeText(
                        context,
                        t[12],
                        Toast.LENGTH_LONG
                    ).show()

                    page = 1

                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        t[13] + ": " + (e.message ?: ""),
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
                        IconButton(
                            onClick = { page = 2 }
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            },

            bottomBar = {
                NavigationBar(
                    containerColor = Panel
                ) {
                    NavigationBarItem(
                        selected = page == 0,
                        onClick = { page = 0 },
                        icon = {
                            Icon(Icons.Default.Home, null)
                        },
                        label = {
                            Text(t[0])
                        }
                    )

                    NavigationBarItem(
                        selected = page == 1,
                        onClick = { page = 1 },
                        icon = {
                            Icon(Icons.Default.Edit, null)
                        },
                        label = {
                            Text(t[1])
                        }
                    )

                    NavigationBarItem(
                        selected = page == 2,
                        onClick = { page = 2 },
                        icon = {
                            Icon(Icons.Default.Settings, null)
                        },
                        label = {
                            Text(t[2])
                        }
                    )
                }
            }
        ) { padding ->

            when (page) {
                0 -> HomePage(
                    modifier = Modifier.padding(padding),
                    t = t,
                    fileName = fileName,
                    hasFile = animationJson != null,
                    importing = importing,
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
                    onEditor = {
                        page = 1
                    }
                )

                1 -> EditorPage(
                    modifier = Modifier.padding(padding),
                    t = t,
                    json = animationJson,
                    fileName = fileName,
                    playing = playing,
                    onPlay = {
                        playing = !playing
                    },
                    onImport = {
                        picker.launch(arrayOf("*/*"))
                    },
                    onJsonChanged = { updated ->
                        animationJson = updated
                        prefs.edit().putString("last_json", updated).apply()
                    }
                )

                2 -> SettingsPage(
                    modifier = Modifier.padding(padding),
                    t = t,
                    language = language,
                    onShowLanguages = {
                        showLanguages = true
                    }
                )
            }
        }

        if (showLanguages) {
            AlertDialog(
                onDismissRequest = {
                    showLanguages = false
                },
                title = {
                    Text(t[10])
                },
                text = {
                    Column {
                        languages.forEach { item ->
                            TextButton(
                                onClick = {
                                    language = item.code

                                    prefs.edit()
                                        .putString("language", item.code)
                                        .apply()

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
                    TextButton(
                        onClick = {
                            showLanguages = false
                        }
                    ) {
                        Text("OK")
                    }
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
        modifier
            .fillMaxSize()
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
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
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
                    Text(
                        "TGS / Lottie",
                        color = Purple
                    )
                }
            }
        }

        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text(t[1])
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPage(
    modifier: Modifier,
    t: List<String>,
    json: String?,
    fileName: String,
    playing: Boolean,
    onPlay: () -> Unit,
    onImport: () -> Unit,
    onJsonChanged: (String) -> Unit
) {
    val context = LocalContext.current
    var documentJson by remember(json) { mutableStateOf(json) }
    val document = remember(documentJson) {
        documentJson?.let { runCatching { LottieDocument.parse(it) }.getOrNull() }
    }
    var selectedPath by remember(documentJson) { mutableStateOf<String?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var refreshTree by remember { mutableIntStateOf(0) }
    val nodes = remember(documentJson, refreshTree) {
        document?.let { runCatching { it.layerTree() }.getOrDefault(emptyList()) } ?: emptyList()
    }
    val selected = remember(nodes, selectedPath) {
        fun find(items: List<LottieLayerNode>): LottieLayerNode? {
            for (item in items) {
                if (item.path == selectedPath) return item
                find(item.children)?.let { return it }
            }
            return null
        }
        find(nodes)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null && document != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(document.toTgsBytes())
                } ?: error("Cannot create output file")
                Toast.makeText(context, "TGS exported successfully", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applyEdit(edit: (LottieDocument) -> Unit) {
        val current = document ?: return
        runCatching {
            edit(current)
            val updated = current.json()
            documentJson = updated
            onJsonChanged(updated)
            refreshTree++
        }.onFailure {
            Toast.makeText(context, it.message ?: "Edit failed", Toast.LENGTH_LONG).show()
        }
    }

    val spec = LottieCompositionSpec.JsonString(documentJson ?: EMPTY_JSON)
    val composition by rememberLottieComposition(spec)
    val animatedProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playing && documentJson != null
    )

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t[1], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(fileName, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Default.FileOpen, contentDescription = "Import")
            }
            IconButton(
                onClick = { exportLauncher.launch(fileName.substringBeforeLast('.') + ".tgs") },
                enabled = document != null
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = "Export TGS", tint = Purple)
            }
        }

        Box(
            Modifier.fillMaxWidth().height(230.dp)
                .background(Panel, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (documentJson != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Animation, null, tint = Purple, modifier = Modifier.size(60.dp))
                    Text(t[7])
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t[14], color = Purple, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = onPlay, enabled = documentJson != null) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(5.dp))
                Text(if (playing) t[9] else t[8])
            }
        }
        Slider(value = animatedProgress, onValueChange = {}, enabled = false)

        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("لایه‌ها", fontWeight = FontWeight.Bold, color = Purple, modifier = Modifier.weight(1f))
                    Text("${nodes.size}", color = Color.LightGray)
                }
                if (nodes.isEmpty()) {
                    Text("فایلی با لایه قابل ویرایش وارد نشده است.", color = Color.LightGray)
                } else {
                    nodes.forEach { node ->
                        LayerTreeRow(node, 0, selectedPath) { selectedPath = it }
                    }
                }
            }
        }

        if (selected != null && document != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("ویرایش لایه", color = Purple, fontWeight = FontWeight.Bold)
                    Text(selected.name, fontWeight = FontWeight.SemiBold)
                    Text(selected.type, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            renameValue = selected.name
                            renameDialog = true
                        }) {
                            Icon(Icons.Default.DriveFileRenameOutline, null)
                            Text("تغییر نام")
                        }
                        OutlinedButton(onClick = {
                            applyEdit { it.setLayerHidden(selected.path, !selected.hidden) }
                        }) {
                            Icon(if (selected.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                            Text(if (selected.hidden) "نمایش" else "مخفی")
                        }
                    }
                    Text("رنگ لایه (برای Fillهای ثابت)", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0xFFFF4D8D.toInt(), 0xFF9B5CFF.toInt(), 0xFF22D3EE.toInt(), 0xFF4ADE80.toInt(), 0xFFFFC857.toInt()).forEach { color ->
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(50),
                                color = Color(color),
                                onClick = {
                                    applyEdit { changed ->
                                        val count = changed.setStaticFillColor(selected.path, color)
                                        Toast.makeText(context, if (count > 0) "رنگ تغییر کرد" else "Fill ثابت پیدا نشد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {}
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    applyEdit { it.addRectangle("New Rectangle", 0xFF9B5CFF.toInt()) }
                },
                enabled = document != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AddBox, null)
                Text("افزودن شکل")
            }
            OutlinedButton(
                onClick = {
                    documentJson?.let { onJsonChanged(it) }
                    Toast.makeText(context, "پروژه ذخیره شد", Toast.LENGTH_SHORT).show()
                },
                enabled = document != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, null)
                Text("ذخیره پروژه")
            }
        }
    }

    if (renameDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("تغییر نام لایه") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("نام جدید") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    applyEdit { it.renameLayer(selected.path, renameValue) }
                    renameDialog = false
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun LayerTreeRow(
    node: LottieLayerNode,
    depth: Int,
    selectedPath: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .background(
                if (selectedPath == node.path) Color(0xFF302347) else Color.Transparent,
                RoundedCornerShape(9.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (node.type) {
                "Precomposition" -> Icons.Default.Folder
                "Text" -> Icons.Default.TextFields
                "Shape" -> Icons.Default.Category
                else -> Icons.Default.Layers
            },
            null,
            tint = if (node.hidden) Color.Gray else Purple,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f)
                .clickable { onSelect(node.path) }
        ) {
            Text(node.name, maxLines = 1, fontWeight = if (selectedPath == node.path) FontWeight.Bold else FontWeight.Normal)
            Text(node.type, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        if (node.hidden) Icon(Icons.Default.VisibilityOff, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
    }
    node.children.forEach { child ->
        LayerTreeRow(child, depth + 1, selectedPath, onSelect)
    }
}

@Composable
fun SettingsPage(
    modifier: Modifier,
    t: List<String>,
    language: String,
    onShowLanguages: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            t[2],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(18.dp)
            ) {
                Text(
                    t[10],
                    color = Purple
                )

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = onShowLanguages,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selected = languages.firstOrNull {
                        it.code == language
                    } ?: languages.first()

                    Text(
                        "${selected.flag}   ${selected.title}",
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        Icons.Default.ArrowDropDown,
                        null
                    )
                }
            }
        }

        Text(
            "Emoji Studio Pro",
            color = Color.LightGray
        )

        Text(
            "Version 1.1",
            color = Purple
        )
    }
}
