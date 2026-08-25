package com.renovation.ledger

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.renovation.ledger.data.prefs.UserPrefs
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.di.ServerEndpoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
            RenovationAppScaffold()
        }
    }
}
