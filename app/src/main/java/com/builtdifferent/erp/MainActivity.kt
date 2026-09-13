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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
 */
@Composable
private fun ErpRoot(container: com.builtdifferent.erp.di.AppContainer) {
    val startDestination by produceState<String?>(initialValue = null, container) {
        value = if (container.companyRepository.hasAnyCompany()) {
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
