package io.sentry.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.sentry.ui.IssueQueryFields
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

data class QuerySuggestion(
    val text: String,
    val description: String? = null,
    val insertText: String = text
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueryBuilder(
    queryText: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSuggestions by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<QuerySuggestion>>(emptyList()) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text("Filter")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, color = JewelTheme.globalColors.outlines.focused, RoundedCornerShape(4.dp))
        ) {
            BasicTextField(
                modifier = Modifier.fillMaxWidth()
                    .padding(4.dp),
                textStyle = JewelTheme.defaultTextStyle,
                value = queryText,
                onValueChange = { newValue: String ->
                    onQueryChanged(newValue)
                    suggestions = getQuerySuggestions(newValue)
                    showSuggestions = suggestions.isNotEmpty()
                },
                singleLine = true,
            )

            if (showSuggestions) {
                Popup(
                    offset = IntOffset(0, 40),
                    onDismissRequest = { showSuggestions = false }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(min = 300.dp, max = 500.dp)
                            .heightIn(max = 200.dp)
                            .background(
                                JewelTheme.globalColors.panelBackground,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(4.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Replace the current incomplete token with the suggestion
                                        val newQuery = replaceCurrentToken(queryText, suggestion.insertText)
                                        onQueryChanged(newQuery)
                                        showSuggestions = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        suggestion.text,
                                        style = JewelTheme.defaultTextStyle
                                    )
                                    if (suggestion.description != null) {
                                        Text(
                                            suggestion.description,
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8
                                            ),
                                            color = JewelTheme.defaultTextStyle.color.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getQuerySuggestions(query: String): List<QuerySuggestion> {
    val suggestions = mutableListOf<QuerySuggestion>()

    // Get the current token being typed (last word after space or start of string)
    val currentToken = getCurrentToken(query)

    if (currentToken.isEmpty()) {
        // Show common field suggestions when starting
        suggestions.addAll(
            listOf(
                QuerySuggestion("is:", "Filter by issue properties", "is:"),
                QuerySuggestion("level:", "Filter by error level", "level:"),
                QuerySuggestion("error.type:", "Filter by error type", "error.type:"),
                QuerySuggestion("user.email:", "Filter by user email", "user.email:"),
                QuerySuggestion("environment:", "Filter by environment", "environment:")
            )
        )
    } else {
        // Parse the current token to see if it's a field or value
        val colonIndex = currentToken.indexOf(':')

        if (colonIndex == -1) {
            // Still typing field name, suggest matching fields
            IssueQueryFields.fields.forEach { field ->
                if (field.key.startsWith(currentToken, ignoreCase = true)) {
                    suggestions.add(
                        QuerySuggestion(
                            "${field.key}:",
                            field.description,
                            "${field.key}:"
                        )
                    )
                }
            }
        } else {
            // Typing value after colon, suggest field values
            val fieldKey = currentToken.substring(0, colonIndex)
            val valuePrefix = currentToken.substring(colonIndex + 1)

            IssueQueryFields.findFieldByKey(fieldKey)?.let { field ->
                // Add predefined values for this field
                field.values.forEach { value ->
                    if (value.value.startsWith(valuePrefix, ignoreCase = true)) {
                        suggestions.add(
                            QuerySuggestion(
                                "${fieldKey}:${value.value}",
                                value.description,
                                "${fieldKey}:${value.value}"
                            )
                        )
                    }
                }

                // For boolean fields, suggest true/false if not already in predefined values
                if (field.values.isEmpty() && (fieldKey.contains("handled") || fieldKey.contains("main_thread"))) {
                    if ("true".startsWith(valuePrefix, ignoreCase = true)) {
                        suggestions.add(QuerySuggestion("${fieldKey}:true", null, "${fieldKey}:true"))
                    }
                    if ("false".startsWith(valuePrefix, ignoreCase = true)) {
                        suggestions.add(QuerySuggestion("${fieldKey}:false", null, "${fieldKey}:false"))
                    }
                }
            }
        }
    }

    return suggestions.take(8) // Limit suggestions
}

private fun getCurrentToken(query: String): String {
    val lastSpaceIndex = query.lastIndexOf(' ')
    return if (lastSpaceIndex == -1) {
        query
    } else {
        query.substring(lastSpaceIndex + 1)
    }
}

private fun replaceCurrentToken(query: String, newToken: String): String {
    val lastSpaceIndex = query.lastIndexOf(' ')
    return if (lastSpaceIndex == -1) {
        newToken
    } else {
        query.substring(0, lastSpaceIndex + 1) + newToken
    }
}