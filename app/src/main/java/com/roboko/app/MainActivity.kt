package com.roboko.app

import android.Manifest
import android.os.Bundle
import android.app.DownloadManager
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.AndroidViewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max

private val Teal = androidx.compose.ui.graphics.Color(0xFF176B87)
private val Orange = androidx.compose.ui.graphics.Color(0xFFE77B45)
private const val REF_MARK = "\u0002REF\u0002"
/** AI 系统提示词：限制 Markdown 与篇幅，并要求在结尾推荐可加入单词本的新词。 */
private const val AI_SYSTEM_PROMPT = """你是 Roboko 的英语学习助手，面向中文母语的英语学习者。

硬性要求：
1. 只用纯文本回答，禁止使用 Markdown 标记：不要 #、*、**、`、>、表格、代码块、分割线。
2. 尽量简短：一般不超过 120 字，最多 200 字。能一句话说清就不展开。
3. 需要列举时用「1. 2. 3.」编号，每条独占一行，不要嵌套层级。
4. 如果用户在比较两个词、或对话里出现了当前学习条目之外的新单词/短语值得单独记忆，
   在回答的最后单独加一行，格式严格如下（全角冒号，词之间用英文逗号加空格）：
   推荐加入单词本：word1, word2
   只列真正值得作为新考点收录的词，通常 0-3 个；没有就整行省略，不要输出这一行。
5. 不要反问用户是否要加入单词本，界面会自动给出按钮。"""
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

data class ContextEntry(val id: String, val english: String, val chinese: String, val createdAt: Instant, val representative: Boolean)
data class MeaningBranch(val id: String, val index: Int, val label: String, val partOfSpeech: String, val englishDefinition: String, val chineseDefinition: String, val progress: Double, val contexts: List<ContextEntry>)
data class LearningItem(val id: String, val headword: String, val displayForm: String, val phoneticUs: String?, val phoneticUk: String?, val createdAt: Instant, val deletedAt: Instant?, val meanings: List<MeaningBranch>, val categories: List<String>)
data class ReviewCard(val itemId: String, val meaningId: String, val context: ContextEntry)
data class TodayStats(val newCount: Int, val reviewCount: Int, val total: Int, val progressDelta: Double, val mastered: Int)
data class ReviewResult(val known: Boolean, val hint: Boolean)

enum class Tab { HOME, LIBRARY, REVIEW, AI, SETTINGS }
enum class ApiFormat { OPENAI, CLAUDE }
enum class ReviewStage { RECALL, ANSWER_REVEALED, COMPLETE }

@Entity(tableName = "learning_items")
data class LearningItemEntity(@androidx.room.PrimaryKey val id: String, val payload: String)

@Dao
interface LearningItemDao {
    @Query("SELECT * FROM learning_items") fun all(): List<LearningItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsert(item: LearningItemEntity)
    @Query("DELETE FROM learning_items WHERE id = :id") fun delete(id: String)
    @Query("DELETE FROM learning_items") fun clear()
}

@Entity(tableName = "ai_messages")
data class AiMessageEntity(@androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0, val content: String, val createdAt: Long)

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages ORDER BY createdAt ASC") fun all(): List<AiMessageEntity>
    @Insert fun insert(message: AiMessageEntity)
    @Query("DELETE FROM ai_messages") fun clear()
}

@Database(entities = [LearningItemEntity::class, AiMessageEntity::class], version = 2, exportSchema = false)
abstract class RobokoDatabase : RoomDatabase() { abstract fun learningItems(): LearningItemDao; abstract fun aiMessages(): AiMessageDao }

private object ItemCodec {
    fun encode(item: LearningItem): String {
        val meanings = org.json.JSONArray().apply { item.meanings.forEach { meaning -> put(JSONObject().put("id", meaning.id).put("order", meaning.index).put("label", meaning.label).put("partOfSpeech", meaning.partOfSpeech).put("englishDefinition", meaning.englishDefinition).put("chineseDefinition", meaning.chineseDefinition).put("progress", meaning.progress).put("contexts", org.json.JSONArray().apply { meaning.contexts.forEach { put(JSONObject().put("id", it.id).put("english", it.english).put("chinese", it.chinese).put("createdAt", it.createdAt.toString()).put("representative", it.representative)) } })) } }
        return JSONObject().put("id", item.id).put("headword", item.headword).put("displayForm", item.displayForm).put("phoneticUs", item.phoneticUs).put("phoneticUk", item.phoneticUk).put("createdAt", item.createdAt.toString()).put("deletedAt", item.deletedAt?.toString()).put("meanings", meanings).put("categories", org.json.JSONArray(item.categories)).toString()
    }
    fun decode(payload: String): LearningItem? = try { val o = JSONObject(payload); val meaningsJson = o.getJSONArray("meanings"); val meanings = (0 until meaningsJson.length()).map { m -> val x = meaningsJson.getJSONObject(m); val contexts = x.getJSONArray("contexts"); MeaningBranch(x.getString("id"), x.getInt("order"), x.getString("label"), x.getString("partOfSpeech"), x.getString("englishDefinition"), x.getString("chineseDefinition"), x.getDouble("progress"), (0 until contexts.length()).map { c -> val y = contexts.getJSONObject(c); ContextEntry(y.getString("id"), y.getString("english"), y.getString("chinese"), Instant.parse(y.getString("createdAt")), y.getBoolean("representative")) }) }; val cats = o.getJSONArray("categories"); LearningItem(o.getString("id"), o.getString("headword"), o.optString("displayForm", o.getString("headword")), o.optString("phoneticUs", null), o.optString("phoneticUk", null), Instant.parse(o.getString("createdAt")), o.optString("deletedAt", null)?.let { Instant.parse(it) }, meanings, (0 until cats.length()).map { cats.getString(it) }) } catch (_: Exception) { null }
    fun decodeGenerated(payload: String, fallbackWord: String): LearningItem {
        val cleaned = payload.substringAfter("{", payload).substringBeforeLast("}", payload).let { "{$it}" }
        val o = JSONObject(cleaned)
        val word = o.optString("headword", fallbackWord).ifBlank { fallbackWord }
        val meaningsJson = o.optJSONArray("meanings") ?: throw IllegalArgumentException("AI 未返回词义")
        val meanings = (0 until meaningsJson.length()).map { index ->
            val m = meaningsJson.getJSONObject(index); val contextsJson = m.optJSONArray("contexts") ?: org.json.JSONArray()
            MeaningBranch(UUID.randomUUID().toString(), index + 1, m.optString("label", "主要含义"), m.optString("partOfSpeech", "phrase"), m.optString("englishDefinition", ""), m.optString("chineseDefinition", ""), 0.0, (0 until contextsJson.length()).map { c -> val x = contextsJson.getJSONObject(c); ContextEntry(UUID.randomUUID().toString(), x.optString("english", ""), x.optString("chinese", ""), Instant.now(), x.optBoolean("representative", c == 0)) })
        }
        if (meanings.isEmpty()) throw IllegalArgumentException("AI 未返回有效词义")
        val categories = o.optJSONArray("categories") ?: org.json.JSONArray().put("待整理")
        return LearningItem(UUID.randomUUID().toString(), word, o.optString("displayForm", word), o.optString("phoneticUs", null), o.optString("phoneticUk", null), Instant.now(), null, meanings, (0 until categories.length()).map { categories.getString(it) })
    }
}

private val ROOM_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS ai_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL)") }
}

private class RoomItemStore(context: android.content.Context) {
    private val database = Room.databaseBuilder(context, RobokoDatabase::class.java, "roboko.db").addMigrations(ROOM_MIGRATION_1_2).fallbackToDestructiveMigration().allowMainThreadQueries().build()
    private val dao = database.learningItems()
    private val messages = database.aiMessages()
    fun load(): List<LearningItem> = dao.all().mapNotNull { ItemCodec.decode(it.payload) }
    fun save(item: LearningItem) = dao.upsert(LearningItemEntity(item.id, ItemCodec.encode(item)))
    fun delete(id: String) = dao.delete(id)
    fun loadMessages(): List<String> = messages.all().map { it.content }
    fun saveMessage(content: String) = messages.insert(AiMessageEntity(content = content, createdAt = System.currentTimeMillis()))
    /** 编辑消息后重写整个会话记录，保持与界面一致。 */
    fun replaceMessages(list: List<String>) { messages.clear(); list.forEach { saveMessage(it) } }
}

data class ProviderConfig(val id: String, val name: String, val baseUrl: String, val model: String, val apiKey: String, val enabled: Boolean, val format: ApiFormat = ApiFormat.OPENAI, val responseApi: Boolean = false)

private val providerPresets = listOf(
    ProviderConfig("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat", "", true),
    ProviderConfig("modelscope", "ModelScope", "https://api-inference.modelscope.cn/v1", "", "", false),
    ProviderConfig("volc-agent", "火山 Agent Plan", "https://ark.cn-beijing.volces.com/api/v3", "", "", false),
    ProviderConfig("volc-coding", "火山 Coding Plan", "https://ark.cn-beijing.volces.com/api/v3", "", "", false)
)

private class LocalProviderStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("roboko_provider", android.content.Context.MODE_PRIVATE)
    private val alias = "roboko_api_key"
    private fun key(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
        return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }
    private fun encrypt(value: String): String { if (value.isBlank()) return ""; val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) }
    private fun decrypt(value: String): String { if (value.isBlank()) return ""; return try { val bytes = Base64.decode(value, Base64.NO_WRAP); Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8) } catch (_: Exception) { "" } }
    fun load(): ProviderConfig { val preset = providerPresets.firstOrNull { it.id == prefs.getString("id", "deepseek") } ?: providerPresets.first(); return preset.copy(name = prefs.getString("name", preset.name) ?: preset.name, baseUrl = prefs.getString("baseUrl", preset.baseUrl) ?: preset.baseUrl, model = prefs.getString("model", preset.model) ?: preset.model, apiKey = decrypt(prefs.getString("apiKey", "") ?: ""), enabled = true, format = runCatching { ApiFormat.valueOf(prefs.getString("format", preset.format.name) ?: preset.format.name) }.getOrDefault(preset.format), responseApi = prefs.getBoolean("responseApi", preset.responseApi)) }
    fun save(config: ProviderConfig) { prefs.edit().putString("id", config.id).putString("name", config.name).putString("baseUrl", config.baseUrl).putString("model", config.model).putString("format", config.format.name).putBoolean("responseApi", config.responseApi).putString("apiKey", encrypt(config.apiKey)).apply() }
    fun loadStt(): SttEngine = runCatching { SttEngine.valueOf(prefs.getString("sttEngine", SttEngine.SYSTEM.name) ?: SttEngine.SYSTEM.name) }.getOrDefault(SttEngine.SYSTEM)
    fun saveStt(engine: SttEngine) { prefs.edit().putString("sttEngine", engine.name).apply() }
    // 各预设的 API Key 分开保存，避免切换预设时串用
    private fun sttKeySlot(id: String) = "sttApiKey_" + (if (id.isBlank()) "custom" else id)

    fun loadSttCloudKeys(): Map<String, String> =
        (sttPresets.map { it.presetId } + "custom").associateWith { decrypt(prefs.getString(sttKeySlot(it), "") ?: "") }

    fun loadSttCloud(): SttCloudConfig {
        val fallback = sttPresets.first()
        val presetId = prefs.getString("sttPreset", fallback.presetId) ?: fallback.presetId
        return SttCloudConfig(
            presetId = presetId,
            baseUrl = prefs.getString("sttBaseUrl", fallback.baseUrl) ?: fallback.baseUrl,
            apiKey = decrypt(prefs.getString(sttKeySlot(presetId), "") ?: ""),
            model = prefs.getString("sttModel", fallback.model) ?: fallback.model,
            keyUrl = prefs.getString("sttKeyUrl", fallback.keyUrl) ?: fallback.keyUrl,
            format = runCatching { SttApiFormat.valueOf(prefs.getString("sttFormat", fallback.format.name) ?: fallback.format.name) }.getOrDefault(fallback.format)
        )
    }
    fun saveSttCloud(config: SttCloudConfig) {
        prefs.edit()
            .putString("sttPreset", config.presetId)
            .putString("sttBaseUrl", config.baseUrl)
            .putString("sttModel", config.model)
            .putString("sttKeyUrl", config.keyUrl)
            .putString("sttFormat", config.format.name)
            .putString(sttKeySlot(config.presetId), encrypt(config.apiKey))
            .apply()
    }
}

private object AiClient {
    fun ask(config: ProviderConfig, history: List<String>, question: String, reference: String? = null): String {
        require(config.apiKey.isNotBlank()) { "请先在设置中填写 API Key" }
        require(config.baseUrl.isNotBlank()) { "请先配置 API Base URL" }
        require(config.model.isNotBlank()) { "请先填写模型名称" }
        val connection = (URL(config.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}"); setRequestProperty("Content-Type", "application/json")
        }
        val messages = org.json.JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", AI_SYSTEM_PROMPT))
            history.takeLast(12).forEach { line -> put(JSONObject().put("role", if (line.startsWith("你：")) "user" else "assistant").put("content", line.substringBefore(REF_MARK).removePrefix("你：").removePrefix("AI："))) }
            put(JSONObject().put("role", "user").put("content", if (reference.isNullOrBlank()) question else "参考学习条目：\n$reference\n\n用户问题：$question"))
        }
        connection.outputStream.use { it.write(JSONObject().put("model", config.model).put("messages", messages).put("temperature", 0.3).toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException("Provider 请求失败（HTTP $status）")
        return JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}

data class ReviewUiState(val card: ReviewCard?, val item: LearningItem?, val stage: ReviewStage = ReviewStage.RECALL, val hintUsed: Boolean = false, val answered: Int = 0, val results: List<ReviewResult> = emptyList())

enum class LibrarySort { ADDED, ALPHABETICAL, MASTERY }

data class AppUiState(val tab: Tab = Tab.AI, val items: List<LearningItem> = emptyList(), val selectedItemId: String? = null, val query: String = "", val librarySort: LibrarySort = LibrarySort.ADDED, val sortDescending: Boolean = true, val stats: TodayStats = TodayStats(0, 0, 0, 0.0, 0), val review: ReviewUiState = ReviewUiState(null, null), val aiMessages: List<String> = listOf("AI：你好，我可以帮你理解语境、整理词义，或发现相关学习对象。"), val aiLoading: Boolean = false, val showSettings: Boolean = false, val provider: ProviderConfig = providerPresets.first(), val generatedItem: LearningItem? = null, val generatingItem: Boolean = false, val itemError: String? = null, val aiReference: LearningItem? = null, val balanceStatus: String? = null, val updateVersion: String? = null, val updateNotes: String = "", val updateUrl: String? = null, val updateChecking: Boolean = false, val mirror: String = "GitHub 官方", val sttEngine: SttEngine = SttEngine.SYSTEM, val sttCloud: SttCloudConfig = SttCloudConfig(), val sttKeys: Map<String, String> = emptyMap(), val sttTesting: Boolean = false, val sttStatus: String? = null, val aiTextRef: String? = null)

object ReviewEngine {
    fun update(progress: Double, known: Boolean, hint: Boolean): Double {
        val delta = when { known && !hint -> 0.8 + (6.0 - progress) * 0.18; known -> 0.35; !hint -> -1.0 - progress * 0.08; else -> -0.45 }
        return (progress + delta).coerceIn(0.0, 6.0)
    }
}

/** 学习条目的仓储层（Room 持久化）。 */
class LearningRepository(context: android.content.Context) {
    private val room = RoomItemStore(context)
    private val _items = MutableStateFlow(room.load())
    val items: StateFlow<List<LearningItem>> = _items.asStateFlow()
    var stats = TodayStats(0, 0, 0, 0.0, 0)

    fun save(item: LearningItem) {
        _items.value = listOf(item) + _items.value.filterNot { it.id == item.id }
        room.save(item)
        stats = stats.copy(newCount = stats.newCount + 1, total = stats.total + 1)
    }
    fun delete(id: String) { _items.value = _items.value.filterNot { it.id == id }; room.delete(id) }
    fun updateAnswer(card: ReviewCard, known: Boolean, hint: Boolean) {
        _items.value = _items.value.map { item ->
            if (item.id != card.itemId) item else item.copy(
                meanings = item.meanings.map { meaning -> if (meaning.id != card.meaningId) meaning else meaning.copy(progress = ReviewEngine.update(meaning.progress, known, hint)) }
            )
        }
        _items.value.firstOrNull { it.id == card.itemId }?.let { room.save(it) }
        val delta = if (known) 0.35 else -0.25
        stats = stats.copy(reviewCount = stats.reviewCount + 1, total = stats.newCount + stats.reviewCount + 0, progressDelta = stats.progressDelta + delta, mastered = _items.value.count { it.meanings.all { m -> m.progress >= 5.0 } })
    }
    fun nextCard(exclude: ReviewCard?): ReviewCard? {
        val candidates = _items.value.filter { it.deletedAt == null }.flatMap { item -> item.meanings.flatMap { meaning -> meaning.contexts.map { ReviewCard(item.id, meaning.id, it) } } }.filter { it.context.id != exclude?.context?.id }
        return candidates.randomOrNull() ?: _items.value.firstOrNull()?.meanings?.firstOrNull()?.contexts?.firstOrNull()?.let { ReviewCard(_items.value.first().id, _items.value.first().meanings.first().id, it) }
    }
}

class RobokoViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = LearningRepository(app)
    private val roomStore = RoomItemStore(app)
    private val providerStore = LocalProviderStore(app)
    private val _state = MutableStateFlow(AppUiState(items = repo.items.value, stats = repo.stats, aiMessages = roomStore.loadMessages().ifEmpty { listOf("AI：你好，我可以帮你理解语境、整理词义，或发现相关学习对象。") }, provider = providerStore.load(), sttEngine = providerStore.loadStt(), sttCloud = providerStore.loadSttCloud(), sttKeys = providerStore.loadSttCloudKeys()))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val cloudVoice = CloudVoiceInput(app).also { it.config = providerStore.loadSttCloud() }
    val systemVoice = SystemVoiceInput(app)
    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { repo.items.collect { _state.value = _state.value.copy(items = it) } }
    }
    override fun onCleared() { super.onCleared(); cloudVoice.shutdown(); systemVoice.shutdown() }
    fun setSttEngine(engine: SttEngine) { providerStore.saveStt(engine); _state.value = _state.value.copy(sttEngine = engine) }

    /** 保存云端识别配置（API Key 按预设分开保存），并给出成功/失败反馈。 */
    fun saveSttCloud(config: SttCloudConfig) {
        val ok = runCatching {
            providerStore.saveSttCloud(config)
            cloudVoice.config = config
        }.isSuccess
        _state.value = _state.value.copy(
            sttCloud = config,
            sttKeys = providerStore.loadSttCloudKeys(),
            sttStatus = if (ok) "✓ 已保存，当前引擎：${sttPresetLabel(config.presetId)}" else "✗ 保存失败，请重试"
        )
        if (ok && _state.value.sttEngine == SttEngine.SYSTEM) setSttEngine(SttEngine.CLOUD)
    }

    /** 用静音音频实测配置。无论成功、失败还是被取消，都保证复位 testing 状态，避免界面卡在「测试中」。 */
    fun testSttCloud(config: SttCloudConfig) {
        if (_state.value.sttTesting) return
        _state.value = _state.value.copy(sttTesting = true, sttStatus = "正在测试…")
        viewModelScope.launch {
            var result = "✗ 测试未完成，请重试"
            try {
                result = cloudVoice.test(config)
            } catch (e: Exception) {
                result = "✗ 测试失败：${e.message ?: "未知错误"}"
            } finally {
                _state.value = _state.value.copy(sttTesting = false, sttStatus = result)
            }
        }
    }

    /** 当前实际生效的引擎：系统识别不可用时回退到云端识别。 */
    fun activeVoice(): SttController =
        if (_state.value.sttEngine == SttEngine.SYSTEM && systemVoice.isReady) systemVoice else cloudVoice
    fun tab(tab: Tab) { _state.value = _state.value.copy(tab = tab, selectedItemId = null) }
    fun search(q: String) { _state.value = _state.value.copy(query = q) }
    fun setSort(sort: LibrarySort) { _state.value = _state.value.copy(librarySort = sort) }
    fun toggleSortDirection() { _state.value = _state.value.copy(sortDescending = !_state.value.sortDescending) }
    fun saveProvider(config: ProviderConfig) { providerStore.save(config); _state.value = _state.value.copy(provider = config, showSettings = false, tab = Tab.AI, balanceStatus = null) }
    fun deleteItem(id: String) {
        repo.delete(id)
        val current = _state.value
        _state.value = current.copy(
            selectedItemId = null,
            tab = Tab.LIBRARY,
            // 若删掉的正是当前引用的词条，一并清掉引用，避免留下悬空引用
            aiReference = current.aiReference?.takeIf { it.id != id }
        )
    }
    fun checkBalance(config: ProviderConfig) {
        _state.value = _state.value.copy(balanceStatus = "查询中...")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = (URL(config.baseUrl.trimEnd('/') + "/user/balance").openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 10000; readTimeout = 15000; setRequestProperty("Authorization", "Bearer ${config.apiKey}") }
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                _state.value = _state.value.copy(balanceStatus = if (connection.responseCode in 200..299) "余额查询成功：$body" else "余额查询失败（HTTP ${connection.responseCode}）")
            } catch (error: Exception) { _state.value = _state.value.copy(balanceStatus = "余额查询失败：${error.message ?: "未知错误"}") }
        }
    }
    fun checkUpdate() {
        _state.value = _state.value.copy(updateChecking = true)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = (URL("https://api.github.com/repos/VellowK/Roboko/releases/latest").openConnection() as HttpURLConnection).apply { connectTimeout = 10000; readTimeout = 15000; setRequestProperty("Accept", "application/vnd.github+json") }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val asset = json.optJSONArray("assets")?.let { assets -> (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
                _state.value = _state.value.copy(updateChecking = false, updateVersion = json.optString("tag_name").ifBlank { null }, updateNotes = json.optString("body").take(600), updateUrl = asset?.optString("browser_download_url"))
            } catch (_: Exception) { _state.value = _state.value.copy(updateChecking = false, updateVersion = null, updateUrl = null) }
        }
    }
    fun setMirror(value: String) { _state.value = _state.value.copy(mirror = value) }
    fun startUpdate(url: String) { val request = DownloadManager.Request(Uri.parse(url)).setTitle("Roboko 更新").setDescription("正在下载更新包").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setAllowedOverMetered(true); (getApplication<android.app.Application>().getSystemService(DownloadManager::class.java)).enqueue(request) }
    fun select(id: String) { _state.value = _state.value.copy(selectedItemId = id) }
    fun openAiFor(item: LearningItem) { _state.value = _state.value.copy(aiReference = item, tab = Tab.AI, selectedItemId = null) }
    fun clearReference() { _state.value = _state.value.copy(aiReference = null) }

    /** 引用一段对话文本作为提问上下文（与引用单词同一套机制，不塞进输入框）。 */
    fun quoteText(value: String) { if (value.isNotBlank()) _state.value = _state.value.copy(aiTextRef = value) }
    fun clearTextRef() { _state.value = _state.value.copy(aiTextRef = null) }

    /** 编辑某条用户消息：截断该条及其之后的对话，用新内容重新提问，让 AI 重新生成回答。 */
    fun editUserMessage(index: Int, newText: String) {
        val text = newText.trim()
        if (text.isBlank() || _state.value.aiLoading) return
        val history = _state.value.aiMessages.take(index)
        roomStore.replaceMessages(history)
        _state.value = _state.value.copy(aiMessages = history, aiLoading = false)
        ask(text)
    }
    fun selectAdjacent(delta: Int) { val current = _state.value; val filtered = current.items.filter { it.headword.contains(current.query, true) || it.meanings.any { m -> m.label.contains(current.query, true) || m.chineseDefinition.contains(current.query, true) || m.contexts.any { c -> c.english.contains(current.query, true) || c.chinese.contains(current.query, true) } } }; val ordered = when (current.librarySort) { LibrarySort.ADDED -> filtered.sortedBy { it.createdAt }; LibrarySort.ALPHABETICAL -> filtered.sortedBy { it.headword.lowercase() }; LibrarySort.MASTERY -> filtered.sortedBy { it.meanings.map { m -> m.progress }.average() } }.let { if (current.sortDescending) it.reversed() else it }; val index = ordered.indexOfFirst { it.id == current.selectedItemId }; ordered.getOrNull(index + delta)?.let { select(it.id) } }
    fun generateItem(raw: String) {
        val word = raw.trim()
        if (word.isBlank() || _state.value.generatingItem) return
        _state.value = _state.value.copy(generatingItem = true, itemError = null)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prompt = "请为英语学习者整理词条：$word。只返回 JSON，不要 Markdown 代码块。格式：{\"headword\":\"$word\",\"displayForm\":\"$word\",\"phoneticUs\":\"\",\"phoneticUk\":\"\",\"meanings\":[{\"label\":\"中文短释义\",\"partOfSpeech\":\"词性\",\"englishDefinition\":\"英英释义\",\"chineseDefinition\":\"中文释义\",\"contexts\":[{\"english\":\"英文例句\",\"chinese\":\"中文翻译\",\"representative\":true}]}],\"categories\":[\"分类\"]}。最多 3 个常用词义。"
                val response = AiClient.ask(_state.value.provider, emptyList(), prompt)
                val item = ItemCodec.decodeGenerated(response, word)
                _state.value = _state.value.copy(generatedItem = item, generatingItem = false)
            } catch (error: Exception) { _state.value = _state.value.copy(generatingItem = false, itemError = error.message ?: "生成失败") }
        }
    }
    fun confirmGenerated(item: LearningItem) { repo.save(item); _state.value = _state.value.copy(items = repo.items.value, stats = repo.stats, generatedItem = null, itemError = null, tab = Tab.LIBRARY) }
    fun cancelGenerated() { _state.value = _state.value.copy(generatedItem = null, itemError = null) }
    fun startReview() { val card = repo.nextCard(null); _state.value = _state.value.copy(tab = Tab.REVIEW, review = ReviewUiState(card, _state.value.items.firstOrNull { it.id == card?.itemId })) }
    fun hint() { _state.value = _state.value.copy(review = _state.value.review.copy(hintUsed = true)) }
    fun answer(known: Boolean) { val r = _state.value.review; val card = r.card ?: return; repo.updateAnswer(card, known, r.hintUsed); _state.value = _state.value.copy(stats = repo.stats, review = r.copy(stage = ReviewStage.ANSWER_REVEALED, item = _state.value.items.firstOrNull { it.id == card.itemId }, answered = r.answered + 1, results = r.results + ReviewResult(known, r.hintUsed))) }
    fun next() { val old = _state.value.review; val card = repo.nextCard(old.card); if (card == null || old.answered >= 5) _state.value = _state.value.copy(review = old.copy(stage = ReviewStage.COMPLETE, card = null)) else _state.value = _state.value.copy(review = ReviewUiState(card, _state.value.items.firstOrNull { it.id == card.itemId }, answered = old.answered)) }
    fun ask(text: String) {
        if (text.isBlank() || _state.value.aiLoading) return
        val ref = _state.value.aiReference
        val quoted = _state.value.aiTextRef
        val reference = buildString {
            if (!quoted.isNullOrBlank()) append("用户引用的对话内容：").append(quoted).append('\n')
            if (ref != null) append(ref.displayForm).append("：").append(ref.meanings.joinToString("；") { it.label + " - " + it.chineseDefinition })
        }.trim().ifBlank { null }
        val refTag = ref?.let { item -> REF_MARK + item.displayForm + "\u0001" + (item.meanings.firstOrNull()?.let { it.label + " · " + it.chineseDefinition } ?: "") } ?: ""
        val userMessage = "你：$text$refTag"
        val history = _state.value.aiMessages + userMessage
        _state.value = _state.value.copy(aiMessages = history, aiLoading = true, aiReference = null, aiTextRef = null)
        roomStore.saveMessage(userMessage)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val response = try { "AI：${AiClient.ask(_state.value.provider, history.dropLast(1), text, reference)}" } catch (error: Exception) { "AI：请求失败：${error.message ?: "未知错误"}" }
            val messages = _state.value.aiMessages + response
            roomStore.saveMessage(response)
            _state.value = _state.value.copy(aiMessages = messages, aiLoading = false)
        }
    }
}

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { RobokoApp() } } }

@Composable fun RobokoApp(vm: RobokoViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.selectedItemId != null || state.tab != Tab.AI) { if (state.selectedItemId != null) vm.tab(Tab.LIBRARY) else vm.tab(Tab.AI) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Orange, background = androidx.compose.ui.graphics.Color(0xFFF7FAFC))) {
        Scaffold(bottomBar = { Navigation(vm, state.tab) }) { pad -> Surface(Modifier.fillMaxSize().padding(pad)) { when { state.selectedItemId != null -> DetailScreen(state.items.first { it.id == state.selectedItemId }, vm); state.tab == Tab.HOME -> HomeScreen(state, vm); state.tab == Tab.LIBRARY -> LibraryScreen(state, vm); state.tab == Tab.REVIEW -> ReviewScreen(state, vm); state.tab == Tab.SETTINGS -> SettingsScreen(vm); else -> AiScreen(state, vm) } } }
    }
}

@Composable fun Navigation(vm: RobokoViewModel, selected: Tab) { Row(Modifier.fillMaxWidth().navigationBarsPadding().background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.SpaceEvenly) { listOf(Tab.AI to ("AI" to Icons.Default.AutoAwesome), Tab.HOME to ("首页" to Icons.Default.Home), Tab.LIBRARY to ("单词本" to Icons.Default.Book), Tab.SETTINGS to ("设置" to Icons.Default.Settings)).forEach { (tab, label) -> Column(Modifier.clickable { vm.tab(tab) }.padding(10.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Icon(label.second, label.first, tint = if (selected == tab) Teal else MaterialTheme.colorScheme.onSurfaceVariant); Text(label.first, style = MaterialTheme.typography.labelSmall, color = if (selected == tab) Teal else MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(s: AppUiState, vm: RobokoViewModel) { var add by remember { mutableStateOf(false) }; Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { TopAppBar(title = { Text("Roboko") }); Text("今天，继续在真实语境里记住它们。", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(16.dp)); Card(colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = .10f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("今日概览", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Stat("新词", s.stats.newCount); Stat("复习", s.stats.reviewCount); Stat("总计", s.stats.total) } } }; Spacer(Modifier.height(18.dp)); Button({ vm.startReview() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.School, null); Spacer(Modifier.width(8.dp)); Text("开始背诵") }; Spacer(Modifier.height(24.dp)); Text("最近加入", style = MaterialTheme.typography.titleMedium); s.items.take(3).forEach { item -> CompactItem(item, { vm.select(item.id) }, { vm.deleteItem(item.id) }) }; Spacer(Modifier.height(12.dp)); Text("每日目标只是参考，不会限制你的实际背诵队列。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (add) AddDialog({ add = false }, vm) }; FloatingActionButton(add = { add = true }) }

@Composable fun FloatingActionButton(add: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomEnd) { IconButton({ add() }, Modifier.padding(18.dp).size(54.dp).background(Orange, RoundedCornerShape(16.dp))) { Icon(Icons.Default.Add, "添加学习条目", tint = androidx.compose.ui.graphics.Color.White) } } }
@Composable fun Stat(label: String, value: Int) { Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = Teal); Text(label, style = MaterialTheme.typography.bodySmall) } }
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun CompactItem(item: LearningItem, onClick: () -> Unit, onDelete: () -> Unit = {}) { var menu by remember { mutableStateOf(false) }; Box(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.displayForm, style = MaterialTheme.typography.titleMedium); Text(item.meanings.firstOrNull()?.label ?: "待整理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${item.meanings.map { it.progress }.average().format1()} / 6", color = Teal) }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("删除", color = androidx.compose.ui.graphics.Color.Red) }, { menu = false; onDelete() }) } } }

private fun sortLabel(sort: LibrarySort) = when (sort) { LibrarySort.ADDED -> "添加时间"; LibrarySort.ALPHABETICAL -> "首字母"; LibrarySort.MASTERY -> "熟练程度" }
private fun sortIcon(sort: LibrarySort) = when (sort) { LibrarySort.ADDED -> Icons.Default.AccessTime; LibrarySort.ALPHABETICAL -> Icons.Default.SortByAlpha; LibrarySort.MASTERY -> Icons.Default.TrendingUp }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryScreen(s: AppUiState, vm: RobokoViewModel) {
    var add by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val filtered = s.items.filter { it.headword.contains(s.query, true) || it.meanings.any { m -> m.label.contains(s.query, true) || m.chineseDefinition.contains(s.query, true) || m.contexts.any { c -> c.english.contains(s.query, true) || c.chinese.contains(s.query, true) } } }
    val sorted = when (s.librarySort) { LibrarySort.ADDED -> filtered.sortedBy { it.createdAt }; LibrarySort.ALPHABETICAL -> filtered.sortedBy { it.headword.lowercase() }; LibrarySort.MASTERY -> filtered.sortedBy { it.meanings.map { m -> m.progress }.average() } }.let { if (s.sortDescending) it.reversed() else it }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopAppBar(title = { Text("单词本") })
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(s.query, vm::search, Modifier.weight(1f), placeholder = { Text("搜索单词、释义或语境") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                Box {
                    IconButton({ sortMenu = true }) { Icon(sortIcon(s.librarySort), "选择排序方式") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        LibrarySort.values().forEach { mode ->
                            DropdownMenuItem(text = { Text(sortLabel(mode)) }, onClick = { vm.setSort(mode); sortMenu = false }, leadingIcon = { Icon(sortIcon(mode), null, tint = if (mode == s.librarySort) Teal else MaterialTheme.colorScheme.onSurfaceVariant) })
                        }
                    }
                }
                IconButton({ vm.toggleSortDirection() }) { Icon(if (s.sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, if (s.sortDescending) "当前倒序，点击改为正序" else "当前正序，点击改为倒序") }
            }
            Text("排序：${sortLabel(s.librarySort)} · ${if (s.sortDescending) "倒序" else "正序"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            if (sorted.isEmpty()) Text("没有匹配的学习条目", Modifier.padding(20.dp)) else LazyColumn(Modifier.fillMaxWidth()) { items(sorted) { item -> CompactItem(item, { vm.select(item.id) }, { vm.deleteItem(item.id) }) } }
        }
        FloatingActionButton(add = { add = true })
    }
    if (add) AddDialog({ add = false }, vm)
}

@Composable fun AddDialog(close: () -> Unit, vm: RobokoViewModel) {
    val state by vm.state.collectAsState()
    var text by remember { mutableStateOf("") }
    if (state.generatedItem != null) {
        val item = state.generatedItem!!
        AlertDialog(
            onDismissRequest = close,
            title = { Text("确认 AI 生成内容") },
            text = {
                Column {
                    Text(item.displayForm, style = MaterialTheme.typography.titleLarge)
                    item.meanings.forEach { meaning ->
                        Text("${meaning.label} · ${meaning.chineseDefinition}", Modifier.padding(top = 8.dp))
                        meaning.contexts.firstOrNull()?.let { context ->
                            Text(context.english, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("分类：${item.categories.joinToString()}", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button({ vm.confirmGenerated(item); close() }) { Text("确认保存") } },
            dismissButton = { TextButton({ vm.cancelGenerated() }) { Text("放弃") } }
        )
    } else {
        AlertDialog(onDismissRequest = close, title = { Text("添加学习条目") }, text = { Column { OutlinedTextField(text, { text = it }, label = { Text("单词或短语") }, singleLine = true); state.itemError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) } } }, confirmButton = { Button({ vm.generateItem(text) }, enabled = text.isNotBlank() && !state.generatingItem) { Text(if (state.generatingItem) "AI 生成中..." else "让 AI 整理") } }, dismissButton = { TextButton(close) { Text("取消") } })
    }
}

@Composable fun SpeakButton(text: String) { val context = androidx.compose.ui.platform.LocalContext.current; val tts = remember { TextToSpeech(context) { } }; DisposableEffect(Unit) { onDispose { tts.shutdown() } }; IconButton({ tts.language = Locale.US; tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "roboko-speech") }) { Icon(Icons.Default.VolumeUp, "播放发音", tint = Teal) } }

@Composable fun DetailScreen(item: LearningItem, vm: RobokoViewModel) { Column(Modifier.fillMaxSize().pointerInput(item.id) { detectVerticalDragGestures { _, dragAmount -> if (kotlin.math.abs(dragAmount) > 80f) vm.selectAdjacent(if (dragAmount < 0) 1 else -1) } }.padding(20.dp)) { Text(item.displayForm, style = MaterialTheme.typography.headlineLarge); SpeakButton(item.headword); Text(item.headword, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Text("整体进度 ${item.meanings.map { it.progress }.average().format1()} / 6", style = MaterialTheme.typography.titleMedium); LinearProgressIndicator(progress = (item.meanings.map { it.progress }.average() / 6).toFloat(), Modifier.fillMaxWidth().padding(vertical = 8.dp)); item.meanings.forEach { meaning -> Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Column(Modifier.padding(16.dp)) { Text("${meaning.index}  ${meaning.label}", style = MaterialTheme.typography.titleMedium); Text(meaning.partOfSpeech, color = Teal, style = MaterialTheme.typography.labelMedium); Text(meaning.englishDefinition, Modifier.padding(top = 8.dp)); Text(meaning.chineseDefinition, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("进度 ${meaning.progress.format1()} / 6", Modifier.padding(top = 8.dp)); meaning.contexts.forEach { context -> Text(context.english, Modifier.padding(top = 12.dp)); Text(context.chinese, style = MaterialTheme.typography.bodySmall); Text(formatter.format(context.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }; Button({ vm.openAiFor(item) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("追问 AI") } } }

@Composable fun ReviewScreen(s: AppUiState, vm: RobokoViewModel) { val r = s.review; if (r.stage == ReviewStage.COMPLETE) { CompleteScreen(s.stats); return }; val card = r.card; val item = r.item; if (card == null || item == null) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text("暂时没有可背诵内容", style = MaterialTheme.typography.titleLarge); Button({ vm.tab(Tab.LIBRARY) }) { Text("去添加条目") } }; return }; val meaning = item.meanings.first { it.id == card.meaningId }; Column(Modifier.fillMaxSize().padding(20.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("本次背诵 ${r.answered + 1}", style = MaterialTheme.typography.titleMedium); Text("${meaning.progress.format1()} / 6", color = Teal) }; Spacer(Modifier.height(30.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(22.dp)) { val sentence = if (r.stage == ReviewStage.RECALL) card.context.english.replace(item.headword, "███████", ignoreCase = true) else card.context.english; Text(sentence, style = MaterialTheme.typography.headlineSmall); Text(meaning.partOfSpeech, color = Teal, modifier = Modifier.padding(top = 14.dp)); if (r.hintUsed && r.stage == ReviewStage.RECALL) { Text("中文：${meaning.chineseDefinition}", Modifier.padding(top = 18.dp)); Text("英英：${meaning.englishDefinition}", style = MaterialTheme.typography.bodySmall) }; if (r.stage == ReviewStage.ANSWER_REVEALED) AnswerCard(item, meaning) } }; Spacer(Modifier.height(18.dp)); if (r.stage == ReviewStage.RECALL) { FilledTonalButton({ vm.hint() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Lightbulb, null); Spacer(Modifier.width(8.dp)); Text(if (r.hintUsed) "已使用提示" else "提示") }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton({ vm.answer(false) }, Modifier.weight(1f)) { Text("不知道") }; Button({ vm.answer(true) }, Modifier.weight(1f)) { Text("知道") } } } else { Button({ vm.next() }, Modifier.fillMaxWidth()) { Text("下一个") }; Spacer(Modifier.height(10.dp)); OutlinedButton({ vm.openAiFor(item) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("追问 AI") } } } }

@Composable fun AnswerCard(item: LearningItem, meaning: MeaningBranch) { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(item.headword, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp)); SpeakButton(item.headword) }; Text(meaning.chineseDefinition, Modifier.padding(top = 4.dp)); Text("美音 ${item.phoneticUs ?: "待生成"}    英音 ${item.phoneticUk ?: "待生成"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)); Text("进度 ${meaning.progress.format1()} / 6", style = MaterialTheme.typography.bodyMedium, color = Teal, modifier = Modifier.padding(top = 10.dp)) }

@Composable fun CompleteScreen(stats: TodayStats) { Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) { Text("今日完成", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(24.dp)); StatLine("新词学习", stats.newCount.toString()); StatLine("复习次数", stats.reviewCount.toString()); StatLine("总学习", stats.total.toString()); StatLine("熟练度变化", if (stats.progressDelta >= 0) "+${stats.progressDelta.format1()}" else stats.progressDelta.format1()); StatLine("已掌握", "+${stats.mastered}") } }

@Composable fun StatLine(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, color = Teal, style = MaterialTheme.typography.titleMedium) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralScreen(vm: RobokoViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState(); var expanded by remember { mutableStateOf(false) }; var test by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置") }; Text("通用设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(18.dp)); Text("下载镜像", style = MaterialTheme.typography.titleMedium)
        Box {
            Box(Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 14.dp)) { Text(s.mirror) }
            DropdownMenu(expanded, { expanded = false }) { listOf("GitHub 官方", "gh-proxy.net", "gh-proxy.com").forEach { DropdownMenuItem({ Text(it) }, { vm.setMirror(it); expanded = false }) } }
        }
        TextButton({ test = "测试中：已检查 latest release 链接" }, Modifier.fillMaxWidth()) { Text("测试镜像") }
        if (test.isNotBlank()) Text(test, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AboutScreen(vm: RobokoViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState(); val context = androidx.compose.ui.platform.LocalContext.current; var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置") }; Text("关于 Roboko", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp)); Card(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VellowK/Roboko"))) }) { Text("GitHub 仓库\nVellowK/Roboko", Modifier.padding(16.dp)) }
        Text("版本 2.0.1", Modifier.padding(top = 16.dp)); Text("构建日期：2026-09-10", style = MaterialTheme.typography.bodySmall)
        if (s.updateChecking) Text("正在检查更新...", Modifier.padding(top = 12.dp))
        s.updateVersion?.let { version ->
            Card(Modifier.fillMaxWidth().padding(top = 18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFE0E0))) { Column(Modifier.padding(16.dp)) { Text(version, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color(0xFFB3261E)); Text(if (expanded) s.updateNotes else s.updateNotes.take(120), Modifier.padding(top = 8.dp)); TextButton({ expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }; s.updateUrl?.let { url -> Button({ vm.startUpdate(url) }, Modifier.fillMaxWidth()) { Text("更新") } } } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsPage(s: AppUiState, vm: RobokoViewModel, onBack: () -> Unit) {
    var presetId by remember(s.sttCloud) { mutableStateOf(s.sttCloud.presetId) }
    var baseUrl by remember(s.sttCloud) { mutableStateOf(s.sttCloud.baseUrl) }
    var apiKey by remember(s.sttCloud) { mutableStateOf(s.sttCloud.apiKey) }
    var model by remember(s.sttCloud) { mutableStateOf(s.sttCloud.model) }
    var keyUrl by remember(s.sttCloud) { mutableStateOf(s.sttCloud.keyUrl) }
    var format by remember(s.sttCloud) { mutableStateOf(s.sttCloud.format) }
    val linkContext = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置") }
            Text("语音识别", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Text("默认使用 Android 系统识别。若系统识别不可用，或想要更好的中英混说效果，可配置下方云端识别接口。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        SttEngine.values().forEach { engine ->
            Row(Modifier.fillMaxWidth().clickable { vm.setSttEngine(engine) }.padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = s.sttEngine == engine, onClick = { vm.setSttEngine(engine) })
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(engine.label, style = MaterialTheme.typography.titleSmall)
                    Text(engine.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (engine == SttEngine.SYSTEM && !vm.systemVoice.isReady) Text("当前设备没有可用的系统语音服务，将自动使用云端识别", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("云端识别设置", style = MaterialTheme.typography.titleSmall)
        Text("兼容 OpenAI 的 /audio/transcriptions 接口。硅基流动的 SenseVoiceSmall 免费且支持中英混说。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sttPresets.forEach { preset ->
                FilterChip(
                    selected = presetId == preset.presetId,
                    onClick = {
                        presetId = preset.presetId
                        baseUrl = preset.baseUrl
                        model = preset.model
                        keyUrl = preset.keyUrl
                        // 各预设的 Key 互相独立，切换时载入该预设已保存的 Key
                        apiKey = s.sttKeys[preset.presetId] ?: ""
                        format = preset.format
                    },
                    label = { Text(sttPresetLabel(preset.presetId)) }
                )
            }
        }
        if (presetId == "volcano") {
            Text("火山引擎走私有 WebSocket 协议（大模型流式语音识别），暂未接入。当前请先使用「硅基流动」，其 SenseVoice 免费且支持中英混说。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (keyUrl.isNotBlank()) {
            Text(
                "获取 API Key：$keyUrl",
                style = MaterialTheme.typography.bodySmall,
                color = Teal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { linkContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl))) }
                    .padding(vertical = 6.dp)
            )
        }
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("接口地址（Base URL）") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
        Text("接口协议：${format.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                { vm.testSttCloud(SttCloudConfig(presetId, baseUrl.trim(), apiKey.trim(), model.trim(), keyUrl, format)) },
                Modifier.weight(1f),
                enabled = !s.sttTesting && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
            ) { Text(if (s.sttTesting) "测试中…" else "测试连接") }
            Button(
                { vm.saveSttCloud(SttCloudConfig(presetId, baseUrl.trim(), apiKey.trim(), model.trim(), keyUrl, format)) },
                Modifier.weight(1f),
                enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
            ) { Text("保存") }
        }
        s.sttStatus?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("✓")) Teal else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun sttPresetLabel(id: String) = when (id) {
    "siliconflow" -> "硅基流动"
    "mimo" -> "小米 MiMo"
    "openai" -> "OpenAI"
    else -> id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: RobokoViewModel) {
    var showProvider by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showGeneral by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    val s by vm.state.collectAsState()
    var provider by remember { mutableStateOf(vm.state.value.provider) }
    var expanded by remember { mutableStateOf(false) }
    val isCustom = provider.id == "custom"
    BackHandler(enabled = showProvider || showGeneral || showAbout || showVoice) { showProvider = false; showGeneral = false; showAbout = false; showVoice = false }
    if (showAbout) { AboutScreen(vm) { showAbout = false } } else if (showGeneral) { GeneralScreen(vm) { showGeneral = false } } else if (showVoice) { VoiceSettingsPage(s, vm) { showVoice = false } } else if (!showProvider) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp, bottom = 18.dp))
            TextButton({ showProvider = true }, Modifier.fillMaxWidth()) { Text("AI Provider 设置", modifier = Modifier.fillMaxWidth()) }
            TextButton({ showVoice = true }, Modifier.fillMaxWidth()) { Text("语音识别设置", modifier = Modifier.fillMaxWidth()) }
            TextButton({ showGeneral = true }, Modifier.fillMaxWidth()) { Text("通用设置", modifier = Modifier.fillMaxWidth()) }
            TextButton({ showAbout = true; vm.checkUpdate() }, Modifier.fillMaxWidth()) { Text("关于软件", modifier = Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(10.dp))
            Text("Provider、协议、余额和 API Key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton({ showProvider = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置") }
            Text("AI Provider 设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Text("配置保存在本机，API Key 使用 Android Keystore 加密。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(provider.name, {}, Modifier.fillMaxWidth().menuAnchor(), label = { Text("选择 Provider") }, readOnly = true, trailingIcon = null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                providerPresets.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset.name) }, onClick = { provider = preset.copy(apiKey = provider.apiKey); expanded = false })
                }
                DropdownMenuItem(text = { Text("自定义 Provider") }, onClick = { provider = provider.copy(id = "custom", name = if (isCustom) provider.name else "", model = if (isCustom) provider.model else "", format = ApiFormat.OPENAI); expanded = false })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(provider.name, { if (isCustom) provider = provider.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Provider 名称") }, enabled = isCustom, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(provider.baseUrl, { provider = provider.copy(baseUrl = it) }, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(provider.model, { provider = provider.copy(model = it) }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, enabled = true, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(provider.apiKey, { provider = provider.copy(apiKey = it) }, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(14.dp))
        Text("协议格式", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = provider.format == ApiFormat.OPENAI, onClick = { if (isCustom) provider = provider.copy(format = ApiFormat.OPENAI) }, label = { Text("OpenAI") }, enabled = isCustom)
            FilterChip(selected = provider.format == ApiFormat.CLAUDE, onClick = { if (isCustom) provider = provider.copy(format = ApiFormat.CLAUDE, responseApi = false) }, label = { Text("Claude") }, enabled = isCustom)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Response API"); Text("仅自定义 Provider 可配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = provider.responseApi, onCheckedChange = { if (isCustom && provider.format == ApiFormat.OPENAI) provider = provider.copy(responseApi = it) }, enabled = isCustom && provider.format == ApiFormat.OPENAI)
        }
        Button({ vm.checkBalance(provider) }, Modifier.fillMaxWidth(), enabled = provider.apiKey.isNotBlank() && provider.baseUrl.isNotBlank()) { Text("查询余额") }
        vm.state.value.balanceStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        Button({ vm.saveProvider(provider) }, Modifier.fillMaxWidth(), enabled = provider.baseUrl.isNotBlank() && provider.model.isNotBlank()) { Text("保存并返回 AI") }
    }
    }
}

@Composable
fun AiScreen(s: AppUiState, vm: RobokoViewModel) {
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var selIndex by remember { mutableStateOf<Int?>(null) }
    var textSelect by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var voiceDraft by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val selBody = selIndex?.let { i -> s.aiMessages.getOrNull(i)?.substringBefore(REF_MARK)?.removePrefix("你：")?.removePrefix("AI：") } ?: ""
    // 只渲染最近 4 条，往上翻到顶再多加载 4 条
    var visibleCount by remember { mutableStateOf(4) }
    var didInitialScroll by remember { mutableStateOf(false) }
    val visibleMessages = if (s.aiMessages.size <= visibleCount) s.aiMessages else s.aiMessages.takeLast(visibleCount)
    val msgOffset = s.aiMessages.size - visibleMessages.size

    // 进入页面直接跳到对话末尾
    LaunchedEffect(visibleMessages.size) {
        if (!didInitialScroll && visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.lastIndex)
            didInitialScroll = true
        }
    }
    // 上翻到顶时追加加载更早的 4 条
    LaunchedEffect(listState, s.aiMessages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            if (didInitialScroll && idx == 0 && s.aiMessages.size > visibleCount) visibleCount += 4
        }
    }

    // 智能滚动：仅在原本就贴近底部时跟随，往上翻历史不会被强行拖回
    LaunchedEffect(s.aiMessages.size, s.aiLoading, transcribing) {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (info.totalItemsCount > 0 && last >= info.totalItemsCount - 4) {
            listState.animateScrollToItem(info.totalItemsCount - 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // 标题栏右侧就是选中消息的操作区（整个页面的右上角）
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("AI 学习空间", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                if (selIndex != null && !textSelect) {
                    MsgAction(Icons.Default.ContentCopy, "复制") { clipboard.setText(AnnotatedString(selBody)); selIndex = null }
                    MsgAction(Icons.Default.LibraryAdd, "加入单词本") { vm.generateItem(selBody); selIndex = null }
                    MsgAction(Icons.Default.Translate, "翻译") { vm.ask("把这句翻译成中文，只给译文：$selBody"); selIndex = null }
                    MsgAction(Icons.Default.FormatQuote, "引用提问") { vm.quoteText(selBody); selIndex = null }
                }
            }
            // 点击空白处：先退出文字选择，再取消整条选中
            Box(
                Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(onTap = { if (textSelect) textSelect = false else selIndex = null })
                }
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(visibleMessages, key = { i, _ -> msgOffset + i }) { index, message ->
                        val absIndex = msgOffset + index
                        val isUser = message.startsWith("你：")
                        val full = message.substringBefore(REF_MARK).removePrefix("你：").removePrefix("AI：")
                        val refPart = if (message.contains(REF_MARK)) message.substringAfter(REF_MARK) else null
                        val refTitle = refPart?.substringBefore('\u0001')
                        val refSub = refPart?.substringAfter('\u0001', "")
                        // AI 结尾可带「推荐加入单词本：a, b」，这里解析出来并去掉该行
                        val recLine = if (isUser) null else full.lines().firstOrNull { it.trim().startsWith("推荐加入单词本") }
                        val recommends = recLine?.substringAfter("：", "")?.split(",", "，")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                        val body = if (recLine == null) full else full.lines().filterNot { it.trim().startsWith("推荐加入单词本") }.joinToString("\n").trim()
                        val selected = selIndex == absIndex
                        val bubbleColor = if (isUser) androidx.compose.ui.graphics.Color(0xFFE8EEF2) else androidx.compose.ui.graphics.Color(0xFFDDF2EC)
                        var selRange by remember(message) { mutableStateOf(TextRange(0, body.length)) }

                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                                // 左下角勾选标记，同时把卡片向右推开
                                if (selected) Icon(Icons.Default.CheckCircle, "已选中", tint = Teal, modifier = Modifier.padding(end = 6.dp, bottom = 10.dp).size(18.dp))
                                if (isUser && !selected) IconButton({ editing = absIndex to body }, Modifier.size(26.dp)) { Icon(Icons.Default.Edit, "编辑并重新生成", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp)) }
                                if (selected && textSelect) {
                                    // 文字选择：用只读输入框才能读到选中的文本
                                    var tf by remember(message) { mutableStateOf(TextFieldValue(body, selRange)) }
                                    val picked = if (tf.selection.length > 0) tf.text.substring(tf.selection.min, tf.selection.max) else ""
                                    // 操作卡片弹在消息旁边并对齐顶部，长消息也不用滚到底
                                    if (isUser) {
                                        TextSelActions(Modifier.align(androidx.compose.ui.Alignment.Top), clipboard, picked, { vm.quoteText(picked) }, { vm.ask("把这段翻译成中文，只给译文：$picked") })
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Surface(color = bubbleColor, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f, fill = false).widthIn(max = 250.dp)) {
                                        BasicTextField(
                                            value = tf,
                                            onValueChange = { tf = it },
                                            readOnly = true,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = androidx.compose.ui.graphics.Color(0xFF1B1B1B)),
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                    if (!isUser) {
                                        Spacer(Modifier.width(6.dp))
                                        TextSelActions(Modifier.align(androidx.compose.ui.Alignment.Top), clipboard, picked, { vm.quoteText(picked) }, { vm.ask("把这段翻译成中文，只给译文：$picked") })
                                    }
                                } else {
                                    var layoutResult by remember(message) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    Surface(
                                        color = bubbleColor,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .pointerInput(message, selected) {
                                                detectTapGestures(
                                                    onLongPress = { offset ->
                                                        if (selIndex == absIndex) {
                                                            // 第二次长按：直接选中按下的那个词组（走系统分词，中文按词切）
                                                            val layout = layoutResult
                                                            selRange = if (layout != null) layout.getWordBoundary(layout.getOffsetForPosition(offset)) else TextRange(0, body.length)
                                                            textSelect = true
                                                        } else {
                                                            selIndex = absIndex
                                                            textSelect = false
                                                        }
                                                    },
                                                    onTap = {
                                                        if (selIndex == absIndex) { textSelect = false; selIndex = null }
                                                    }
                                                )
                                            }
                                    ) { Text(body, Modifier.padding(14.dp).then(if (selected) Modifier.background(Teal.copy(alpha = .22f)) else Modifier), onTextLayout = { layoutResult = it }) }
                                }
                            }
                            if (refTitle != null) {
                                Surface(color = Teal.copy(alpha = .10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 4.dp).widthIn(max = 280.dp)) {
                                    Column(Modifier.padding(10.dp)) { Text("引用 · $refTitle", style = MaterialTheme.typography.labelMedium, color = Teal); if (!refSub.isNullOrBlank()) Text(refSub, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            // AI 推荐的可加入词条，点一下就让 AI 整理并加入
                            if (recommends.isNotEmpty()) {
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    recommends.forEach { word -> AssistChip(onClick = { vm.generateItem(word) }, label = { Text("+ $word") }) }
                                }
                            }
                        }
                    }
                    // 语音草稿：流式文字直接长在对话记录里的用户气泡上，气泡随文本变化大小
                    if (listening || transcribing) {
                        item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Surface(color = androidx.compose.ui.graphics.Color(0xFFE8EEF2), shape = RoundedCornerShape(16.dp)) {
                                    Text(voiceDraft.ifBlank { "…" }, Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
                                }
                            }
                        }
                    }
                    // 转写期间 AI 侧用省略号表示正在思考
                    if (transcribing) {
                        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.Start) { Surface(color = androidx.compose.ui.graphics.Color(0xFFDDF2EC), shape = RoundedCornerShape(16.dp)) { Text("…", Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) } } }
                    }
                }
            }
            if (s.aiLoading) Text("AI 正在思考...", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.generatingItem) Text("AI 正在整理词条…", Modifier.padding(vertical = 8.dp), color = Teal)
            // 引用的对话内容卡片（与引用单词同一形态）
            s.aiTextRef?.let { quoted ->
                Surface(color = Orange.copy(alpha = .12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("引用内容", style = MaterialTheme.typography.labelMedium, color = Orange); Text(quoted, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                        IconButton({ vm.clearTextRef() }) { Icon(Icons.Default.Close, "移除引用", tint = Orange) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            s.aiReference?.let { item ->
                Surface(color = Teal.copy(alpha = .10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("引用学习条目", style = MaterialTheme.typography.labelMedium, color = Teal); Text(item.displayForm, style = MaterialTheme.typography.titleMedium); Text(item.meanings.firstOrNull()?.let { it.label + " · " + it.chineseDefinition } ?: "", style = MaterialTheme.typography.bodySmall) }
                        IconButton({ vm.clearReference() }) { Icon(Icons.Default.Close, "移除引用", tint = Teal) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (voiceMode) {
                // 语音模式：文本框整体变成「按住说话」
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    VoicePushToTalkButton(
                        s = s,
                        vm = vm,
                        listening = listening,
                        modifier = Modifier.weight(1f),
                        onPartial = { partial -> voiceDraft = partial },
                        onFinal = { finalText -> transcribing = false; voiceDraft = ""; if (finalText.isNotBlank()) vm.ask(finalText) },
                        onListeningChanged = { isListening -> listening = isListening },
                        onTranscribing = { value -> transcribing = value }
                    )
                    IconButton({ voiceMode = false }) { Icon(Icons.Default.Keyboard, "切换到打字") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("问问当前词条") }, singleLine = false, maxLines = 3)
                    // 没字时是「切换语音」，有字时变成「发送」
                    IconButton(
                        onClick = { if (text.isBlank()) voiceMode = true else { vm.ask(text); text = "" } },
                        enabled = !s.aiLoading
                    ) { Icon(if (text.isBlank()) Icons.Default.Mic else Icons.Default.Send, if (text.isBlank()) "切换到语音输入" else "发送") }
                }
            }
        }
        if (listening) VoiceListeningOverlay()
    }
    // AI 整理完成的确认弹窗（从「加入单词本」或推荐词条触发）
    if (s.generatedItem != null) AddDialog({ vm.cancelGenerated() }, vm)
    editing?.let { pair ->
        val index = pair.first
        val original = pair.second
        var draft by remember(pair) { mutableStateOf(original) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑后重新生成") },
            text = {
                Column {
                    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("你的问题") }, maxLines = 5)
                    Text("该条之后的对话会被清除，AI 将基于新内容重新回答。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { Button({ vm.editUserMessage(index, draft); editing = null }, enabled = draft.isNotBlank() && !s.aiLoading) { Text("重新生成") } },
            dismissButton = { TextButton({ editing = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MsgAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(32.dp)) { Icon(icon, label, tint = Teal, modifier = Modifier.size(19.dp)) }
}

/** 文字选中后弹出的操作卡片：复制 / 引用 / 翻译。 */
@Composable
private fun TextSelActions(
    modifier: Modifier,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    picked: String,
    onQuote: () -> Unit,
    onTranslate: () -> Unit
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp, shadowElevation = 3.dp) {
        Row {
            IconButton({ clipboard.setText(AnnotatedString(picked)) }, Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, "复制", tint = Teal, modifier = Modifier.size(19.dp)) }
            IconButton(onQuote, Modifier.size(40.dp)) { Icon(Icons.Default.FormatQuote, "引用", tint = Teal, modifier = Modifier.size(19.dp)) }
            IconButton(onTranslate, Modifier.size(40.dp)) { Icon(Icons.Default.Translate, "翻译", tint = Teal, modifier = Modifier.size(19.dp)) }
        }
    }
}

@Composable
fun VoiceListeningOverlay() {
    val transition = rememberInfiniteTransition(label = "voice-bars")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f), androidx.compose.ui.graphics.Color.Black.copy(alpha = .25f), Teal.copy(alpha = .35f)))),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter
    ) {
        Column(Modifier.padding(bottom = 120.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                repeat(21) { index ->
                    val barHeight by transition.animateFloat(initialValue = 8f, targetValue = 42f + (index % 5) * 8f, animationSpec = infiniteRepeatable(tween(420 + index * 24), RepeatMode.Reverse), label = "bar-$index")
                    Box(Modifier.padding(horizontal = 2.dp).width(4.dp).height(barHeight.dp).background(androidx.compose.ui.graphics.Color(0xFF6EC6E8), RoundedCornerShape(4.dp)))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("正在聆听，松开结束", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun VoicePushToTalkButton(s: AppUiState, vm: RobokoViewModel, listening: Boolean, modifier: Modifier = Modifier, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onListeningChanged: (Boolean) -> Unit, onTranscribing: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 系统语音服务不可用时（模拟器、部分精简 ROM）自动回退到离线模型
    val engine = if (s.sttEngine == SttEngine.SYSTEM && !vm.systemVoice.isReady) SttEngine.CLOUD else s.sttEngine
    val fallback = engine != s.sttEngine
    val controller: SttController = if (engine == SttEngine.SYSTEM) vm.systemVoice else vm.cloudVoice
    var status by remember { mutableStateOf<String?>(null) }
    var ready by remember(engine) { mutableStateOf(controller.isReady) }
    var hasPermission by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) status = "未授予麦克风权限，无法语音输入"
    }
    LaunchedEffect(engine) {
        if (controller.isReady) { ready = true; status = null }
        else {
            status = if (controller.isLoading) "正在加载离线语音模型…" else null
            controller.prepare(
                onReady = { ready = true; status = null },
                onError = { message -> ready = false; status = message }
            )
        }
    }
    Column(modifier.padding(bottom = 2.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(if (listening) Orange else Teal, RoundedCornerShape(26.dp))
                .pointerInput(engine, ready, hasPermission) {
                    detectTapGestures(
                        onPress = {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@detectTapGestures
                            }
                            if (!ready) {
                                status = if (controller.isLoading) "正在加载语音引擎…" else "语音引擎尚未就绪"
                                return@detectTapGestures
                            }
                            // 按下开始录音，松手结束；结束后等待转写结果再自动发送
                            var done = false
                            val started = controller.start(
                                onPartial = { partial -> onPartial(partial) },
                                onFinal = { finalText ->
                                    done = true
                                    onTranscribing(false)
                                    onListeningChanged(false)
                                    if (finalText.isNotBlank()) onFinal(finalText)
                                },
                                onError = { message ->
                                    done = true
                                    status = message
                                    onTranscribing(false)
                                    onListeningChanged(false)
                                }
                            )
                            if (!started) return@detectTapGestures
                            status = null
                            onListeningChanged(true)
                            try {
                                awaitRelease()
                            } finally {
                                controller.stop()
                                onListeningChanged(false)
                                // 松手到出文字之间有间隔，先告诉界面「正在转写」
                                if (!done) onTranscribing(true)
                            }
                        }
                    )
                },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (listening) "松开结束" else "按住说话", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        Text(engine.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (fallback) Text("系统语音服务不可用，已自动改用离线中英双语识别", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp)) }
    }
}


private fun Double.format1() = "%.1f".format(this)
