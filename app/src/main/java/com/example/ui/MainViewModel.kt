package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountingRepository
import com.example.data.model.AccountingDocument
import com.example.data.model.BikLookupResult
import com.example.data.model.Counterparty
import com.example.data.model.CreateMode
import com.example.data.model.DocumentLineItem
import com.example.data.model.DocumentStatus
import com.example.data.model.DocumentType
import com.example.data.model.InnLookupResult
import com.example.data.model.OrganizationProfile
import com.example.data.remote.FnsEgrulService
import com.example.domain.DocumentCalculator
import com.example.pdf.AccountingPdfGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AppDestination {
    data object Home : AppDestination
    data class Editor(
        val createMode: CreateMode,
        val initialDoc: AccountingDocument
    ) : AppDestination
    data class Preview(val docId: String) : AppDestination
}

class MainViewModel(
    private val repository: AccountingRepository,
    private val egrulService: FnsEgrulService,
    private val currentUserId: String
) : ViewModel() {

    val organizationsState: StateFlow<UiState<List<OrganizationProfile>>> =
        repository.observeOrganizations(currentUserId)
            .map<List<OrganizationProfile>, UiState<List<OrganizationProfile>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing organizations", error)
                emit(UiState.Error(error.localizedMessage ?: "Не удалось загрузить организации"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val counterpartiesState: StateFlow<UiState<List<Counterparty>>> =
        repository.observeCounterparties(currentUserId)
            .map<List<Counterparty>, UiState<List<Counterparty>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing counterparties", error)
                emit(UiState.Error(error.localizedMessage ?: "Не удалось загрузить контрагентов"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val documentsState: StateFlow<UiState<List<AccountingDocument>>> =
        repository.observeDocuments(currentUserId)
            .map<List<AccountingDocument>, UiState<List<AccountingDocument>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing documents", error)
                emit(UiState.Error(error.localizedMessage ?: "Не удалось загрузить документы"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _destination = MutableStateFlow<AppDestination>(AppDestination.Home)
    val destination: StateFlow<AppDestination> = _destination.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _previewPdfFile = MutableStateFlow<File?>(null)
    val previewPdfFile: StateFlow<File?> = _previewPdfFile.asStateFlow()

    private val _previewBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val previewBitmaps: StateFlow<List<Bitmap>> = _previewBitmaps.asStateFlow()

    private val _isRenderingPdf = MutableStateFlow(false)
    val isRenderingPdf: StateFlow<Boolean> = _isRenderingPdf.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun navigateBackToHome() {
        _destination.value = AppDestination.Home
        _previewPdfFile.value = null
        _previewBitmaps.value = emptyList()
    }

    fun openNewDocument(
        mode: CreateMode = CreateMode.BOTH,
        prefilledBuyer: InnLookupResult? = null,
        prefilledCounterparty: Counterparty? = null
    ) {
        val orgs = (organizationsState.value as? UiState.Success)?.data.orEmpty()
        val defaultOrg = orgs.firstOrNull { it.defaultProfile } ?: orgs.firstOrNull()
        val docs = (documentsState.value as? UiState.Success)?.data.orEmpty()
        val nextNum = suggestNextNumber(docs)
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val defaultVat = defaultOrg?.defaultVatRate ?: 20

        val buyerName = prefilledCounterparty?.name ?: prefilledBuyer?.name ?: ""
        val buyerInn = prefilledCounterparty?.inn ?: prefilledBuyer?.inn ?: ""
        val buyerKpp = prefilledCounterparty?.kpp ?: prefilledBuyer?.kpp ?: ""
        val buyerOgrn = prefilledCounterparty?.ogrn ?: prefilledBuyer?.ogrn ?: ""
        val buyerAddress = prefilledCounterparty?.address ?: prefilledBuyer?.address ?: ""
        val buyerPhone = prefilledCounterparty?.phone ?: ""
        val buyerBank = prefilledCounterparty?.bankName ?: ""
        val buyerBik = prefilledCounterparty?.bik ?: ""
        val buyerRs = prefilledCounterparty?.checkingAccount ?: ""
        val buyerKs = prefilledCounterparty?.correspondentAccount ?: ""

        val draft = DocumentCalculator.recalculateDocument(
            AccountingDocument(
                docType = if (mode == CreateMode.TORG12) DocumentType.TORG12.name else DocumentType.INVOICE.name,
                docNumber = nextNum,
                docDate = today,
                basisDoc = "",
                paymentDoc = "",
                status = DocumentStatus.DRAFT.name,
                sellerName = defaultOrg?.name.orEmpty(),
                sellerInn = defaultOrg?.inn.orEmpty(),
                sellerKpp = defaultOrg?.kpp.orEmpty(),
                sellerOgrn = defaultOrg?.ogrn.orEmpty(),
                sellerAddress = defaultOrg?.address.orEmpty(),
                sellerPhone = defaultOrg?.phone.orEmpty(),
                sellerBankName = defaultOrg?.bankName.orEmpty(),
                sellerBik = defaultOrg?.bik.orEmpty(),
                sellerCheckingAccount = defaultOrg?.checkingAccount.orEmpty(),
                sellerCorrAccount = defaultOrg?.correspondentAccount.orEmpty(),
                sellerDirectorTitle = defaultOrg?.directorTitle ?: "Генеральный директор",
                sellerDirectorName = defaultOrg?.directorName.orEmpty(),
                sellerAccountantName = defaultOrg?.accountantName.orEmpty(),
                buyerName = buyerName,
                buyerInn = buyerInn,
                buyerKpp = buyerKpp,
                buyerOgrn = buyerOgrn,
                buyerAddress = buyerAddress,
                buyerPhone = buyerPhone,
                buyerBankName = buyerBank,
                buyerBik = buyerBik,
                buyerCheckingAccount = buyerRs,
                buyerCorrAccount = buyerKs,
                consignorInfo = "Он же",
                consigneeInfo = "Он же",
                items = listOf(
                    DocumentLineItem(
                        name = "",
                        unitCode = "796",
                        unitName = "шт",
                        quantity = 1.0,
                        priceWithoutVat = 0.0,
                        vatRate = defaultVat
                    )
                ),
                totalPlaces = 1,
                totalWeightKg = 0.0
            )
        )
        _destination.value = AppDestination.Editor(createMode = mode, initialDoc = draft)
    }

    fun openEditDocument(doc: AccountingDocument) {
        if (doc.resolvedStatus == DocumentStatus.ISSUED) {
            duplicateAsDraft(doc)
            return
        }
        val mode = if (doc.resolvedType == DocumentType.INVOICE) CreateMode.INVOICE else CreateMode.TORG12
        _destination.value = AppDestination.Editor(createMode = mode, initialDoc = doc)
    }

    fun duplicateAsDraft(doc: AccountingDocument) {
        val docs = (documentsState.value as? UiState.Success)?.data.orEmpty()
        val nextNum = suggestNextNumber(docs)
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val cloned = doc.copy(
            id = "",
            docNumber = nextNum,
            docDate = today,
            status = DocumentStatus.DRAFT.name,
            createdAtMillis = 0L,
            updatedAtMillis = 0L
        )
        val mode = if (doc.resolvedType == DocumentType.INVOICE) CreateMode.INVOICE else CreateMode.TORG12
        _destination.value = AppDestination.Editor(createMode = mode, initialDoc = cloned)
    }

    fun openPreview(context: Context, docId: String) {
        _previewPdfFile.value = null
        _previewBitmaps.value = emptyList()
        _destination.value = AppDestination.Preview(docId)
        val doc = (documentsState.value as? UiState.Success)?.data?.firstOrNull { it.id == docId }
        if (doc != null) {
            renderPdfForDocument(context, doc)
        }
    }

    fun renderPdfForDocument(context: Context, doc: AccountingDocument) {
        viewModelScope.launch {
            _isRenderingPdf.value = true
            try {
                val file = AccountingPdfGenerator.generatePdfFile(context, doc)
                val pages = AccountingPdfGenerator.renderPreviewPages(file)
                _previewPdfFile.value = file
                _previewBitmaps.value = pages
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate PDF", e)
                _snackbarMessage.value = "Ошибка генерации PDF: ${e.localizedMessage}"
            } finally {
                _isRenderingPdf.value = false
            }
        }
    }

    suspend fun lookupInn(inn: String): Result<InnLookupResult> = egrulService.lookupByInn(inn)

    suspend fun lookupBik(bik: String): Result<BikLookupResult> = egrulService.lookupByBik(bik)

    fun saveOrganization(org: OrganizationProfile, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val existing = (organizationsState.value as? UiState.Success)?.data.orEmpty()
            val finalOrg = if (existing.isEmpty()) org.copy(defaultProfile = true) else org
            val res = repository.saveOrganization(currentUserId, finalOrg)
            _isSaving.value = false
            res.onSuccess {
                _snackbarMessage.value = "Организация сохранена"
                onDone()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Ошибка сохранения организации"
            }
        }
    }

    fun deleteOrganization(orgId: String) {
        viewModelScope.launch {
            repository.deleteOrganization(currentUserId, orgId)
                .onSuccess { _snackbarMessage.value = "Организация удалена" }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Не удалось удалить организацию"
                }
        }
    }

    fun saveCounterparty(party: Counterparty, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val res = repository.saveCounterparty(currentUserId, party)
            _isSaving.value = false
            res.onSuccess {
                _snackbarMessage.value = "Контрагент сохранён"
                onDone()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Ошибка сохранения контрагента"
            }
        }
    }

    fun deleteCounterparty(partyId: String) {
        viewModelScope.launch {
            repository.deleteCounterparty(currentUserId, partyId)
                .onSuccess { _snackbarMessage.value = "Контрагент удалён" }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Не удалось удалить контрагента"
                }
        }
    }

    fun saveDocumentFromEditor(
        context: Context,
        doc: AccountingDocument,
        createMode: CreateMode,
        openPdfAfter: Boolean,
        saveBuyerAsCounterparty: Boolean,
        saveSellerAsOrganization: Boolean
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val calculated = DocumentCalculator.recalculateDocument(doc)

                if (saveBuyerAsCounterparty && calculated.buyerName.isNotBlank() && calculated.buyerInn.length in 10..12) {
                    val existingParties = (counterpartiesState.value as? UiState.Success)?.data.orEmpty()
                    val match = existingParties.firstOrNull { it.inn == calculated.buyerInn }
                    val party = Counterparty(
                        id = match?.id.orEmpty(),
                        name = calculated.buyerName,
                        inn = calculated.buyerInn,
                        kpp = calculated.buyerKpp,
                        ogrn = calculated.buyerOgrn,
                        address = calculated.buyerAddress,
                        phone = calculated.buyerPhone,
                        bankName = calculated.buyerBankName,
                        bik = calculated.buyerBik,
                        checkingAccount = calculated.buyerCheckingAccount,
                        correspondentAccount = calculated.buyerCorrAccount,
                        directorName = match?.directorName.orEmpty(),
                        createdAtMillis = match?.createdAtMillis ?: 0L
                    )
                    repository.saveCounterparty(currentUserId, party)
                }

                if (saveSellerAsOrganization && calculated.sellerName.isNotBlank() && calculated.sellerInn.length in 10..12) {
                    val existingOrgs = (organizationsState.value as? UiState.Success)?.data.orEmpty()
                    val match = existingOrgs.firstOrNull { it.inn == calculated.sellerInn }
                    val org = OrganizationProfile(
                        id = match?.id.orEmpty(),
                        name = calculated.sellerName,
                        inn = calculated.sellerInn,
                        kpp = calculated.sellerKpp,
                        ogrn = calculated.sellerOgrn,
                        address = calculated.sellerAddress,
                        phone = calculated.sellerPhone,
                        bankName = calculated.sellerBankName,
                        bik = calculated.sellerBik,
                        checkingAccount = calculated.sellerCheckingAccount,
                        correspondentAccount = calculated.sellerCorrAccount,
                        directorTitle = calculated.sellerDirectorTitle,
                        directorName = calculated.sellerDirectorName,
                        accountantName = calculated.sellerAccountantName,
                        defaultVatRate = calculated.items.firstOrNull()?.vatRate ?: 20,
                        defaultProfile = match?.defaultProfile ?: existingOrgs.isEmpty(),
                        createdAtMillis = match?.createdAtMillis ?: 0L
                    )
                    repository.saveOrganization(currentUserId, org)
                }

                if (doc.id.isBlank() && createMode == CreateMode.BOTH) {
                    val invoiceDoc = calculated.copy(docType = DocumentType.INVOICE.name)
                    val torgDoc = calculated.copy(docType = DocumentType.TORG12.name)
                    val r1 = repository.saveDocument(currentUserId, invoiceDoc, DocumentStatus.DRAFT)
                    val r2 = repository.saveDocument(currentUserId, torgDoc, DocumentStatus.DRAFT)
                    val firstId = r1.getOrNull() ?: r2.getOrNull()
                    if (r1.isSuccess && r2.isSuccess && firstId != null) {
                        _snackbarMessage.value = "Созданы Счёт-фактура и ТОРГ-12 № ${calculated.docNumber}"
                        if (openPdfAfter) {
                            openPreview(context, firstId)
                        } else {
                            navigateBackToHome()
                        }
                    } else {
                        val err = r1.exceptionOrNull() ?: r2.exceptionOrNull()
                        _snackbarMessage.value = err?.localizedMessage ?: "Ошибка сохранения документов"
                    }
                } else {
                    val targetType = when (createMode) {
                        CreateMode.TORG12 -> DocumentType.TORG12.name
                        CreateMode.INVOICE -> DocumentType.INVOICE.name
                        CreateMode.BOTH -> calculated.docType
                    }
                    val res = repository.saveDocument(
                        currentUserId,
                        calculated.copy(docType = targetType),
                        DocumentStatus.DRAFT
                    )
                    res.onSuccess { savedId ->
                        _snackbarMessage.value = "Документ № ${calculated.docNumber} сохранён"
                        if (openPdfAfter) {
                            openPreview(context, savedId)
                        } else {
                            navigateBackToHome()
                        }
                    }.onFailure { err ->
                        _snackbarMessage.value = err.localizedMessage ?: "Ошибка сохранения документа"
                    }
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun issueDocument(docId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val res = repository.issueDocument(currentUserId, docId)
            _isSaving.value = false
            res.onSuccess {
                _snackbarMessage.value = "Документ проведён"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Не удалось провести документ"
            }
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(currentUserId, docId)
                .onSuccess {
                    _snackbarMessage.value = "Документ удалён"
                    if ((_destination.value as? AppDestination.Preview)?.docId == docId) {
                        navigateBackToHome()
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Не удалось удалить документ"
                }
        }
    }

    private fun suggestNextNumber(docs: List<AccountingDocument>): String {
        val maxNum = docs.mapNotNull { it.docNumber.filter { ch -> ch.isDigit() }.toIntOrNull() }.maxOrNull() ?: 0
        return (maxNum + 1).toString()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
