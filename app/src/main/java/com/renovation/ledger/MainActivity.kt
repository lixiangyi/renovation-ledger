package com.renovation.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.renovation.ledger.data.repo.ProjectRepository
import com.renovation.ledger.ui.navigation.RenovationAppScaffold
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var projectRepository: ProjectRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            projectRepository.ensureDefaultProject()
        }
        setContent {
            RenovationAppScaffold()
        }
    }
}
