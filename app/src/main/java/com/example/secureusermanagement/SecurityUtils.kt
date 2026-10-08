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

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private val random = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    fun verify(password: String, salt: String, passwordHash: String): Boolean {
        return try {
            val actual = Base64.decode(hash(password, salt), Base64.NO_WRAP)
            val expected = Base64.decode(passwordHash, Base64.NO_WRAP)
            MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

object ValidationUtils {
    private const val MIN_PASSWORD_LENGTH = 12

    fun validateRegistration(username: String, email: String, password: String, confirmPassword: String): String? {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank()) return "Username is required."
        if (normalizedUsername.length !in 3..32) return "Username must be 3 to 32 characters long."
        if (!normalizedUsername.matches(Regex("[A-Za-z0-9._-]+"))) return "Username may use letters, numbers, dot, underscore, and hyphen."
        if (email.isBlank()) return "Email is required."
        if (!email.trim().matches(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))) return "Enter a valid email address."
        if (password.isBlank()) return "Password is required."
        if (password.length < MIN_PASSWORD_LENGTH) return "Password must be at least 12 characters."
        if (confirmPassword != password) return "Passwords do not match."
        return null
    }

    fun validateLogin(username: String, password: String): String? {
        if (username.isBlank()) return "Username is required."
        if (password.isBlank()) return "Password is required."
        return null
    }

    fun validatePasswordChange(currentPassword: String, newPassword: String, confirmPassword: String): String? {
        if (currentPassword.isBlank()) return "Current password is required."
        if (newPassword.isBlank()) return "New password is required."
        if (newPassword.length < MIN_PASSWORD_LENGTH) return "New password must be at least 12 characters."
        if (confirmPassword != newPassword) return "New passwords do not match."
        return null
    }
}

class SessionManager(context: Context) {
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_user_management_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(userId: Long, username: String, role: String) {
        encryptedPrefs.edit().putLong("user_id", userId).putString("username", username).putString("role", role).apply()
    }

    fun clearSession() {
        encryptedPrefs.edit().clear().apply()
    }

    fun getCurrentUserId(): Long? = if (encryptedPrefs.contains("user_id")) encryptedPrefs.getLong("user_id", -1L) else null
    fun getCurrentUsername(): String? = encryptedPrefs.getString("username", null)
    fun getCurrentRole(): String? = encryptedPrefs.getString("role", null)
}
