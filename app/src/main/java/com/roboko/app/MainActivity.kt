package com.roboko.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max

private val Teal = androidx.compose.ui.graphics.Color(0xFF176B87)
private val Orange = androidx.compose.ui.graphics.Color(0xFFE77B45)
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

data class ContextEntry(val id: String, val english: String, val chinese: String, val createdAt: Instant, val representative: Boolean)
data class MeaningBranch(val id: String, val index: Int, val label: String, val partOfSpeech: String, val englishDefinition: String, val chineseDefinition: String, val progress: Double, val contexts: List<ContextEntry>)
data class LearningItem(val id: String, val headword: String, val displayForm: String, val phoneticUs: String?, val phoneticUk: String?, val createdAt: Instant, val deletedAt: Instant?, val meanings: List<MeaningBranch>, val categories: List<String>)
data class ReviewCard(val itemId: String, val meaningId: String, val context: ContextEntry)
data class TodayStats(val newCount: Int, val reviewCount: Int, val total: Int, val progressDelta: Double, val mastered: Int)
data class ReviewResult(val known: Boolean, val hint: Boolean)

enum class Tab { HOME, LIBRARY, REVIEW, AI }
enum class ReviewStage { RECALL, ANSWER_REVEALED, COMPLETE }

data class ReviewUiState(val card: ReviewCard?, val item: LearningItem?, val stage: ReviewStage = ReviewStage.RECALL, val hintUsed: Boolean = false, val answered: Int = 0, val results: List<ReviewResult> = emptyList())

data class AppUiState(val tab: Tab = Tab.HOME, val items: List<LearningItem> = emptyList(), val selectedItemId: String? = null, val query: String = "", val stats: TodayStats = TodayStats(0, 0, 0, 0.0, 0), val review: ReviewUiState = ReviewUiState(null, null), val aiMessages: List<String> = listOf("你好，我可以帮你理解语境、整理词义，或发现相关学习对象。"))

object ReviewEngine {
    fun update(progress: Double, known: Boolean, hint: Boolean): Double {
        val delta = when { known && !hint -> 0.8 + (6.0 - progress) * 0.18; known -> 0.35; !hint -> -1.0 - progress * 0.08; else -> -0.45 }
        return (progress + delta).coerceIn(0.0, 6.0)
    }
}

class MockRepository {
    private val now = Instant.now()
    private val initial = LearningItem("cancel", "cancel", "can·cel", "/ˈkæn.səl/", "/ˈkæn.səl/", now.minusSeconds(3600), null, listOf(
        MeaningBranch("cancel-1", 1, "取消；终止", "verb", "to decide that a planned event will not happen", "取消；终止", 3.2, listOf(ContextEntry("c1", "The company decided to cancel the meeting.", "公司决定取消会议。", now.minusSeconds(3600), true), ContextEntry("c2", "You can cancel your reservation online.", "你可以在线取消预订。", now.minusSeconds(1800), false))),
        MeaningBranch("cancel-2", 2, "抵消；撤销", "verb", "to offset or neutralize an effect", "抵消；撤销", 1.5, listOf(ContextEntry("c3", "The two signals cancel each other out.", "两个信号相互抵消。", now.minusSeconds(1200), true)))
    ), listOf("工作沟通", "日常表达"))
    private val _items = MutableStateFlow(listOf(initial))
    val items: StateFlow<List<LearningItem>> = _items.asStateFlow()
    var lastCard: ReviewCard? = null
    var stats = TodayStats(0, 2, 2, 0.0, 0)

    fun add(raw: String): LearningItem {
        val word = raw.trim().lowercase()
        val item = LearningItem(UUID.randomUUID().toString(), word, word, null, null, Instant.now(), null, listOf(MeaningBranch(UUID.randomUUID().toString(), 1, "主要含义", "phrase", "a useful expression to learn in context", "待由 AI 补充中文释义", 0.0, listOf(ContextEntry(UUID.randomUUID().toString(), "Let's learn the expression: $word.", "让我们在语境中学习 $word。", Instant.now(), true)))), listOf("待整理"))
        _items.value = listOf(item) + _items.value
        stats = stats.copy(newCount = stats.newCount + 1, total = stats.total + 1)
        return item
    }
    fun updateAnswer(card: ReviewCard, known: Boolean, hint: Boolean) {
        _items.value = _items.value.map { item ->
            if (item.id != card.itemId) item else item.copy(
                meanings = item.meanings.map { meaning ->
                    if (meaning.id != card.meaningId) meaning
                    else meaning.copy(progress = ReviewEngine.update(meaning.progress, known, hint))
                }
            )
        }
        val delta = if (known) 0.35 else -0.25
        stats = stats.copy(reviewCount = stats.reviewCount + 1, total = stats.newCount + stats.reviewCount + 0, progressDelta = stats.progressDelta + delta, mastered = _items.value.count { it.meanings.all { m -> m.progress >= 5.0 } })
    }
    fun nextCard(exclude: ReviewCard?): ReviewCard? {
        val candidates = _items.value.filter { it.deletedAt == null }.flatMap { item -> item.meanings.flatMap { meaning -> meaning.contexts.map { ReviewCard(item.id, meaning.id, it) } } }.filter { it.context.id != exclude?.context?.id }
        return candidates.randomOrNull() ?: _items.value.firstOrNull()?.meanings?.firstOrNull()?.contexts?.firstOrNull()?.let { ReviewCard(_items.value.first().id, _items.value.first().meanings.first().id, it) }
    }
}

class RobokoViewModel : ViewModel() {
    private val repo = MockRepository()
    private val _state = MutableStateFlow(AppUiState(items = repo.items.value, stats = repo.stats))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    init { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { repo.items.collect { _state.value = _state.value.copy(items = it) } } }
    fun tab(tab: Tab) { _state.value = _state.value.copy(tab = tab, selectedItemId = null) }
    fun search(q: String) { _state.value = _state.value.copy(query = q) }
    fun select(id: String) { _state.value = _state.value.copy(selectedItemId = id) }
    fun add(word: String) { if (word.isNotBlank()) repo.add(word); _state.value = _state.value.copy(stats = repo.stats, tab = Tab.LIBRARY) }
    fun startReview() { val card = repo.nextCard(null); _state.value = _state.value.copy(tab = Tab.REVIEW, review = ReviewUiState(card, _state.value.items.firstOrNull { it.id == card?.itemId })) }
    fun hint() { _state.value = _state.value.copy(review = _state.value.review.copy(hintUsed = true)) }
    fun answer(known: Boolean) { val r = _state.value.review; val card = r.card ?: return; repo.updateAnswer(card, known, r.hintUsed); _state.value = _state.value.copy(stats = repo.stats, review = r.copy(stage = ReviewStage.ANSWER_REVEALED, item = _state.value.items.firstOrNull { it.id == card.itemId }, answered = r.answered + 1, results = r.results + ReviewResult(known, r.hintUsed))) }
    fun next() { val old = _state.value.review; val card = repo.nextCard(old.card); if (card == null || old.answered >= 5) _state.value = _state.value.copy(review = old.copy(stage = ReviewStage.COMPLETE, card = null)); else _state.value = _state.value.copy(review = ReviewUiState(card, _state.value.items.firstOrNull { it.id == card.itemId }, answered = old.answered)) }
    fun ask(text: String) { if (text.isNotBlank()) _state.value = _state.value.copy(aiMessages = _state.value.aiMessages + "你：$text" + "AI：结合当前词条，我建议先比较它在不同语境中的动作对象，再尝试自己造一个句子。") }
}

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { RobokoApp() } } }

@Composable fun RobokoApp(vm: RobokoViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Orange, background = androidx.compose.ui.graphics.Color(0xFFF7FAFC))) {
        Scaffold(bottomBar = { Navigation(vm, state.tab) }) { pad -> Surface(Modifier.fillMaxSize().padding(pad)) { when { state.selectedItemId != null -> DetailScreen(state.items.first { it.id == state.selectedItemId }, vm); state.tab == Tab.HOME -> HomeScreen(state, vm); state.tab == Tab.LIBRARY -> LibraryScreen(state, vm); state.tab == Tab.REVIEW -> ReviewScreen(state, vm); else -> AiScreen(state, vm) } } }
    }
}

@Composable fun Navigation(vm: RobokoViewModel, selected: Tab) { Row(Modifier.fillMaxWidth().navigationBarsPadding().background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.SpaceEvenly) { listOf(Tab.HOME to ("首页" to Icons.Default.Home), Tab.LIBRARY to ("单词本" to Icons.Default.Book), Tab.REVIEW to ("背诵" to Icons.Default.School), Tab.AI to ("AI" to Icons.Default.AutoAwesome)).forEach { (tab, label) -> Column(Modifier.clickable { vm.tab(tab) }.padding(10.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Icon(label.second, label.first, tint = if (selected == tab) Teal else MaterialTheme.colorScheme.onSurfaceVariant); Text(label.first, style = MaterialTheme.typography.labelSmall, color = if (selected == tab) Teal else MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(s: AppUiState, vm: RobokoViewModel) { var add by remember { mutableStateOf(false) }; Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { TopAppBar(title = { Text("Roboko") }, actions = { IconButton({}) { Icon(Icons.Default.Settings, "设置") } }); Text("今天，继续在真实语境里记住它们。", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(16.dp)); Card(colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = .10f)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("今日概览", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Stat("新词", s.stats.newCount); Stat("复习", s.stats.reviewCount); Stat("总计", s.stats.total) } } }; Spacer(Modifier.height(18.dp)); Button({ vm.startReview() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.School, null); Spacer(Modifier.width(8.dp)); Text("开始背诵") }; Spacer(Modifier.height(24.dp)); Text("最近加入", style = MaterialTheme.typography.titleMedium); s.items.take(3).forEach { CompactItem(it) { vm.select(it.id) } }; Spacer(Modifier.height(12.dp)); Text("每日目标只是参考，不会限制你的实际背诵队列。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (add) AddDialog({ add = false }, { vm.add(it); add = false }) }; FloatingActionButton(add = { add = true }) }

@Composable fun FloatingActionButton(add: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomEnd) { IconButton({ add() }, Modifier.padding(18.dp).size(54.dp).background(Orange, RoundedCornerShape(16.dp))) { Icon(Icons.Default.Add, "添加学习条目", tint = androidx.compose.ui.graphics.Color.White) } } }
@Composable fun Stat(label: String, value: Int) { Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = Teal); Text(label, style = MaterialTheme.typography.bodySmall) } }
@Composable fun CompactItem(item: LearningItem, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(item.displayForm, style = MaterialTheme.typography.titleMedium); Text(item.meanings.firstOrNull()?.label ?: "待整理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${item.meanings.map { it.progress }.average().format1()} / 6", color = Teal) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryScreen(s: AppUiState, vm: RobokoViewModel) { var add by remember { mutableStateOf(false) }; val results = s.items.filter { it.headword.contains(s.query, true) || it.meanings.any { m -> m.label.contains(s.query, true) || m.chineseDefinition.contains(s.query, true) || m.contexts.any { c -> c.english.contains(s.query, true) || c.chinese.contains(s.query, true) } } }; Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { TopAppBar(title = { Text("单词本") }, actions = { IconButton({ add = true }) { Icon(Icons.Default.Add, "添加学习条目") } }); OutlinedTextField(s.query, vm::search, Modifier.fillMaxWidth(), placeholder = { Text("搜索单词、释义或语境") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true); Spacer(Modifier.height(14.dp)); if (results.isEmpty()) Text("没有匹配的学习条目", Modifier.padding(20.dp)); else LazyColumn { items(results) { CompactItem(it) { vm.select(it.id) } } } }; if (add) AddDialog({ add = false }, { vm.add(it); add = false }) }

@Composable fun AddDialog(close: () -> Unit, save: (String) -> Unit) { var text by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("添加学习条目") }, text = { OutlinedTextField(text, { text = it }, label = { Text("单词或短语") }, singleLine = true) }, confirmButton = { Button({ save(text) }) { Text("加入并整理") } }, dismissButton = { TextButton(close) { Text("取消") } }) }

@Composable fun DetailScreen(item: LearningItem, vm: RobokoViewModel) { Column(Modifier.fillMaxSize().padding(20.dp)) { Text(item.displayForm, style = MaterialTheme.typography.headlineLarge); Text(item.headword, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Text("整体进度 ${item.meanings.map { it.progress }.average().format1()} / 6", style = MaterialTheme.typography.titleMedium); LinearProgressIndicator(progress = (item.meanings.map { it.progress }.average() / 6).toFloat(), Modifier.fillMaxWidth().padding(vertical = 8.dp)); item.meanings.forEach { meaning -> Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Column(Modifier.padding(16.dp)) { Text("${meaning.index}  ${meaning.label}", style = MaterialTheme.typography.titleMedium); Text(meaning.partOfSpeech, color = Teal, style = MaterialTheme.typography.labelMedium); Text(meaning.englishDefinition, Modifier.padding(top = 8.dp)); Text(meaning.chineseDefinition, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("进度 ${meaning.progress.format1()} / 6", Modifier.padding(top = 8.dp)); meaning.contexts.forEach { context -> Text(context.english, Modifier.padding(top = 12.dp)); Text(context.chinese, style = MaterialTheme.typography.bodySmall); Text(formatter.format(context.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }; Button({ vm.tab(Tab.AI) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("追问 AI") } } }

@Composable fun ReviewScreen(s: AppUiState, vm: RobokoViewModel) { val r = s.review; if (r.stage == ReviewStage.COMPLETE) { CompleteScreen(s.stats) ; return }; val card = r.card; val item = r.item; if (card == null || item == null) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("暂时没有可背诵内容", style = MaterialTheme.typography.titleLarge); Button({ vm.tab(Tab.LIBRARY) }) { Text("去添加条目") } }; return }; val meaning = item.meanings.first { it.id == card.meaningId }; Column(Modifier.fillMaxSize().padding(20.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("本次背诵 ${r.answered + 1}", style = MaterialTheme.typography.titleMedium); Text("${meaning.progress.format1()} / 6", color = Teal) }; Spacer(Modifier.height(30.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(22.dp)) { val sentence = if (r.stage == ReviewStage.RECALL) card.context.english.replace(item.headword, "███████", ignoreCase = true) else card.context.english; Text(sentence, style = MaterialTheme.typography.headlineSmall); Text(meaning.partOfSpeech, color = Teal, modifier = Modifier.padding(top = 14.dp)); if (r.hintUsed && r.stage == ReviewStage.RECALL) { Text("中文：${meaning.chineseDefinition}", Modifier.padding(top = 18.dp)); Text("英英：${meaning.englishDefinition}", style = MaterialTheme.typography.bodySmall) }; if (r.stage == ReviewStage.ANSWER_REVEALED) AnswerCard(item, meaning) } }; Spacer(Modifier.height(18.dp)); if (r.stage == ReviewStage.RECALL) { FilledTonalButton({ vm.hint() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Lightbulb, null); Spacer(Modifier.width(8.dp)); Text(if (r.hintUsed) "已使用提示" else "提示") }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton({ vm.answer(false) }, Modifier.weight(1f)) { Icon(Icons.Default.Close, null); Text("不知道") }; Button({ vm.answer(true) }, Modifier.weight(1f)) { Icon(Icons.Default.Check, null); Text("知道") } } } else Button({ vm.next() }, Modifier.fillMaxWidth()) { Text("下一条") } } }

@Composable fun AnswerCard(item: LearningItem, meaning: MeaningBranch) { Text(item.headword, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp)); Text(meaning.chineseDefinition, Modifier.padding(top = 4.dp)); Text("美音 ${item.phoneticUs ?: "待生成"}    英音 ${item.phoneticUk ?: "待生成"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
@Composable fun CompleteScreen(stats: TodayStats) { Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) { Text("今日完成", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(24.dp)); StatLine("新词学习", stats.newCount.toString()); StatLine("复习次数", stats.reviewCount.toString()); StatLine("总学习", stats.total.toString()); StatLine("熟练度变化", if (stats.progressDelta >= 0) "+${stats.progressDelta.format1()}" else stats.progressDelta.format1()); StatLine("已掌握", "+${stats.mastered}") } }
@Composable fun StatLine(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, color = Teal, style = MaterialTheme.typography.titleMedium) } }

@Composable fun AiScreen(s: AppUiState, vm: RobokoViewModel) { var text by remember { mutableStateOf("") }; Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { Text("AI 学习空间", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 22.dp, bottom = 16.dp)); LazyColumn(Modifier.weight(1f)) { items(s.aiMessages) { message -> Text(message, Modifier.padding(vertical = 8.dp)) } }; Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("问问当前词条") }, singleLine = true); IconButton({ vm.ask(text); text = "" }) { Icon(Icons.Default.AutoAwesome, "发送问题") } } } }

private fun Double.format1() = "%.1f".format(this)
