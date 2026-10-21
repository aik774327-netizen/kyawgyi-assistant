package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Task
import com.example.ui.theme.AngelSurfaceDark

/**
 * Reusable UI component to filter tasks based on color-coded 'category' tags.
 * Displays interactive chips with tag icons, localized labels, and dynamic count badges.
 *
 * @param selectedCategory Currently selected category filter.
 * @param onSelectCategory Callback triggered when a category tag is chosen.
 * @param tasks Optional list of tasks to compute live tag counts.
 * @param modifier Custom modifier for styling and layout constraints.
 */
@Composable
fun TaskCategoryFilterBar(
    selectedCategory: TaskCategory,
    onSelectCategory: (TaskCategory) -> Unit,
    modifier: Modifier = Modifier,
    tasks: List<Task> = emptyList()
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_category_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(TaskCategory.entries) { category ->
            val isSelected = selectedCategory == category

            // Calculate tag count for this category
            val tagCount = if (category == TaskCategory.ALL) {
                tasks.size
            } else {
                tasks.count { task ->
                    task.category.equals(category.title, ignoreCase = true) ||
                            task.category.equals(category.name, ignoreCase = true) ||
                            task.tags.contains(category.title, ignoreCase = true)
                }
            }

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) category.color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                label = "FilterChipBg"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                label = "FilterChipText"
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = backgroundColor,
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectCategory(category) }
                    .testTag("filter_tag_${category.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        tint = if (isSelected) Color.Black else category.color,
                        modifier = Modifier.size(15.dp)
                    )

                    Text(
                        text = category.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )

                    // Tag count indicator badge
                    if (tasks.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color.Black.copy(alpha = 0.18f) else category.color.copy(alpha = 0.2f),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = tagCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.Black else category.color,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
