package com.painterly.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.painterly.app.ui.home.HomeScreen
import com.painterly.app.ui.workspace.WorkspaceScreen

private sealed interface Route {
    data object Home : Route
    data class Workspace(val projectId: String) : Route
}

@Composable
fun PainterlyApp() {
    var route by remember { mutableStateOf<Route>(Route.Home) }

    when (val current = route) {
        is Route.Home -> HomeScreen(
            onOpenProject = { id -> route = Route.Workspace(id) },
        )
        is Route.Workspace -> WorkspaceScreen(
            projectId = current.projectId,
            onBack = { route = Route.Home },
        )
    }
}
