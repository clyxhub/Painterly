package com.painterly.app.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.painterly.app.data.ProjectRepository
import com.painterly.app.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectSummary(val project: Project, val thumbnail: Bitmap?)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository.get(application)

    private val _projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val projects: StateFlow<List<ProjectSummary>> = _projects.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val summaries = withContext(Dispatchers.IO) {
                repository.listProjects().map { ProjectSummary(it, repository.loadThumbnail(it)) }
            }
            _projects.value = summaries
        }
    }

    fun createProject(uri: Uri, name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val project = withContext(Dispatchers.IO) { repository.createProject(uri, name) }
                refresh()
                onCreated(project.id)
            } catch (e: Exception) {
                _error.value = "That image couldn't be imported. Please try another one."
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteProject(id) }
            refresh()
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
