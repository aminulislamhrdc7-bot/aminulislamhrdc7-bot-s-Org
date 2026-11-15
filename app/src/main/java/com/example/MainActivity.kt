package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.HisabRepository
import com.example.data.LocalHisabStore
import com.example.data.PreferencesManager
import com.example.ui.HisabViewModel
import com.example.ui.components.PinLockScreen
import com.example.ui.components.UserLoginScreen
import com.example.ui.components.attemptAutoSignIn
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseScreen
import com.example.ui.screens.IncomeScreen
import com.example.ui.screens.LoansScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SavingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyHisabTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object HisabRoutes {
    const val HOME = "home"
    const val INCOME = "income"
    const val EXPENSE = "expense"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val SAVINGS = "savings"
    const val LOANS = "loans"
    const val ADMIN_PANEL = "admin_panel"
}

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyHisabRoot()
        }
    }
}

@Composable
fun MyHisabRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context.applicationContext) }
    val localPrefs by preferencesManager.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.data.LocalAppPreferences()
    )
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            val cm = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = cm,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    MyHisabTheme(themeMode = localPrefs.themeMode) {
        val user = currentUser
        val sessionKey = user?.uid ?: "local_device"
        key(sessionKey) {
            AuthenticatedApp(
                currentUser = user,
                preferencesManager = preferencesManager
            )
        }
    }
}

@Composable
private fun AuthenticatedApp(
    currentUser: FirebaseUser?,
    preferencesManager: PreferencesManager
) {
    val sessionKey = currentUser?.uid ?: "local_device"
    val viewModel: HisabViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val localStore = LocalHisabStore(app)
                if (currentUser != null) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    HisabViewModel(
                        repository = HisabRepository(db, Firebase.auth),
                        preferencesManager = preferencesManager,
                        currentUserId = currentUser.uid,
                        currentUserEmail = currentUser.email ?: "",
                        localStoreToSync = localStore
                    )
                } else {
                    HisabViewModel(
                        repository = localStore,
                        preferencesManager = preferencesManager,
                        currentUserId = null,
                        currentUserEmail = "",
                        localStoreToSync = null
                    )
                }
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (!state.isAppUnlocked) {
        PinLockScreen(
            strings = state.strings,
            onVerifyPin = viewModel::unlockWithPin
        )
        return
    }

    if (state.userLoginRequired) {
        UserLoginScreen(
            strings = state.strings,
            language = state.language,
            isGoogleSignedIn = currentUser != null,
            onToggleLanguage = viewModel::setLanguage,
            onSubmitUserLogin = viewModel::loginWithUserIdPassword
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingOpenAddIncome by remember { mutableStateOf(false) }
    var pendingOpenAddExpense by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val strings = state.strings
    val tabs = listOf(
        NavTab(HisabRoutes.HOME, strings.homeTab, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        NavTab(HisabRoutes.INCOME, strings.incomeTab, Icons.AutoMirrored.Filled.TrendingUp, Icons.AutoMirrored.Outlined.TrendingUp, "nav_income"),
        NavTab(HisabRoutes.EXPENSE, strings.expenseTab, Icons.AutoMirrored.Filled.TrendingDown, Icons.AutoMirrored.Outlined.TrendingDown, "nav_expense"),
        NavTab(HisabRoutes.REPORTS, strings.reportsTab, Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_reports"),
        NavTab(HisabRoutes.SETTINGS, strings.settingsTab, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    val showChrome = currentDestination?.route in tabs.map { it.route }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HisabRoutes.HOME,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable(HisabRoutes.HOME) {
                        DashboardScreen(
                            state = state,
                            onToggleCurrency = viewModel::setPreferredCurrency,
                            onToggleLanguage = viewModel::setLanguage,
                            onOpenSavings = { navController.navigate(HisabRoutes.SAVINGS) },
                            onOpenLoans = { navController.navigate(HisabRoutes.LOANS) },
                            onOpenAdminPanel = { navController.navigate(HisabRoutes.ADMIN_PANEL) },
                            onQuickAddIncome = {
                                pendingOpenAddIncome = true
                                navigateTopLevel(HisabRoutes.INCOME)
                            },
                            onQuickAddExpense = {
                                pendingOpenAddExpense = true
                                navigateTopLevel(HisabRoutes.EXPENSE)
                            }
                        )
                    }
                    composable(HisabRoutes.INCOME) {
                        IncomeScreen(
                            state = state,
                            openAddImmediately = pendingOpenAddIncome,
                            onConsumedOpenAdd = { pendingOpenAddIncome = false },
                            onAddIncome = viewModel::addIncome,
                            onUpdateIncome = viewModel::updateIncome,
                            onDeleteIncome = viewModel::deleteIncome
                        )
                    }
                    composable(HisabRoutes.EXPENSE) {
                        ExpenseScreen(
                            state = state,
                            openAddImmediately = pendingOpenAddExpense,
                            onConsumedOpenAdd = { pendingOpenAddExpense = false },
                            onAddExpense = viewModel::addExpense,
                            onUpdateExpense = viewModel::updateExpense,
                            onDeleteExpense = viewModel::deleteExpense
                        )
                    }
                    composable(HisabRoutes.REPORTS) {
                        ReportsScreen(state = state)
                    }
                    composable(HisabRoutes.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            onSetLanguage = viewModel::setLanguage,
                            onSetThemeMode = viewModel::setThemeMode,
                            onSetPreferredCurrency = viewModel::setPreferredCurrency,
                            onSaveExchangeRate = viewModel::updateExchangeRate,
                            onSetPin = viewModel::setPinLock,
                            onDisablePin = viewModel::disablePinLock,
                            onBuildBackupJson = viewModel::buildBackupJson,
                            onRestoreBackupJson = viewModel::restoreFromBackupJson,
                            onOpenAdminPanel = { navController.navigate(HisabRoutes.ADMIN_PANEL) },
                            onSwitchToUserLogin = viewModel::switchToUserLoginScreen
                        )
                    }
                    composable(HisabRoutes.SAVINGS) {
                        SavingsScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onAddGoal = viewModel::addSavingsGoal,
                            onUpdateGoal = viewModel::updateSavingsGoal,
                            onDeleteGoal = viewModel::deleteSavingsGoal,
                            onAddDeposit = viewModel::addSavingsDeposit,
                            onDeleteEntry = viewModel::deleteSavingsEntry
                        )
                    }
                    composable(HisabRoutes.LOANS) {
                        LoansScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onAddLoan = viewModel::addLoan,
                            onUpdateLoan = viewModel::updateLoan,
                            onRecordRepayment = viewModel::recordLoanRepayment,
                            onDeleteRepayment = viewModel::deleteLoanRepayment,
                            onDeleteLoan = viewModel::deleteLoan
                        )
                    }
                    composable(HisabRoutes.ADMIN_PANEL) {
                        AdminPanelScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onCreateUser = viewModel::createManagedUser,
                            onUpdateUser = viewModel::updateManagedUser,
                            onToggleActive = viewModel::toggleManagedUserActive,
                            onDeleteUser = viewModel::deleteManagedUser,
                            onLoginAsUser = { managedUser ->
                                viewModel.loginAsManagedUserDirectly(managedUser)
                                navigateTopLevel(HisabRoutes.HOME)
                            },
                            onSwitchToUserLogin = {
                                navController.popBackStack()
                                viewModel.switchToUserLoginScreen()
                            }
                        )
                    }
                }
            }
        }
    }
}
