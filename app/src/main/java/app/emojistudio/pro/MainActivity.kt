
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import org.json.JSONObject
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
    val templateStore = remember { TemplateStore(context) }
    var templates by remember { mutableStateOf(templateStore.all()) }

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
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(t[2]) }
                    )
                    NavigationBarItem(
                        selected = page == 3,
                        onClick = { templates = templateStore.all(); page = 3 },
                        icon = { Icon(Icons.Default.CollectionsBookmark, null) },
                        label = { Text("پروژه‌ها") }
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
                    },
                    onSaveTemplate = { name, sourceName, json ->
                        templateStore.save(name, sourceName, json)
                        templates = templateStore.all()
                    }
                )

                2 -> SettingsPage(
                    modifier = Modifier.padding(padding),
                    t = t,
                    language = language,
                    onShowLanguages = { showLanguages = true }
                )
                3 -> TemplatesPage(
                    modifier = Modifier.padding(padding),
                    templates = templates,
                    onOpen = { item ->
                        animationJson = item.json
                        fileName = item.sourceName
                        prefs.edit().putString("last_json", item.json).putString("last_name", item.sourceName).apply()
                        page = 1
                    },
                    onDelete = { item ->
                        templateStore.delete(item.id)
                        templates = templateStore.all()
                    },
                    onRename = { item, newName ->
                        templateStore.rename(item.id, newName)
                        templates = templateStore.all()
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
    onJsonChanged: (String) -> Unit,
    onSaveTemplate: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var documentJson by remember(json) { mutableStateOf(json) }
    val document = remember(documentJson) {
        documentJson?.let { runCatching { LottieDocument.parse(it) }.getOrNull() }
    }
    var refreshTree by remember { mutableIntStateOf(0) }
    val nodes = remember(documentJson, refreshTree) {
        document?.let { runCatching { it.layerTree() }.getOrDefault(emptyList()) } ?: emptyList()
    }
    var selectedPath by remember(fileName) { mutableStateOf(nodes.firstOrNull()?.path) }
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
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var templateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf(fileName.substringBeforeLast('.').ifBlank { "قالب من" }) }
    var textValue by remember(selectedPath, documentJson) {
        mutableStateOf(selectedPath?.let { document?.readTextLayer(it) }.orEmpty())
    }
    var xValue by remember(selectedPath, documentJson) { mutableStateOf(selectedPath?.let { document?.readLayerPosition(it)?.first?.toString() }.orEmpty()) }
    var yValue by remember(selectedPath, documentJson) { mutableStateOf(selectedPath?.let { document?.readLayerPosition(it)?.second?.toString() }.orEmpty()) }
    var rotationValue by remember(selectedPath, documentJson) { mutableStateOf(selectedPath?.let { document?.readLayerRotation(it)?.toString() } ?: "0") }
    var scaleValue by remember(selectedPath, documentJson) { mutableStateOf(selectedPath?.let { document?.readLayerScale(it)?.toString() } ?: "100") }
    var opacityValue by remember(selectedPath, documentJson) { mutableFloatStateOf((selectedPath?.let { document?.readLayerOpacity(it) } ?: 100).toFloat()) }
    var scrubProgress by remember(documentJson) { mutableFloatStateOf(0f) }
    val undoStack = remember(fileName) { mutableStateListOf<String>() }
    val redoStack = remember(fileName) { mutableStateListOf<String>() }

    var validationMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null && document != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(document.toTgsBytes()) }
                    ?: error("Cannot create output file")
                Toast.makeText(context, "فایل TGS خروجی گرفته شد", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "خطا در خروجی: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun requestExport() {
        val current = document ?: return
        val report = TelegramTgsValidator.validate(current.json())
        if (!report.isValid) {
            validationMessage = (listOf("خروجی با استاندارد TGS سازگار نیست:") + report.errors +
                (if (report.warnings.isNotEmpty()) listOf("", "هشدارها:") + report.warnings else emptyList())).joinToString("\n")
            return
        }
        val note = if (report.warnings.isEmpty()) {
            "اعتبارسنجی TGS موفق بود؛ حجم فشرده: ${report.compressedBytes} بایت."
        } else {
            "اعتبارسنجی ساختاری موفق بود، اما ${report.warnings.size} هشدار وجود دارد."
        }
        Toast.makeText(context, note, Toast.LENGTH_LONG).show()
        exportLauncher.launch(fileName.substringBeforeLast('.') + ".tgs")
    }

    fun applyEdit(edit: (LottieDocument) -> Unit) {
        val current = document ?: return
        val before = current.json()
        runCatching {
            edit(current)
            val updated = current.json()
            if (updated != before) {
                undoStack.add(before)
                if (undoStack.size > 50) undoStack.removeAt(0)
                redoStack.clear()
                documentJson = updated
                onJsonChanged(updated)
                refreshTree++
            }
        }.onFailure {
            Toast.makeText(context, it.message ?: "ویرایش انجام نشد", Toast.LENGTH_LONG).show()
        }
    }

    fun undoEdit() {
        if (undoStack.isEmpty()) return
        val current = documentJson ?: return
        redoStack.add(current)
        documentJson = undoStack.removeAt(undoStack.lastIndex)
        onJsonChanged(documentJson!!)
        refreshTree++
    }

    fun redoEdit() {
        if (redoStack.isEmpty()) return
        val current = documentJson ?: return
        undoStack.add(current)
        documentJson = redoStack.removeAt(redoStack.lastIndex)
        onJsonChanged(documentJson!!)
        refreshTree++
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(documentJson ?: EMPTY_JSON))
    val animatedProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playing && documentJson != null
    )

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ویرایشگر حرفه‌ای", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(fileName, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onImport) { Icon(Icons.Default.FileOpen, contentDescription = "Import") }
            IconButton(onClick = { undoEdit() }, enabled = undoStack.isNotEmpty()) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { redoEdit() }, enabled = redoStack.isNotEmpty()) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = { requestExport() }, enabled = document != null) {
                Icon(Icons.Default.Download, contentDescription = "Export TGS", tint = Purple)
            }
        }

        // Tools are deliberately placed before the preview, so the selected layer's actions are immediately visible.
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ابزارهای لایه انتخاب‌شده", color = Purple, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(selected?.type ?: "لایه‌ای انتخاب نشده", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                }
                Text(selected?.name ?: "از پیش‌نمایش یا فهرست لایه‌ها یک مورد انتخاب کن", fontWeight = FontWeight.SemiBold)
                if (selected != null && document != null) {
                    Text(document.layerDescription(selected.path), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { renameValue = selected.name; renameDialog = true }) {
                            Icon(Icons.Default.DriveFileRenameOutline, null); Text("تغییر نام")
                        }
                        OutlinedButton(onClick = { applyEdit { it.setLayerHidden(selected.path, !selected.hidden) } }) {
                            Icon(if (selected.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                            Text(if (selected.hidden) "نمایش" else "مخفی")
                        }
                    }
                    if (selected.type == "Text") {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("متن داخل لایه") },
                            placeholder = { Text("مثلاً ZYRO") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                applyEdit {
                                    if (!it.setTextLayer(selected.path, textValue)) {
                                        error("متن این لایه از نوع قابل ویرایش پشتیبانی نمی‌شود")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Default.TextFields, null); Text("اعمال متن جدید") }
                    }
                    Text("شفافیت لایه: ${opacityValue.toInt()}٪", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Slider(value = opacityValue, onValueChange = { opacityValue = it }, valueRange = 0f..100f)
                    OutlinedButton(
                        onClick = { applyEdit { it.setLayerOpacity(selected.path, opacityValue.toInt()) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Opacity, null); Text("اعمال شفافیت") }
                    Text("رنگ لایه", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        listOf(0xFFFF4D8D.toInt(), 0xFF9B5CFF.toInt(), 0xFF22D3EE.toInt(), 0xFF4ADE80.toInt(), 0xFFFFC857.toInt(), 0xFFFFFFFF.toInt()).forEach { color ->
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(50),
                                color = Color(color),
                                onClick = {
                                    applyEdit {
                                        val changed = it.setStaticFillColor(selected.path, color)
                                        if (changed == 0) Toast.makeText(context, "برای این لایه Fill ثابت پیدا نشد؛ ممکن است رنگ داخل Precomp یا متحرک باشد.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) {}
                        }
                    }
                    Text("موقعیت و اندازه (برای مقادیر ثابت)", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(xValue, { xValue = it }, modifier = Modifier.weight(1f), label = { Text("X") }, singleLine = true)
                        OutlinedTextField(yValue, { yValue = it }, modifier = Modifier.weight(1f), label = { Text("Y") }, singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(rotationValue, { rotationValue = it }, modifier = Modifier.weight(1f), label = { Text("چرخش °") }, singleLine = true)
                        OutlinedTextField(scaleValue, { scaleValue = it }, modifier = Modifier.weight(1f), label = { Text("اندازه %") }, singleLine = true)
                    }
                    OutlinedButton(
                        onClick = {
                            applyEdit {
                                it.setLayerTransform(
                                    selected.path,
                                    x = xValue.toDoubleOrNull(),
                                    y = yValue.toDoubleOrNull(),
                                    rotation = rotationValue.toDoubleOrNull(),
                                    scalePercent = scaleValue.toDoubleOrNull()
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Tune, null); Text("اعمال تغییرات موقعیت و اندازه") }
                } else {
                    Text("برای شروع یک فایل TGS یا JSON وارد کن.", color = Color.LightGray)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("پیش‌نمایش زنده", color = Purple, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (playing) scrubProgress = animatedProgress; onPlay() }, enabled = documentJson != null) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    }
                }
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(270.dp)
                        .background(Color(0xFF101019), RoundedCornerShape(14.dp))
                        .pointerInput(documentJson, nodes) {
                            detectTapGestures { tap ->
                                val nx = (tap.x / size.width).coerceIn(0f, 1f).toDouble()
                                val ny = (tap.y / size.height).coerceIn(0f, 1f).toDouble()
                                document?.nearestRootLayer(nx, ny)?.let { selectedPath = it }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (documentJson != null) {
                        LottieAnimation(
                            composition = composition,
                            progress = { if (playing) animatedProgress else scrubProgress },
                            modifier = Modifier.fillMaxSize().padding(12.dp)
                        )
                    } else {
                        Text("ابتدا یک فایل انیمیشن وارد کن", color = Color.LightGray)
                    }
                    Text(
                        "برای انتخاب سریع روی بخش ایموجی بزن",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
                        color = Color.LightGray.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Slider(
                    value = if (playing) animatedProgress else scrubProgress,
                    onValueChange = { scrubProgress = it },
                    enabled = !playing && documentJson != null
                )
                Text(if (playing) "برای جابه‌جایی در تایم‌لاین، ابتدا پخش را متوقف کن." else "جابه‌جایی در تایم‌لاین فعال است.", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("درخت لایه‌ها و پوشه‌ها", color = Purple, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${nodes.size} لایه اصلی", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    IconButton(
                        onClick = { applyEdit { it.addRectangle("شکل جدید", 0xFF9B5CFF.toInt()) } },
                        enabled = document != null
                    ) { Icon(Icons.Default.AddBox, contentDescription = "افزودن شکل") }
                }
                if (nodes.isEmpty()) Text("لایه‌ای برای نمایش وجود ندارد.", color = Color.LightGray)
                nodes.forEach { node -> LayerTreeRow(node, 0, selectedPath) { selectedPath = it } }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { templateName = fileName.substringBeforeLast('.').ifBlank { "قالب من" }; templateDialog = true },
                enabled = document != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.BookmarkAdd, null); Text("ذخیره پروژه")
            }
            OutlinedButton(onClick = { requestExport() },
                enabled = document != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FileDownload, null); Text("خروجی TGS")
            }
        }
    }

    if (renameDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("تغییر نام لایه") },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true, label = { Text("نام جدید") }) },
            confirmButton = {
                TextButton(onClick = { applyEdit { it.renameLayer(selected.path, renameValue) }; renameDialog = false }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("انصراف") } }
        )
    }
    if (validationMessage != null) {
        AlertDialog(
            onDismissRequest = { validationMessage = null },
            title = { Text("گزارش اعتبارسنجی TGS") },
            text = { Text(validationMessage!!, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { validationMessage = null }) { Text("متوجه شدم") }
            }
        )
    }
    if (templateDialog && documentJson != null) {
        AlertDialog(
            onDismissRequest = { templateDialog = false },
            title = { Text("ذخیره پروژه جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پروژه در بخش «پروژه‌ها» نگهداری می‌شود و بعداً می‌توانی دوباره بازش کنی.")
                    OutlinedTextField(templateName, { templateName = it }, singleLine = true, label = { Text("نام پروژه") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { onSaveTemplate(templateName, fileName, documentJson!!) }
                        .onSuccess { Toast.makeText(context, "قالب در بخش قالب‌ها ذخیره شد", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "ذخیره نشد", Toast.LENGTH_LONG).show() }
                    templateDialog = false
                }) { Text("ذخیره پروژه") }
            },
            dismissButton = { TextButton(onClick = { templateDialog = false }) { Text("انصراف") } }
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
private fun TemplatesPage(
    modifier: Modifier,
    templates: List<SavedTemplate>,
    onOpen: (SavedTemplate) -> Unit,
    onDelete: (SavedTemplate) -> Unit,
    onRename: (SavedTemplate, String) -> Unit
) {
    var renaming by remember { mutableStateOf<SavedTemplate?>(null) }
    var renameValue by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("پروژه‌های من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("پروژه‌ها با فایل مستقل در حافظه داخلی برنامه نگهداری می‌شوند؛ می‌توانی آن‌ها را باز کنی، تغییر نام بدهی یا حذف کنی.", color = Color.LightGray)
        if (templates.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CollectionsBookmark, null, tint = Purple, modifier = Modifier.size(52.dp))
                    Text("هنوز قالبی ذخیره نکرده‌ای", fontWeight = FontWeight.Bold)
                    Text("به ویرایشگر برو و «ذخیره در قالب‌های من» را بزن.", color = Color.LightGray)
                }
            }
        } else {
            templates.forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Animation, null, tint = Purple)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(item.sourceName, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpen(item) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.FolderOpen, null); Text("باز کردن")
                            }
                            OutlinedButton(onClick = { renameValue = item.name; renaming = item }) {
                                Icon(Icons.Default.Edit, null); Text("نام")
                            }
                            OutlinedButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Default.DeleteOutline, null); Text("حذف")
                            }
                        }
                    }
                }
            }
        }
    }
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("تغییر نام پروژه") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("نام جدید پروژه") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming?.let { onRename(it, renameValue) }
                    renaming = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("انصراف") }
            }
        )
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
