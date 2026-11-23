package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AccountingDocument
import com.example.data.model.CreateMode
import com.example.data.model.DocumentStatus
import com.example.data.model.DocumentType
import com.example.data.model.InnLookupResult
import com.example.domain.RussianNumberToWords
import com.example.ui.UiState
import com.example.ui.components.DocumentStatusBadge
import com.example.ui.components.DocumentTypeBadge
import com.example.ui.components.InnSearchRow
import com.example.ui.theme.MonoNumberStyle
import kotlinx.coroutines.launch

enum class DocumentFilter(val label: String) {
    ALL("Все"),
    INVOICE("Счёт-фактура"),
    TORG12("ТОРГ-12"),
    DRAFT("Черновики"),
    ISSUED("Проведены")
}

@Composable
fun DocumentsScreen(
    documentsState: UiState<List<AccountingDocument>>,
    onQuickInnCreate: (InnLookupResult) -> Unit,
    onLookupInn: suspend (String) -> Result<InnLookupResult>,
    onCreateEmpty: (CreateMode) -> Unit,
    onOpenDocument: (AccountingDocument) -> Unit,
    onDuplicateDocument: (AccountingDocument) -> Unit,
    onDeleteDocument: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var quickInn by remember { mutableStateOf("") }
    var isLookingUp by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DocumentFilter.ALL) }
    var pendingDeleteDoc by remember { mutableStateOf<AccountingDocument?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Быстрое создание по ИНН",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Введите ИНН покупателя — мы загрузим реквизиты из ФНС и подготовим Счёт-фактуру и ТОРГ-12.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InnSearchRow(
                        inn = quickInn,
                        onInnChange = {
                            quickInn = it
                            lookupError = null
                        },
                        isLoading = isLookingUp,
                        onLookupClick = {
                            if (quickInn.length >= 10 && !isLookingUp) {
                                scope.launch {
                                    isLookingUp = true
                                    lookupError = null
                                    val res = onLookupInn(quickInn)
                                    isLookingUp = false
                                    res.onSuccess { info ->
                                        quickInn = ""
                                        onQuickInnCreate(info)
                                    }.onFailure { err ->
                                        lookupError = err.localizedMessage ?: "Не удалось найти организацию"
                                    }
                                }
                            }
                        },
                        label = "ИНН покупателя",
                        buttonText = "Создать",
                        testTagPrefix = "quick_inn",
                        statusMessage = lookupError,
                        isError = lookupError != null
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { onCreateEmpty(CreateMode.INVOICE) },
                            modifier = Modifier.testTag("quick_new_invoice_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Счёт-фактура")
                        }
                        TextButton(
                            onClick = { onCreateEmpty(CreateMode.TORG12) },
                            modifier = Modifier.testTag("quick_new_torg12_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ТОРГ-12")
                        }
                    }
                }
            }
        }

        when (documentsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = documentsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val allDocs = documentsState.data
                val invoiceCount = allDocs.count { it.resolvedType == DocumentType.INVOICE }
                val torgCount = allDocs.count { it.resolvedType == DocumentType.TORG12 }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricMiniCard(
                            title = "Всего",
                            value = allDocs.size.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            title = "СФ",
                            value = invoiceCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            title = "ТОРГ-12",
                            value = torgCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (allDocs.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск по номеру, покупателю или ИНН") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("documents_search_input")
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DocumentFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                val filtered = allDocs.filter { doc ->
                    val matchesFilter = when (selectedFilter) {
                        DocumentFilter.ALL -> true
                        DocumentFilter.INVOICE -> doc.resolvedType == DocumentType.INVOICE
                        DocumentFilter.TORG12 -> doc.resolvedType == DocumentType.TORG12
                        DocumentFilter.DRAFT -> doc.resolvedStatus == DocumentStatus.DRAFT
                        DocumentFilter.ISSUED -> doc.resolvedStatus == DocumentStatus.ISSUED
                    }
                    val q = searchQuery.trim().lowercase()
                    val matchesQuery = q.isEmpty() ||
                        doc.docNumber.lowercase().contains(q) ||
                        doc.buyerName.lowercase().contains(q) ||
                        doc.buyerInn.contains(q) ||
                        doc.sellerName.lowercase().contains(q)
                    matchesFilter && matchesQuery
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_docs),
                                    contentDescription = "Список документов пуст",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Text(
                                    text = if (allDocs.isEmpty()) "Документов пока нет" else "Ничего не найдено",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (allDocs.isEmpty()) {
                                        "Введите ИНН покупателя выше или нажмите «+» чтобы создать Счёт-фактуру и накладную ТОРГ-12."
                                    } else {
                                        "Попробуйте изменить строку поиска или фильтр."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { doc ->
                        DocumentListCard(
                            doc = doc,
                            onClick = { onOpenDocument(doc) },
                            onDuplicate = { onDuplicateDocument(doc) },
                            onDelete = { pendingDeleteDoc = doc }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    pendingDeleteDoc?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDoc = null },
            title = { Text("Удалить документ?") },
            text = {
                Text("${target.resolvedType.label} № ${target.docNumber} от ${target.docDate} будет удалён без возможности восстановления.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDocument(target.id)
                        pendingDeleteDoc = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDoc = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MonoNumberStyle.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun DocumentListCard(
    doc: AccountingDocument,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("document_card_${doc.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocumentTypeBadge(doc.resolvedType)
                    DocumentStatusBadge(doc.resolvedStatus)
                    Text(
                        text = "№ ${doc.docNumber}",
                        style = MonoNumberStyle.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.titleSmall.fontSize
                        )
                    )
                }
                Text(
                    text = doc.docDate,
                    style = MonoNumberStyle.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = doc.buyerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ИНН ${doc.buyerInn} • Позиций: ${doc.items.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${RussianNumberToWords.formatMoney(doc.totalWithVat)} ₽",
                        style = MonoNumberStyle.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "в т.ч. НДС ${RussianNumberToWords.formatMoney(doc.totalVat)} ₽",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = "Открыть PDF",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Дублировать документ",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Удалить документ",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
