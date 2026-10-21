package com.roboko.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Geometry is not UI state: onTextLayout must not invalidate/recompose a scrolling row. */
private class MessageLayout { var result: TextLayoutResult? = null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatMessageBubble(
    message: ChatMessage,
    selected: Boolean,
    selectionRange: TextRange?,
    generating: Boolean,
    savedWordKeys: Set<String>,
    onLongPress: (Long, TextRange) -> Unit,
    onSelectionChange: (Long, TextRange) -> Unit,
    onTap: (Long) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onGenerate: (String, String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val bubbleColor = if (message.isUser) UserBubbleColor else AiBubbleColor
    val layout = remember(message.id) { MessageLayout() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (selected) Icon(Icons.Default.CheckCircle, "已选中", tint = primary, modifier = Modifier.padding(end = 6.dp, bottom = 10.dp).size(18.dp))
            if (message.isUser && !selected) IconButton({ onEdit(message) }, Modifier.size(26.dp)) {
                Icon(Icons.Default.Edit, "编辑并重新生成", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            Surface(color = bubbleColor, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 280.dp)) {
                if (selected && selectionRange != null) {
                    val value = remember(message.body, selectionRange) { TextFieldValue(message.body, selectionRange) }
                    BasicTextField(
                        value = value,
                        onValueChange = { onSelectionChange(message.id, it.selection) },
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF1B1B1B)),
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    Text(
                        message.body,
                        modifier = Modifier.padding(14.dp)
                            .then(if (selected) Modifier.background(primary.copy(alpha = .22f)) else Modifier)
                            .pointerInput(message.id, selected) {
                                detectTapGestures(
                                    onLongPress = { offset ->
                                        val result = layout.result
                                        val range = result?.getWordBoundary(result.getOffsetForPosition(offset)) ?: TextRange(0, message.body.length)
                                        onLongPress(message.id, range)
                                    },
                                    onTap = { onTap(message.id) }
                                )
                            },
                        onTextLayout = { layout.result = it }
                    )
                }
            }
        }
        message.referenceTitle?.let { title ->
            Surface(color = primary.copy(alpha = .10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 4.dp).widthIn(max = 280.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("引用 · $title", style = MaterialTheme.typography.labelMedium, color = primary)
                    message.referenceSubtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (message.candidates.isNotEmpty()) {
            FlowRow(Modifier.widthIn(max = 280.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                message.candidates.forEach { word ->
                    val added = LearningLibrary.wordKey(word) in savedWordKeys
                    AssistChip(
                        onClick = { if (!added) onGenerate(word, message.full) },
                        label = { Text(if (added) word else "+ $word") },
                        enabled = !added && !generating,
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = if (added) primary.copy(alpha = .14f) else Color.Transparent,
                            disabledLabelColor = if (added) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        )
                    )
                }
            }
        }
    }
}
