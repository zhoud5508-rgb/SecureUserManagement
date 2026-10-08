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

class UserRepository(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) {
    private val userDao = db.userDao()
    private val activityLogDao = db.activityLogDao()

    suspend fun registerUser(username: String, email: String, password: String, confirmPassword: String): Result<String> = withContext(Dispatchers.IO) {
        val validationError = ValidationUtils.validateRegistration(username, email, password, confirmPassword)
        if (validationError != null) return@withContext Result.failure(IllegalArgumentException(validationError))

        val trimmedUsername = username.trim()
        val trimmedEmail = email.trim()
        if (userDao.getByUsername(trimmedUsername) != null) {
            return@withContext Result.failure(IllegalArgumentException("Username is already taken."))
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash(password, salt)
        val user = UserEntity(
            username = trimmedUsername,
            email = trimmedEmail,
            passwordHash = hash,
            salt = salt,
            role = Role.USER.name
        )
        val insertedId = try {
            userDao.insert(user)
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            return@withContext Result.failure(IllegalArgumentException("Username is already taken."))
        }
        logAction(insertedId, trimmedUsername, "Registration")
        Result.success("Registration successful.")
    }

    suspend fun login(username: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val validationError = ValidationUtils.validateLogin(username, password)
        if (validationError != null) return@withContext Result.failure(IllegalArgumentException(validationError))

        val user = userDao.getByUsername(username.trim()) ?: return@withContext Result.failure(IllegalArgumentException("Invalid username or password."))
        val now = System.currentTimeMillis()
        if (user.accountLocked && now < user.lockedUntil) {
            return@withContext Result.failure(IllegalArgumentException("Invalid username or password."))
        }

        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            val attempts = user.failedLoginAttempts + 1
            val locked = attempts >= MAX_FAILED_LOGIN_ATTEMPTS
            val updated = user.copy(
                failedLoginAttempts = attempts,
                accountLocked = locked,
                lockedUntil = if (locked) now + LOCK_DURATION_MS else 0L
            )
            userDao.update(updated)
            logAction(user.id, user.username, if (locked) "Account Lockout" else "Failed Login")
            return@withContext Result.failure(IllegalArgumentException("Invalid username or password."))
        }

        val activeUser = user.copy(
            failedLoginAttempts = 0,
            accountLocked = false,
            lockedUntil = 0L
        )
        userDao.update(activeUser)
        sessionManager.saveSession(activeUser.id, activeUser.username, activeUser.role)
        logAction(activeUser.id, activeUser.username, "Successful Login")
        Result.success(activeUser)
    }

    suspend fun logout(): Result<String> = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No active session."))
        val currentUsername = sessionManager.getCurrentUsername() ?: "unknown"
        sessionManager.clearSession()
        logAction(currentUserId, currentUsername, "Logout")
        Result.success("Logged out successfully.")
    }

    suspend fun changePassword(userId: Long, currentPassword: String, newPassword: String, confirmPassword: String): Result<String> = withContext(Dispatchers.IO) {
        val validationError = ValidationUtils.validatePasswordChange(currentPassword, newPassword, confirmPassword)
        if (validationError != null) return@withContext Result.failure(IllegalArgumentException(validationError))

        if (sessionManager.getCurrentUserId() != userId) {
            return@withContext Result.failure(SecurityException("You may only change your own password."))
        }
        val user = userDao.getById(userId) ?: return@withContext Result.failure(IllegalStateException("User not found."))
        if (!PasswordHasher.verify(currentPassword, user.salt, user.passwordHash)) {
            return@withContext Result.failure(IllegalArgumentException("Current password is incorrect."))
        }

        val newSalt = PasswordHasher.generateSalt()
        val newHash = PasswordHasher.hash(newPassword, newSalt)
        userDao.update(user.copy(passwordHash = newHash, salt = newSalt))
        logAction(user.id, user.username, "Password Change")
        Result.success("Password changed successfully.")
    }

    suspend fun getCurrentUser(): UserEntity? = withContext(Dispatchers.IO) {
        val userId = sessionManager.getCurrentUserId() ?: return@withContext null
        userDao.getById(userId)
    }

    suspend fun getActivityLogsForCurrentUser(): List<ActivityLogEntity> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getCurrentUserId() ?: return@withContext emptyList()
        activityLogDao.getByUserId(userId)
    }

    suspend fun getAllUsers(): List<AdminUserSummary> = withContext(Dispatchers.IO) {
        requireAdmin()
        userDao.getAll()
    }

    suspend fun getAllLogs(): List<ActivityLogEntity> = withContext(Dispatchers.IO) {
        requireAdmin()
        activityLogDao.getAll()
    }

    suspend fun toggleAdminStatus(userId: Long): Result<UserEntity> = withContext(Dispatchers.IO) {
        val actor = try { requireAdmin() } catch (e: SecurityException) { return@withContext Result.failure(e) }
        val user = userDao.getById(userId) ?: return@withContext Result.failure(IllegalStateException("User not found."))
        val updated = user.copy(role = if (user.role == Role.ADMIN.name) Role.USER.name else Role.ADMIN.name)
        userDao.update(updated)
        logAction(actor.id, actor.username, "Changed role for ${user.username}")
        Result.success(updated)
    }

    suspend fun unlockUser(userId: Long): Result<UserEntity> = withContext(Dispatchers.IO) {
        val actor = try { requireAdmin() } catch (e: SecurityException) { return@withContext Result.failure(e) }
        val user = userDao.getById(userId) ?: return@withContext Result.failure(IllegalStateException("User not found."))
        val unlocked = user.copy(accountLocked = false, failedLoginAttempts = 0, lockedUntil = 0L)
        userDao.update(unlocked)
        logAction(actor.id, actor.username, "Unlocked account for ${user.username}")
        Result.success(unlocked)
    }

    private suspend fun requireAdmin(): UserEntity {
        val userId = sessionManager.getCurrentUserId() ?: throw SecurityException("Sign in is required.")
        val user = userDao.getById(userId) ?: throw SecurityException("Sign in is required.")
        if (user.role != Role.ADMIN.name) throw SecurityException("Administrator access is required.")
        return user
    }

    private suspend fun logAction(userId: Long, username: String, action: String) {
        activityLogDao.insert(
            ActivityLogEntity(
                userId = userId,
                username = username,
                action = action,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
