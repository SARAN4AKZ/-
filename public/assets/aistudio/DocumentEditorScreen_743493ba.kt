package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountingDocument
import com.example.data.model.BikLookupResult
import com.example.data.model.Counterparty
import com.example.data.model.CreateMode
import com.example.data.model.DocumentLineItem
import com.example.data.model.InnLookupResult
import com.example.data.model.OrganizationProfile
import com.example.data.model.STANDARD_UNIT_PRESETS
import com.example.data.model.VatRate
import com.example.domain.DocumentCalculator
import com.example.domain.RussianNumberToWords
import com.example.ui.components.InnSearchRow
import com.example.ui.theme.MonoNumberStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    initialMode: CreateMode,
    initialDoc: AccountingDocument,
    organizations: List<OrganizationProfile>,
    counterparties: List<Counterparty>,
    isSaving: Boolean,
    onLookupInn: suspend (String) -> Result<InnLookupResult>,
    onLookupBik: suspend (String) -> Result<BikLookupResult>,
    onBack: () -> Unit,
    onSave: (
        doc: AccountingDocument,
        mode: CreateMode,
        openPdfAfter: Boolean,
        saveBuyer: Boolean,
        saveSeller: Boolean
    ) -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val isNew = initialDoc.id.isBlank()
    var createMode by remember { mutableStateOf(initialMode) }
    var doc by remember { mutableStateOf(DocumentCalculator.recalculateDocument(initialDoc)) }

    var sellerExpanded by remember { mutableStateOf(doc.sellerName.isBlank()) }
    var buyerBankExpanded by remember { mutableStateOf(false) }
    var cargoExpanded by remember { mutableStateOf(createMode != CreateMode.INVOICE) }

    var saveBuyerAsCounterparty by remember { mutableStateOf(true) }
    var saveSellerAsOrganization by remember { mutableStateOf(organizations.isEmpty()) }

    var sellerInnLoading by remember { mutableStateOf(false) }
    var sellerInnMsg by remember { mutableStateOf<String?>(null) }
    var sellerInnErr by remember { mutableStateOf(false) }

    var buyerInnLoading by remember { mutableStateOf(false) }
    var buyerInnMsg by remember { mutableStateOf<String?>(null) }
    var buyerInnErr by remember { mutableStateOf(false) }

    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var showPickOrgDialog by remember { mutableStateOf(false) }
    var showPickPartyDialog by remember { mutableStateOf(false) }

    val canSave = doc.docNumber.isNotBlank() &&
        doc.docDate.isNotBlank() &&
        doc.sellerName.isNotBlank() &&
        doc.sellerInn.length in 10..12 &&
        doc.buyerName.isNotBlank() &&
        doc.buyerInn.length in 10..12 &&
        doc.items.isNotEmpty() &&
        doc.items.all { it.name.isNotBlank() && it.quantity > 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNew) "Создание документа" else "Редактирование № ${doc.docNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Итого с НДС:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${RussianNumberToWords.formatMoney(doc.totalWithVat)} ₽",
                            style = MonoNumberStyle.copy(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSave(
                                    doc,
                                    createMode,
                                    false,
                                    saveBuyerAsCounterparty,
                                    saveSellerAsOrganization
                                )
                            },
                            enabled = canSave && !isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_draft_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Черновик")
                        }
                        Button(
                            onClick = {
                                onSave(
                                    doc,
                                    createMode,
                                    true,
                                    saveBuyerAsCounterparty,
                                    saveSellerAsOrganization
                                )
                            },
                            enabled = canSave && !isSaving,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("save_and_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isNew && createMode == CreateMode.BOTH) "Создать оба и PDF" else "Сохранить и PDF")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isNew) {
                item {
                    SectionCard(title = "Тип документа") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CreateMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = createMode == mode,
                                    onClick = { createMode = mode },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = createMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Document metadata
            item {
                SectionCard(title = "Реквизиты документа") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = doc.docNumber,
                            onValueChange = { doc = doc.copy(docNumber = it.take(50)) },
                            label = { Text("Номер *") },
                            singleLine = true,
                            textStyle = MonoNumberStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("doc_number_input")
                        )
                        OutlinedTextField(
                            value = doc.docDate,
                            onValueChange = { doc = doc.copy(docDate = it.take(20)) },
                            label = { Text("Дата *") },
                            placeholder = { Text("23.11.2026") },
                            singleLine = true,
                            textStyle = MonoNumberStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("doc_date_input")
                        )
                    }
                    OutlinedTextField(
                        value = doc.basisDoc,
                        onValueChange = { doc = doc.copy(basisDoc = it.take(200)) },
                        label = { Text("Основание (договор, счёт)") },
                        placeholder = { Text("Договор поставки № 15 от 01.11.2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (createMode != CreateMode.TORG12) {
                        OutlinedTextField(
                            value = doc.paymentDoc,
                            onValueChange = { doc = doc.copy(paymentDoc = it.take(200)) },
                            label = { Text("К платёжно-расчётному документу (для СФ)") },
                            placeholder = { Text("П/П № 42 от 20.11.2026") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Seller section
            item {
                SectionCard(
                    title = "Продавец / Поставщик",
                    trailingAction = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (organizations.isNotEmpty()) {
                                TextButton(onClick = { showPickOrgDialog = true }) {
                                    Text("Выбрать")
                                }
                            }
                            IconButton(onClick = { sellerExpanded = !sellerExpanded }) {
                                Icon(
                                    imageVector = if (sellerExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Развернуть продавца"
                                )
                            }
                        }
                    }
                ) {
                    if (!sellerExpanded && doc.sellerName.isNotBlank()) {
                        Text(
                            text = doc.sellerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = buildString {
                                append("ИНН ${doc.sellerInn}")
                                if (doc.sellerKpp.isNotBlank()) append(" • КПП ${doc.sellerKpp}")
                            },
                            style = MonoNumberStyle.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    } else {
                        InnSearchRow(
                            inn = doc.sellerInn,
                            onInnChange = {
                                doc = doc.copy(sellerInn = it)
                                sellerInnMsg = null
                            },
                            isLoading = sellerInnLoading,
                            onLookupClick = {
                                scope.launch {
                                    sellerInnLoading = true
                                    val res = onLookupInn(doc.sellerInn)
                                    sellerInnLoading = false
                                    res.onSuccess { info ->
                                        doc = doc.copy(
                                            sellerInn = info.inn,
                                            sellerName = info.name,
                                            sellerKpp = info.kpp,
                                            sellerOgrn = info.ogrn,
                                            sellerAddress = info.address.ifBlank { doc.sellerAddress },
                                            sellerDirectorTitle = info.directorTitle,
                                            sellerDirectorName = info.directorName.ifBlank { doc.sellerDirectorName }
                                        )
                                        sellerInnErr = false
                                        sellerInnMsg = "Продавец заполнен из ФНС"
                                    }.onFailure { err ->
                                        sellerInnErr = true
                                        sellerInnMsg = err.localizedMessage
                                    }
                                }
                            },
                            label = "ИНН продавца *",
                            testTagPrefix = "seller_inn",
                            statusMessage = sellerInnMsg,
                            isError = sellerInnErr
                        )
                        OutlinedTextField(
                            value = doc.sellerName,
                            onValueChange = { doc = doc.copy(sellerName = it.take(300)) },
                            label = { Text("Наименование продавца *") },
                            modifier = Modifier.fillMaxWidth().testTag("seller_name_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = doc.sellerKpp,
                                onValueChange = { doc = doc.copy(sellerKpp = it.filter { c -> c.isDigit() }.take(9)) },
                                label = { Text("КПП") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = doc.sellerOgrn,
                                onValueChange = { doc = doc.copy(sellerOgrn = it.filter { c -> c.isDigit() }.take(15)) },
                                label = { Text("ОГРН") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = doc.sellerAddress,
                            onValueChange = { doc = doc.copy(sellerAddress = it.take(500)) },
                            label = { Text("Юридический адрес") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = doc.sellerBik,
                                onValueChange = { doc = doc.copy(sellerBik = it.filter { c -> c.isDigit() }.take(9)) },
                                label = { Text("БИК банка") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        onLookupBik(doc.sellerBik).onSuccess { b ->
                                            doc = doc.copy(
                                                sellerBankName = b.bankName,
                                                sellerCorrAccount = b.correspondentAccount
                                            )
                                        }
                                    }
                                },
                                enabled = doc.sellerBik.length == 9
                            ) { Text("Найти банк") }
                        }
                        OutlinedTextField(
                            value = doc.sellerBankName,
                            onValueChange = { doc = doc.copy(sellerBankName = it.take(200)) },
                            label = { Text("Банк продавца") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = doc.sellerCheckingAccount,
                                onValueChange = { doc = doc.copy(sellerCheckingAccount = it.filter { c -> c.isDigit() }.take(20)) },
                                label = { Text("Расч. счёт (р/с)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = doc.sellerCorrAccount,
                                onValueChange = { doc = doc.copy(sellerCorrAccount = it.filter { c -> c.isDigit() }.take(20)) },
                                label = { Text("Корр. счёт (к/с)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = doc.sellerDirectorName,
                                onValueChange = { doc = doc.copy(sellerDirectorName = it.take(120)) },
                                label = { Text("Руководитель") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = doc.sellerAccountantName,
                                onValueChange = { doc = doc.copy(sellerAccountantName = it.take(120)) },
                                label = { Text("Главбух") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { saveSellerAsOrganization = !saveSellerAsOrganization }
                        ) {
                            Checkbox(
                                checked = saveSellerAsOrganization,
                                onCheckedChange = { saveSellerAsOrganization = it }
                            )
                            Text("Сохранить в «Мои фирмы»", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Buyer section
            item {
                SectionCard(
                    title = "Покупатель / Плательщик",
                    trailingAction = {
                        if (counterparties.isNotEmpty()) {
                            TextButton(onClick = { showPickPartyDialog = true }) {
                                Text("Из справочника")
                            }
                        }
                    }
                ) {
                    InnSearchRow(
                        inn = doc.buyerInn,
                        onInnChange = {
                            doc = doc.copy(buyerInn = it)
                            buyerInnMsg = null
                        },
                        isLoading = buyerInnLoading,
                        onLookupClick = {
                            scope.launch {
                                buyerInnLoading = true
                                val res = onLookupInn(doc.buyerInn)
                                buyerInnLoading = false
                                res.onSuccess { info ->
                                    doc = doc.copy(
                                        buyerInn = info.inn,
                                        buyerName = info.name,
                                        buyerKpp = info.kpp,
                                        buyerOgrn = info.ogrn,
                                        buyerAddress = info.address.ifBlank { doc.buyerAddress }
                                    )
                                    buyerInnErr = false
                                    buyerInnMsg = "Покупатель загружен из ЕГРЮЛ/ЕГРИП"
                                }.onFailure { err ->
                                    buyerInnErr = true
                                    buyerInnMsg = err.localizedMessage
                                }
                            }
                        },
                        label = "ИНН покупателя *",
                        testTagPrefix = "buyer_inn",
                        statusMessage = buyerInnMsg,
                        isError = buyerInnErr
                    )
                    OutlinedTextField(
                        value = doc.buyerName,
                        onValueChange = { doc = doc.copy(buyerName = it.take(300)) },
                        label = { Text("Наименование покупателя *") },
                        modifier = Modifier.fillMaxWidth().testTag("buyer_name_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = doc.buyerKpp,
                            onValueChange = { doc = doc.copy(buyerKpp = it.filter { c -> c.isDigit() }.take(9)) },
                            label = { Text("КПП") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = doc.buyerOgrn,
                            onValueChange = { doc = doc.copy(buyerOgrn = it.filter { c -> c.isDigit() }.take(15)) },
                            label = { Text("ОГРН") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = doc.buyerAddress,
                        onValueChange = { doc = doc.copy(buyerAddress = it.take(500)) },
                        label = { Text("Адрес покупателя") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { buyerBankExpanded = !buyerBankExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Банковские реквизиты и телефон покупателя",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Icon(
                            imageVector = if (buyerBankExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (buyerBankExpanded) {
                        OutlinedTextField(
                            value = doc.buyerPhone,
                            onValueChange = { doc = doc.copy(buyerPhone = it.take(40)) },
                            label = { Text("Телефон") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = doc.buyerBik,
                                onValueChange = { doc = doc.copy(buyerBik = it.filter { c -> c.isDigit() }.take(9)) },
                                label = { Text("БИК") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        onLookupBik(doc.buyerBik).onSuccess { b ->
                                            doc = doc.copy(
                                                buyerBankName = b.bankName,
                                                buyerCorrAccount = b.correspondentAccount
                                            )
                                        }
                                    }
                                },
                                enabled = doc.buyerBik.length == 9
                            ) { Text("Найти банк") }
                        }
                        OutlinedTextField(
                            value = doc.buyerBankName,
                            onValueChange = { doc = doc.copy(buyerBankName = it.take(200)) },
                            label = { Text("Банк") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = doc.buyerCheckingAccount,
                                onValueChange = { doc = doc.copy(buyerCheckingAccount = it.filter { c -> c.isDigit() }.take(20)) },
                                label = { Text("р/с") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = doc.buyerCorrAccount,
                                onValueChange = { doc = doc.copy(buyerCorrAccount = it.filter { c -> c.isDigit() }.take(20)) },
                                label = { Text("к/с") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { saveBuyerAsCounterparty = !saveBuyerAsCounterparty }
                    ) {
                        Checkbox(
                            checked = saveBuyerAsCounterparty,
                            onCheckedChange = { saveBuyerAsCounterparty = it }
                        )
                        Text("Сохранить в «Контрагенты»", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Cargo & Consignor/Consignee
            item {
                SectionCard(
                    title = "Грузоотправитель и доставка",
                    trailingAction = {
                        IconButton(onClick = { cargoExpanded = !cargoExpanded }) {
                            Icon(
                                imageVector = if (cargoExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Развернуть доставку"
                            )
                        }
                    }
                ) {
                    if (cargoExpanded) {
                        OutlinedTextField(
                            value = doc.consignorInfo,
                            onValueChange = { doc = doc.copy(consignorInfo = it.take(600)) },
                            label = { Text("Грузоотправитель («Он же» или адрес)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = doc.consigneeInfo,
                            onValueChange = { doc = doc.copy(consigneeInfo = it.take(600)) },
                            label = { Text("Грузополучатель («Он же» или адрес)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (createMode != CreateMode.INVOICE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = doc.totalPlaces.toString(),
                                    onValueChange = { raw ->
                                        val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                                        doc = doc.copy(totalPlaces = parsed)
                                    },
                                    label = { Text("Мест, шт (ТОРГ-12)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = if (doc.totalWeightKg == 0.0) "" else doc.totalWeightKg.toString(),
                                    onValueChange = { raw ->
                                        val clean = raw.replace(',', '.')
                                        val parsed = clean.toDoubleOrNull() ?: 0.0
                                        doc = doc.copy(totalWeightKg = parsed)
                                    },
                                    label = { Text("Масса брутто, кг") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Грузоотправитель: ${doc.consignorInfo} • Грузополучатель: ${doc.consigneeInfo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Line items header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Товары и услуги (${doc.items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            val defaultVat = doc.items.lastOrNull()?.vatRate ?: 20
                            val updated = doc.items + DocumentLineItem(vatRate = defaultVat)
                            doc = DocumentCalculator.recalculateDocument(doc.copy(items = updated))
                            editingItemIndex = updated.lastIndex
                        },
                        enabled = doc.items.size < 50,
                        modifier = Modifier.testTag("add_line_item_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Позиция")
                    }
                }
            }

            itemsIndexed(doc.items) { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingItemIndex = index }
                        .testTag("line_item_card_$index"),
                    shape = RoundedCornerShape(14.dp),
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
                            Text(
                                text = item.name.ifBlank { "Позиция ${index + 1} (нажмите для заполнения)" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.name.isBlank()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(onClick = { editingItemIndex = index }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Редактировать позицию")
                                }
                                if (doc.items.size > 1) {
                                    IconButton(onClick = {
                                        val nextItems = doc.items.toMutableList().apply { removeAt(index) }
                                        doc = DocumentCalculator.recalculateDocument(doc.copy(items = nextItems))
                                    }) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Удалить позицию",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${RussianNumberToWords.formatQuantity(item.quantity)} ${item.unitName} × ${RussianNumberToWords.formatMoney(item.priceWithoutVat)} ₽ • НДС ${VatRate.fromPercent(item.vatRate).label}",
                                style = MonoNumberStyle.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "${RussianNumberToWords.formatMoney(item.totalWithVat)} ₽",
                                style = MonoNumberStyle.copy(
                                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // Summary card
            item {
                SectionCard(title = "Итоги по документу") {
                    SummaryRow("Сумма без НДС:", "${RussianNumberToWords.formatMoney(doc.subtotalWithoutVat)} ₽")
                    SummaryRow("НДС всего:", "${RussianNumberToWords.formatMoney(doc.totalVat)} ₽")
                    SummaryRow("Всего к оплате:", "${RussianNumberToWords.formatMoney(doc.totalWithVat)} ₽", bold = true)
                    Text(
                        text = doc.amountInWords,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    editingItemIndex?.let { idx ->
        if (idx in doc.items.indices) {
            LineItemEditorDialog(
                initial = doc.items[idx],
                onDismiss = { editingItemIndex = null },
                onSave = { updatedItem ->
                    val nextList = doc.items.toMutableList()
                    nextList[idx] = updatedItem
                    doc = DocumentCalculator.recalculateDocument(doc.copy(items = nextList))
                    editingItemIndex = null
                }
            )
        }
    }

    if (showPickOrgDialog) {
        AlertDialog(
            onDismissRequest = { showPickOrgDialog = false },
            title = { Text("Выберите организацию") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    organizations.forEach { org ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    doc = doc.copy(
                                        sellerName = org.name,
                                        sellerInn = org.inn,
                                        sellerKpp = org.kpp,
                                        sellerOgrn = org.ogrn,
                                        sellerAddress = org.address,
                                        sellerPhone = org.phone,
                                        sellerBankName = org.bankName,
                                        sellerBik = org.bik,
                                        sellerCheckingAccount = org.checkingAccount,
                                        sellerCorrAccount = org.correspondentAccount,
                                        sellerDirectorTitle = org.directorTitle,
                                        sellerDirectorName = org.directorName,
                                        sellerAccountantName = org.accountantName
                                    )
                                    sellerExpanded = false
                                    showPickOrgDialog = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(org.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "ИНН ${org.inn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickOrgDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (showPickPartyDialog) {
        AlertDialog(
            onDismissRequest = { showPickPartyDialog = false },
            title = { Text("Выберите покупателя") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    counterparties.forEach { party ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    doc = doc.copy(
                                        buyerName = party.name,
                                        buyerInn = party.inn,
                                        buyerKpp = party.kpp,
                                        buyerOgrn = party.ogrn,
                                        buyerAddress = party.address,
                                        buyerPhone = party.phone,
                                        buyerBankName = party.bankName,
                                        buyerBik = party.bik,
                                        buyerCheckingAccount = party.checkingAccount,
                                        buyerCorrAccount = party.correspondentAccount
                                    )
                                    showPickPartyDialog = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(party.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "ИНН ${party.inn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickPartyDialog = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                trailingAction?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MonoNumberStyle.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (bold) MaterialTheme.typography.titleMedium.fontSize else MaterialTheme.typography.bodyMedium.fontSize
            )
        )
    }
}

@Composable
private fun LineItemEditorDialog(
    initial: DocumentLineItem,
    onDismiss: () -> Unit,
    onSave: (DocumentLineItem) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var unitCode by remember { mutableStateOf(initial.unitCode) }
    var unitName by remember { mutableStateOf(initial.unitName) }
    var qtyText by remember { mutableStateOf(if (initial.quantity == 0.0) "1" else initial.quantity.toString().removeSuffix(".0")) }
    var priceText by remember { mutableStateOf(if (initial.priceWithoutVat == 0.0) "" else initial.priceWithoutVat.toString()) }
    var vatRate by remember { mutableIntStateOf(initial.vatRate) }
    var countryCode by remember { mutableStateOf(initial.countryCode) }
    var countryName by remember { mutableStateOf(initial.countryName) }
    var customsDecl by remember { mutableStateOf(initial.customsDecl) }

    val parsedQty = qtyText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedPrice = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val preview = remember(name, unitCode, unitName, parsedQty, parsedPrice, vatRate, countryCode, countryName, customsDecl) {
        DocumentCalculator.recalculateLineItem(
            name = name,
            unitCode = unitCode,
            unitName = unitName,
            quantity = parsedQty,
            priceWithoutVat = parsedPrice,
            vatRate = vatRate,
            countryCode = countryCode,
            countryName = countryName,
            customsDecl = customsDecl
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Позиция товара / услуги") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(250) },
                    label = { Text("Наименование *") },
                    placeholder = { Text("Например: Консультационные услуги") },
                    modifier = Modifier.fillMaxWidth().testTag("item_name_input")
                )
                Text("Единица измерения (ОКЕИ)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STANDARD_UNIT_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = unitName == preset.name,
                            onClick = {
                                unitName = preset.name
                                unitCode = preset.code
                            },
                            label = { Text(preset.name) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it.take(20) },
                        label = { Text("Ед. изм.") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitCode,
                        onValueChange = { unitCode = it.take(10) },
                        label = { Text("Код ОКЕИ") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Количество *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("item_qty_input")
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Цена без НДС *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("item_price_input")
                    )
                }
                Text("Ставка НДС", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VatRate.entries.forEach { rate ->
                        FilterChip(
                            selected = vatRate == rate.percent,
                            onClick = { vatRate = rate.percent },
                            label = { Text(rate.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.take(10) },
                        label = { Text("Код страны") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = countryName,
                        onValueChange = { countryName = it.take(60) },
                        label = { Text("Страна") },
                        modifier = Modifier.weight(1.5f)
                    )
                }
                OutlinedTextField(
                    value = customsDecl,
                    onValueChange = { customsDecl = it.take(60) },
                    label = { Text("Рег. номер ДТ (для импорта)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "НДС: ${RussianNumberToWords.formatMoney(preview.vatAmount)} ₽",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Итого с НДС: ${RussianNumberToWords.formatMoney(preview.totalWithVat)} ₽",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(preview) },
                enabled = name.isNotBlank() && parsedQty > 0.0,
                modifier = Modifier.testTag("save_line_item_button")
            ) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
