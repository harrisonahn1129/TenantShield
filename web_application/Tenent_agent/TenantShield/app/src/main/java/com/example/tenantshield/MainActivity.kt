package com.example.tenantshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenantshield.agents.orchestrator.OrchestratorState
import com.example.tenantshield.ui.viewmodel.InspectionViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tenantshield.ui.screens.home.HomeScreen
import com.example.tenantshield.ui.screens.inspect.InspectScreen
import com.example.tenantshield.ui.screens.owners.OwnersScreen
import com.example.tenantshield.ui.screens.reports.ReportsScreen
import com.example.tenantshield.ui.theme.Primary
import com.example.tenantshield.ui.theme.TenantShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TenantShieldTheme {
                TenantShieldApp()
            }
        }
    }
}

enum class AppDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Outlined.Home),
    INSPECT("Inspect", Icons.Outlined.Videocam),
    OWNERS("Owners", Icons.Outlined.PersonSearch),
    REPORTS("Reports", Icons.Outlined.Description),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantShieldApp(inspectionViewModel: InspectionViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { inspectionViewModel.initialize(context) }

    val inspectionUiState by inspectionViewModel.uiState.collectAsState()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var previousDestination by rememberSaveable { mutableStateOf<AppDestination?>(null) }

    // Auto-navigate to Reports when complaint is generated
    LaunchedEffect(inspectionUiState.pipelineState) {
        if (inspectionUiState.pipelineState == OrchestratorState.COMPLETE
            && inspectionUiState.complaintForm != null) {
            previousDestination = currentDestination
            currentDestination = AppDestination.REPORTS
        }
    }

    BackHandler(enabled = currentDestination != AppDestination.HOME) {
        val target = previousDestination ?: AppDestination.HOME
        previousDestination = null
        currentDestination = target
    }

    fun navigateTo(dest: AppDestination) {
        if (dest != currentDestination) {
            previousDestination = currentDestination
            currentDestination = dest
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentDestination != AppDestination.INSPECT) {
                TenantShieldTopBar()
            }
        },
        bottomBar = {
            if (currentDestination != AppDestination.INSPECT) {
                TenantShieldBottomBar(currentDestination) { navigateTo(it) }
            }
        },
        floatingActionButton = {}
    ) { innerPadding ->
        when (currentDestination) {
            AppDestination.HOME -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                onStartInspection = {
                    inspectionViewModel.startInspectionFlow()
                    navigateTo(AppDestination.INSPECT)
                }
            )
            AppDestination.INSPECT -> InspectScreen(
                modifier = Modifier.fillMaxSize(),
                inspectionViewModel = inspectionViewModel,
                onClose = {
                    val target = previousDestination ?: AppDestination.HOME
                    previousDestination = null
                    currentDestination = target
                }
            )
            AppDestination.OWNERS -> OwnersScreen(Modifier.padding(innerPadding))
            AppDestination.REPORTS -> ReportsScreen(
                modifier = Modifier.padding(innerPadding),
                complaintForm = inspectionUiState.complaintForm
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TenantShieldTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Gavel,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "TenantShield",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            androidx.compose.material3.IconButton(onClick = { }) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = "Profile",
                    tint = Primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun TenantShieldBottomBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit
) {
    NavigationBar(
        containerColor = Color.White.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
    ) {
        AppDestination.entries.forEach { dest ->
            NavigationBarItem(
                icon = { Icon(dest.icon, dest.label) },
                label = {
                    Text(
                        dest.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                },
                selected = dest == selected,
                onClick = { onSelect(dest) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Primary,
                    indicatorColor = Primary.copy(alpha = 0.9f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
        }
    }
}
