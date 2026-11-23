package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AccountingRepository
import com.example.data.local.AppDatabase
import com.example.data.local.LocalAuthRepository
import com.example.data.model.CreateMode
import com.example.data.remote.FnsEgrulService
import com.example.ui.AppDestination
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CounterpartiesScreen
import com.example.ui.screens.DocumentEditorScreen
import com.example.ui.screens.DocumentPreviewScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.OrganizationsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getInstance(context) }
    val authRepository = remember(context, database) {
        LocalAuthRepository(context.applicationContext, database.userDao())
    }

    val currentSession by authRepository.currentSession.collectAsStateWithLifecycle(initialValue = null)
    val hasRegisteredUsers by authRepository.hasRegisteredUsers.collectAsStateWithLifecycle(initialValue = true)

    val session = currentSession
    if (session == null) {
        AuthScreen(
            authRepository = authRepository,
            hasRegisteredUsers = hasRegisteredUsers
        )
    } else {
        key(session.username) {
            AuthenticatedRoot(
                database = database,
                currentUserId = session.username,
                displayName = session.displayName,
                onSignOut = {
                    scope.launch { authRepository.logout() }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedRoot(
    database: AppDatabase,
    currentUserId: String,
    displayName: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = AccountingRepository(database),
                    egrulService = FnsEgrulService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val organizationsState by viewModel.organizationsState.collectAsStateWithLifecycle()
    val counterpartiesState by viewModel.counterpartiesState.collectAsStateWithLifecycle()
    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val previewPdfFile by viewModel.previewPdfFile.collectAsStateWithLifecycle()
    val previewBitmaps by viewModel.previewBitmaps.collectAsStateWithLifecycle()
    val isRenderingPdf by viewModel.isRenderingPdf.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    when (val dest = destination) {
        is AppDestination.Editor -> {
            val orgs = (organizationsState as? UiState.Success)?.data.orEmpty()
            val parties = (counterpartiesState as? UiState.Success)?.data.orEmpty()
            DocumentEditorScreen(
                initialMode = dest.createMode,
                initialDoc = dest.initialDoc,
                organizations = orgs,
                counterparties = parties,
                isSaving = isSaving,
                onLookupInn = { viewModel.lookupInn(it) },
                onLookupBik = { viewModel.lookupBik(it) },
                onBack = { viewModel.navigateBackToHome() },
                onSave = { doc, mode, openPdf, saveBuyer, saveSeller ->
                    viewModel.saveDocumentFromEditor(
                        context = context,
                        doc = doc,
                        createMode = mode,
                        openPdfAfter = openPdf,
                        saveBuyerAsCounterparty = saveBuyer,
                        saveSellerAsOrganization = saveSeller
                    )
                }
            )
        }

        is AppDestination.Preview -> {
            val docs = (documentsState as? UiState.Success)?.data.orEmpty()
            val doc = docs.firstOrNull { it.id == dest.docId }
            DocumentPreviewScreen(
                document = doc,
                pdfFile = previewPdfFile,
                previewBitmaps = previewBitmaps,
                isRendering = isRenderingPdf,
                isSaving = isSaving,
                onRenderPdf = { viewModel.renderPdfForDocument(context, it) },
                onBack = { viewModel.navigateBackToHome() },
                onEdit = { viewModel.openEditDocument(it) },
                onDuplicate = { viewModel.duplicateAsDraft(it) },
                onIssue = { viewModel.issueDocument(it) },
                onDelete = { viewModel.deleteDocument(it) },
                onMessage = { viewModel.showMessage(it) }
            )
        }

        AppDestination.Home -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                val tabs = listOf(
                    Triple("Документы", Icons.Filled.Description, Icons.Outlined.Description),
                    Triple("Контрагенты", Icons.Filled.People, Icons.Outlined.People),
                    Triple("Мои фирмы", Icons.Filled.Business, Icons.Outlined.Business)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            header = {
                                FloatingActionButton(
                                    onClick = { viewModel.openNewDocument(CreateMode.BOTH) },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .testTag("fab_create_document")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Создать документ")
                                }
                            }
                        ) {
                            tabs.forEachIndexed { idx, (label, filled, outlined) ->
                                NavigationRailItem(
                                    selected = selectedTab == idx,
                                    onClick = { viewModel.selectTab(idx) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == idx) filled else outlined,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = "Первичка Про",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (displayName.isNotBlank()) {
                                            Text(
                                                text = "Пользователь: $displayName",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = onSignOut,
                                        modifier = Modifier.testTag("sign_out_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = "Выйти из аккаунта"
                                        )
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            if (!useRail && selectedTab == 0) {
                                FloatingActionButton(
                                    onClick = { viewModel.openNewDocument(CreateMode.BOTH) },
                                    modifier = Modifier.testTag("fab_create_document")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Создать документ")
                                }
                            }
                        },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar {
                                    tabs.forEachIndexed { idx, (label, filled, outlined) ->
                                        NavigationBarItem(
                                            selected = selectedTab == idx,
                                            onClick = { viewModel.selectTab(idx) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selectedTab == idx) filled else outlined,
                                                    contentDescription = label
                                                )
                                            },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("nav_tab_$idx")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 760.dp)
                            ) {
                                when (selectedTab) {
                                    0 -> DocumentsScreen(
                                        documentsState = documentsState,
                                        onQuickInnCreate = { info ->
                                            viewModel.openNewDocument(
                                                mode = CreateMode.BOTH,
                                                prefilledBuyer = info
                                            )
                                        },
                                        onLookupInn = { viewModel.lookupInn(it) },
                                        onCreateEmpty = { mode -> viewModel.openNewDocument(mode) },
                                        onOpenDocument = { doc -> viewModel.openPreview(context, doc.id) },
                                        onDuplicateDocument = { doc -> viewModel.duplicateAsDraft(doc) },
                                        onDeleteDocument = { docId -> viewModel.deleteDocument(docId) }
                                    )

                                    1 -> CounterpartiesScreen(
                                        counterpartiesState = counterpartiesState,
                                        onLookupInn = { viewModel.lookupInn(it) },
                                        onLookupBik = { viewModel.lookupBik(it) },
                                        onSaveCounterparty = { party, onDone ->
                                            viewModel.saveCounterparty(party, onDone)
                                        },
                                        onDeleteCounterparty = { id -> viewModel.deleteCounterparty(id) },
                                        onCreateDocForCounterparty = { party ->
                                            viewModel.openNewDocument(
                                                mode = CreateMode.BOTH,
                                                prefilledCounterparty = party
                                            )
                                        }
                                    )

                                    2 -> OrganizationsScreen(
                                        organizationsState = organizationsState,
                                        onLookupInn = { viewModel.lookupInn(it) },
                                        onLookupBik = { viewModel.lookupBik(it) },
                                        onSaveOrganization = { org, onDone ->
                                            viewModel.saveOrganization(org, onDone)
                                        },
                                        onDeleteOrganization = { id -> viewModel.deleteOrganization(id) }
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
