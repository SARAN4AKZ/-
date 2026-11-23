package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BikLookupResult
import com.example.data.model.Counterparty
import com.example.data.model.InnLookupResult
import com.example.data.model.OrganizationProfile
import com.example.data.model.VatRate
import com.example.ui.UiState
import com.example.ui.components.InnSearchRow
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.MonoNumberStyle
import kotlinx.coroutines.launch

@Composable
fun CounterpartiesScreen(
    counterpartiesState: UiState<List<Counterparty>>,
    onLookupInn: suspend (String) -> Result<InnLookupResult>,
    onLookupBik: suspend (String) -> Result<BikLookupResult>,
    onSaveCounterparty: (Counterparty, () -> Unit) -> Unit,
    onDeleteCounterparty: (String) -> Unit,
    onCreateDocForCounterparty: (Counterparty) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var editingParty by remember { mutableStateOf<Counterparty?>(null) }
    var deletingParty by remember { mutableStateOf<Counterparty?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Справочник контрагентов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Покупатели и грузополучатели с поиском по ИНН",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { editingParty = Counterparty() },
                    modifier = Modifier.testTag("add_counterparty_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }

        when (counterpartiesState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(counterpartiesState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val list = counterpartiesState.data
                if (list.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Поиск по названию или ИНН") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val filtered = list.filter { p ->
                    val q = search.trim().lowercase()
                    q.isEmpty() || p.name.lowercase().contains(q) || p.inn.contains(q)
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Список контрагентов пуст",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Нажмите «Добавить», введите ИНН покупателя — реквизиты загрузятся из ЕГРЮЛ автоматически.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { party ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = party.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append("ИНН ${party.inn}")
                                                if (party.kpp.isNotBlank()) append(" • КПП ${party.kpp}")
                                            },
                                            style = MonoNumberStyle.copy(
                                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingParty = party }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                                        }
                                        IconButton(onClick = { deletingParty = party }) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = "Удалить",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                if (party.address.isNotBlank()) {
                                    Text(
                                        text = party.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onCreateDocForCounterparty(party) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Создать СФ и ТОРГ-12")
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    editingParty?.let { current ->
        CounterpartyEditorDialog(
            initial = current,
            onDismiss = { editingParty = null },
            onLookupInn = onLookupInn,
            onLookupBik = onLookupBik,
            onSave = { updated ->
                onSaveCounterparty(updated) { editingParty = null }
            }
        )
    }

    deletingParty?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingParty = null },
            title = { Text("Удалить контрагента?") },
            text = { Text("${target.name} (ИНН ${target.inn}) будет удалён из справочника.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCounterparty(target.id)
                    deletingParty = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingParty = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun OrganizationsScreen(
    organizationsState: UiState<List<OrganizationProfile>>,
    onLookupInn: suspend (String) -> Result<InnLookupResult>,
    onLookupBik: suspend (String) -> Result<BikLookupResult>,
    onSaveOrganization: (OrganizationProfile, () -> Unit) -> Unit,
    onDeleteOrganization: (String) -> Unit
) {
    var editingOrg by remember { mutableStateOf<OrganizationProfile?>(null) }
    var deletingOrg by remember { mutableStateOf<OrganizationProfile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Мои организации",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Реквизиты продавца, банка и подписантов по умолчанию",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { editingOrg = OrganizationProfile() },
                    modifier = Modifier.testTag("add_org_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }

        when (organizationsState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item { Text(organizationsState.message, color = MaterialTheme.colorScheme.error) }
            }
            is UiState.Success -> {
                val orgs = organizationsState.data
                if (orgs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Добавьте вашу организацию или ИП",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Введите ваш ИНН — данные подтянутся из ФНС, и реквизиты продавца будут подставляться в каждый новый документ.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(orgs, key = { it.id }) { org ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingOrg = org },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (org.defaultProfile) {
                                            Surface(
                                                color = LocalLedgerColors.current.successContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Star,
                                                        contentDescription = null,
                                                        tint = LocalLedgerColors.current.onSuccessContainer,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        "Основная",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = LocalLedgerColors.current.onSuccessContainer
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = org.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingOrg = org }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                                        }
                                        IconButton(onClick = { deletingOrg = org }) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = "Удалить",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append("ИНН ${org.inn}")
                                        if (org.kpp.isNotBlank()) append(" • КПП ${org.kpp}")
                                        append(" • НДС: ${VatRate.fromPercent(org.defaultVatRate).label}")
                                    },
                                    style = MonoNumberStyle.copy(
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                if (org.bankName.isNotBlank()) {
                                    Text(
                                        text = "${org.bankName} • р/с ${org.checkingAccount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    editingOrg?.let { current ->
        OrganizationEditorDialog(
            initial = current,
            onDismiss = { editingOrg = null },
            onLookupInn = onLookupInn,
            onLookupBik = onLookupBik,
            onSave = { updated ->
                onSaveOrganization(updated) { editingOrg = null }
            }
        )
    }

    deletingOrg?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingOrg = null },
            title = { Text("Удалить организацию?") },
            text = { Text("${target.name} (ИНН ${target.inn}) будет удалена.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteOrganization(target.id)
                    deletingOrg = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingOrg = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun CounterpartyEditorDialog(
    initial: Counterparty,
    onDismiss: () -> Unit,
    onLookupInn: suspend (String) -> Result<InnLookupResult>,
    onLookupBik: suspend (String) -> Result<BikLookupResult>,
    onSave: (Counterparty) -> Unit
) {
    val scope = rememberCoroutineScope()
    var inn by remember { mutableStateOf(initial.inn) }
    var name by remember { mutableStateOf(initial.name) }
    var kpp by remember { mutableStateOf(initial.kpp) }
    var ogrn by remember { mutableStateOf(initial.ogrn) }
    var address by remember { mutableStateOf(initial.address) }
    var phone by remember { mutableStateOf(initial.phone) }
    var bik by remember { mutableStateOf(initial.bik) }
    var bankName by remember { mutableStateOf(initial.bankName) }
    var rs by remember { mutableStateOf(initial.checkingAccount) }
    var ks by remember { mutableStateOf(initial.correspondentAccount) }
    var directorName by remember { mutableStateOf(initial.directorName) }

    var isLookingInn by remember { mutableStateOf(false) }
    var innMsg by remember { mutableStateOf<String?>(null) }
    var innErr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Новый контрагент" else "Редактировать контрагента") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InnSearchRow(
                    inn = inn,
                    onInnChange = { inn = it; innMsg = null },
                    isLoading = isLookingInn,
                    onLookupClick = {
                        scope.launch {
                            isLookingInn = true
                            val res = onLookupInn(inn)
                            isLookingInn = false
                            res.onSuccess { info ->
                                inn = info.inn
                                name = info.name
                                kpp = info.kpp
                                ogrn = info.ogrn
                                if (info.address.isNotBlank()) address = info.address
                                if (info.directorName.isNotBlank()) directorName = info.directorName
                                innErr = false
                                innMsg = "Заполнено из ЕГРЮЛ/ЕГРИП"
                            }.onFailure { err ->
                                innErr = true
                                innMsg = err.localizedMessage
                            }
                        }
                    },
                    testTagPrefix = "dialog_party_inn",
                    statusMessage = innMsg,
                    isError = innErr
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Наименование *") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kpp,
                        onValueChange = { kpp = it.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("КПП") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ogrn,
                        onValueChange = { ogrn = it.filter { c -> c.isDigit() }.take(15) },
                        label = { Text("ОГРН") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Юридический адрес") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bik,
                        onValueChange = { bik = it.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("БИК") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                onLookupBik(bik).onSuccess { b ->
                                    bankName = b.bankName
                                    ks = b.correspondentAccount
                                }
                            }
                        },
                        enabled = bik.length == 9
                    ) { Text("Найти банк") }
                }
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Банк") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rs,
                    onValueChange = { rs = it.filter { c -> c.isDigit() }.take(20) },
                    label = { Text("Расчётный счёт (р/с)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ks,
                    onValueChange = { ks = it.filter { c -> c.isDigit() }.take(20) },
                    label = { Text("Корр. счёт (к/с)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name,
                            inn = inn,
                            kpp = kpp,
                            ogrn = ogrn,
                            address = address,
                            phone = phone,
                            bik = bik,
                            bankName = bankName,
                            checkingAccount = rs,
                            correspondentAccount = ks,
                            directorName = directorName
                        )
                    )
                },
                enabled = name.isNotBlank() && inn.length in 10..12
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun OrganizationEditorDialog(
    initial: OrganizationProfile,
    onDismiss: () -> Unit,
    onLookupInn: suspend (String) -> Result<InnLookupResult>,
    onLookupBik: suspend (String) -> Result<BikLookupResult>,
    onSave: (OrganizationProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var inn by remember { mutableStateOf(initial.inn) }
    var name by remember { mutableStateOf(initial.name) }
    var kpp by remember { mutableStateOf(initial.kpp) }
    var ogrn by remember { mutableStateOf(initial.ogrn) }
    var address by remember { mutableStateOf(initial.address) }
    var phone by remember { mutableStateOf(initial.phone) }
    var bik by remember { mutableStateOf(initial.bik) }
    var bankName by remember { mutableStateOf(initial.bankName) }
    var rs by remember { mutableStateOf(initial.checkingAccount) }
    var ks by remember { mutableStateOf(initial.correspondentAccount) }
    var directorTitle by remember { mutableStateOf(initial.directorTitle) }
    var directorName by remember { mutableStateOf(initial.directorName) }
    var accountantName by remember { mutableStateOf(initial.accountantName) }
    var defaultVat by remember { mutableIntStateOf(initial.defaultVatRate) }
    var isDefault by remember { mutableStateOf(initial.defaultProfile) }

    var isLookingInn by remember { mutableStateOf(false) }
    var innMsg by remember { mutableStateOf<String?>(null) }
    var innErr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Моя организация / ИП" else "Редактировать фирму") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InnSearchRow(
                    inn = inn,
                    onInnChange = { inn = it; innMsg = null },
                    isLoading = isLookingInn,
                    onLookupClick = {
                        scope.launch {
                            isLookingInn = true
                            val res = onLookupInn(inn)
                            isLookingInn = false
                            res.onSuccess { info ->
                                inn = info.inn
                                name = info.name
                                kpp = info.kpp
                                ogrn = info.ogrn
                                if (info.address.isNotBlank()) address = info.address
                                if (info.directorTitle.isNotBlank()) directorTitle = info.directorTitle
                                if (info.directorName.isNotBlank()) directorName = info.directorName
                                innErr = false
                                innMsg = "Реквизиты загружены из ФНС"
                            }.onFailure { err ->
                                innErr = true
                                innMsg = err.localizedMessage
                            }
                        }
                    },
                    testTagPrefix = "dialog_org_inn",
                    statusMessage = innMsg,
                    isError = innErr
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Наименование *") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kpp,
                        onValueChange = { kpp = it.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("КПП") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ogrn,
                        onValueChange = { ogrn = it.filter { c -> c.isDigit() }.take(15) },
                        label = { Text("ОГРН") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bik,
                        onValueChange = { bik = it.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("БИК") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                onLookupBik(bik).onSuccess { b ->
                                    bankName = b.bankName
                                    ks = b.correspondentAccount
                                }
                            }
                        },
                        enabled = bik.length == 9
                    ) { Text("Найти банк") }
                }
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Банк") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rs,
                    onValueChange = { rs = it.filter { c -> c.isDigit() }.take(20) },
                    label = { Text("Расчётный счёт (р/с)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ks,
                    onValueChange = { ks = it.filter { c -> c.isDigit() }.take(20) },
                    label = { Text("Корр. счёт (к/с)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = directorTitle,
                    onValueChange = { directorTitle = it },
                    label = { Text("Должность руководителя") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = directorName,
                    onValueChange = { directorName = it },
                    label = { Text("ФИО руководителя") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountantName,
                    onValueChange = { accountantName = it },
                    label = { Text("Главный бухгалтер") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Ставка НДС по умолчанию", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VatRate.entries.forEach { rate ->
                        FilterChip(
                            selected = defaultVat == rate.percent,
                            onClick = { defaultVat = rate.percent },
                            label = { Text(rate.label) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isDefault = !isDefault }
                ) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text("Использовать по умолчанию", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name,
                            inn = inn,
                            kpp = kpp,
                            ogrn = ogrn,
                            address = address,
                            phone = phone,
                            bik = bik,
                            bankName = bankName,
                            checkingAccount = rs,
                            correspondentAccount = ks,
                            directorTitle = directorTitle,
                            directorName = directorName,
                            accountantName = accountantName,
                            defaultVatRate = defaultVat,
                            defaultProfile = isDefault
                        )
                    )
                },
                enabled = name.isNotBlank() && inn.length in 10..12
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
