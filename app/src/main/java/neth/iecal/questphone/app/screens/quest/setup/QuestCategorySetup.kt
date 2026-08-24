package neth.iecal.questphone.app.screens.quest.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neth.iecal.questphone.app.screens.quest.QUEST_CATEGORIES

/**
 * Reusable composable for picking a quest category + color during quest setup.
 * Embed this in any quest setup screen (SetSwiftMark, SetDeepFocus, etc.)
 */
@Composable
fun QuestCategoryPicker(
    selectedCategory: String,
    selectedColor: Long,
    onCategorySelected: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var customCategory by remember { mutableStateOf("") }
    val allCategories = QUEST_CATEGORIES + listOf("Custom" to 0xFF607D8BL)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allCategories) { (name, colorVal) ->
                val isSelected = selectedCategory == name
                val chipColor = Color(colorVal)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) chipColor.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) chipColor else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onCategorySelected(name, colorVal) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (selectedCategory == "Custom") {
            OutlinedTextField(
                value = customCategory,
                onValueChange = {
                    customCategory = it
                    onCategorySelected(it, selectedColor)
                },
                label = { Text("Custom category name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
