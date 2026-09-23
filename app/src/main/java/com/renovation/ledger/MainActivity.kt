package com.renovation.ledger

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.di.ServerEndpoint
import com.renovation.ledger.ui.navigation.LxyRoutes
import com.renovation.ledger.ui.navigation.RenovationAppScaffold
import com.renovation.ledger.ui.theme.HealthThemeBootstrap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var projectRepository: ProjectRepository
    @Inject lateinit var userPrefs: UserPrefs
    @Inject lateinit var serverEndpoint: ServerEndpoint

    private var externalRoute by mutableStateOf<String?>(null)
    private var externalRouteNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            deliverRoute(intent)
        }
        window.setBackgroundDrawable(
            ColorDrawable(
                HealthThemeBootstrap.pageBackgroundArgb(
                    userPrefs.peekLastHealthLevel(),
                    userPrefs.peekHealthColorEnabled(),
                ),
            ),
        )
        lifecycleScope.launch {
            projectRepository.ensureDefaultProject()
        }
        lifecycleScope.launch {
            userPrefs.serverBaseUrl.collect { url ->
                serverEndpoint.baseUrl = url
            }
        }
        setContent {
            RenovationAppScaffold(
                externalRoute = externalRoute,
                externalRouteNonce = externalRouteNonce,
                onExternalRouteConsumed = { externalRoute = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverRoute(intent)
    }

    private fun deliverRoute(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme.equals(LxyRoutes.SCHEME, ignoreCase = true)) return
        externalRoute = data.toString()
        externalRouteNonce += 1
    }
}
