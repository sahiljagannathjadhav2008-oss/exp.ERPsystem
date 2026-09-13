package com.builtdifferent.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.builtdifferent.erp.ui.navigation.ErpDestinations
import com.builtdifferent.erp.ui.navigation.ErpNavGraph
import com.builtdifferent.erp.ui.theme.OfflineErpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ErpApplication).container

        setContent {
            OfflineErpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErpRoot(container = container)
                }
            }
        }
    }
}

/**
 * Decides once, at cold start, whether to land on Company Setup (no
 * business created yet — this is the very first run) or the Dashboard.
 * Shown as a brief loading spinner rather than defaulting to one screen
 * and redirecting, so the user never sees a flash of the wrong screen.
 *
 * Uses LaunchedEffect + mutableStateOf rather than produceState: behavior
 * is identical (resolve once when `container` is first available, hold a
 * loading spinner until resolved), but produceState trips a known Compose
 * lint false-positive (ProduceStateDoesNotAssignValue) on this exact shape
 * of producer body even though `value = ...` is genuinely assigned. This
 * sidesteps the lint limitation rather than fighting it, with no change in
 * behavior.
 */
@Composable
private fun ErpRoot(container: com.builtdifferent.erp.di.AppContainer) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(container) {
        startDestination = if (container.companyRepository.hasAnyCompany()) {
            ErpDestinations.DASHBOARD
        } else {
            ErpDestinations.COMPANY_SETUP
        }
    }

    val resolved = startDestination
    if (resolved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ErpNavGraph(container = container, startDestination = resolved)
    }
}
