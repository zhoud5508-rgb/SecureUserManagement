@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.secureusermanagement

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val sessionManager = SessionManager(application)
    private val repository = UserRepository(db, sessionManager)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser

    private val _users = MutableStateFlow<List<AdminUserSummary>>(emptyList())
    val users: StateFlow<List<AdminUserSummary>> = _users

    private val _activityLogs = MutableStateFlow<List<ActivityLogEntity>>(emptyList())
    val activityLogs: StateFlow<List<ActivityLogEntity>> = _activityLogs

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _passwordChangeMessage = MutableStateFlow("")
    val passwordChangeMessage: StateFlow<String> = _passwordChangeMessage

    init {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            _currentUser.value = user
            if (user != null) {
                _activityLogs.value = repository.getActivityLogsForCurrentUser()
                if (user.role == Role.ADMIN.name) {
                    _users.value = repository.getAllUsers()
                }
            }
        }
    }

    fun registerUser(username: String, email: String, password: String, confirmPassword: String) = viewModelScope.launch {
        repository.registerUser(username, email, password, confirmPassword)
            .onSuccess {
                _message.value = it
            }
            .onFailure {
                _message.value = it.message ?: "Registration failed."
            }
    }

    fun login(username: String, password: String) = viewModelScope.launch {
        repository.login(username, password)
            .onSuccess { user ->
                _currentUser.value = user
                _message.value = "Welcome, ${user.username}!"
                _activityLogs.value = repository.getActivityLogsForCurrentUser()
                if (user.role == Role.ADMIN.name) {
                    _users.value = repository.getAllUsers()
                }
            }
            .onFailure {
                _message.value = it.message ?: "Login failed."
            }
    }

    fun logout() = viewModelScope.launch {
        repository.logout().onSuccess {
            _currentUser.value = null
            _activityLogs.value = emptyList()
            _users.value = emptyList()
            _message.value = it
        }.onFailure {
            _message.value = it.message ?: "Logout failed."
        }
    }

    fun reloadUser() = viewModelScope.launch {
        _currentUser.value = repository.getCurrentUser()
        if (_currentUser.value != null) {
            _activityLogs.value = repository.getActivityLogsForCurrentUser()
            if (_currentUser.value?.role == Role.ADMIN.name) {
                _users.value = repository.getAllUsers()
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) = viewModelScope.launch {
        val currentUserId = _currentUser.value?.id ?: return@launch
        _passwordChangeMessage.value = ""
        repository.changePassword(currentUserId, currentPassword, newPassword, confirmPassword)
            .onSuccess { _passwordChangeMessage.value = it }
            .onFailure { _passwordChangeMessage.value = it.message ?: "Password change failed." }
    }

    fun loadUsers() = viewModelScope.launch {
        _users.value = repository.getAllUsers()
    }

    fun loadLogs() = viewModelScope.launch {
        _activityLogs.value = repository.getActivityLogsForCurrentUser()
    }

    fun loadAllLogs() = viewModelScope.launch {
        _activityLogs.value = repository.getAllLogs()
    }

    fun toggleRole(userId: Long) = viewModelScope.launch {
        repository.toggleAdminStatus(userId)
            .onSuccess { _users.value = repository.getAllUsers() }
            .onFailure { _message.value = it.message ?: "Role update failed." }
    }

    fun unlockUser(userId: Long) = viewModelScope.launch {
        repository.unlockUser(userId)
            .onSuccess { _users.value = repository.getAllUsers() }
            .onFailure { _message.value = it.message ?: "Unlock failed." }
    }
}

