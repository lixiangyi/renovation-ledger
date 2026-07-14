package com.renovation.ledger.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** 外层 NavHost 已统一 statusBarsPadding，页面 TopAppBar 不再叠一层。 */
val ZeroTopAppBarWindowInsets: WindowInsets
    @Composable
    get() = WindowInsets(0.dp)
