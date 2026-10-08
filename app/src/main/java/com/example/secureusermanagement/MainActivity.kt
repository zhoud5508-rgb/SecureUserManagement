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

@Composable
fun SecureUserManagementTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF1565C0),
            secondary = androidx.compose.ui.graphics.Color(0xFF03A9F4),
            background = androidx.compose.ui.graphics.Color(0xFFF5F7FA),
            surface = androidx.compose.ui.graphics.Color.White
        ),
        content = content
    )
}

@Composable
fun SecureUserManagementApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(context.applicationContext as Application) as T
            }
        }
    )
    val currentUser by viewModel.currentUser.collectAsState()

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Welcome.route) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Welcome.route) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onLoginClick = { navController.navigate(Screen.Login.route) },
                onRegisterClick = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = viewModel,
                onSuccess = { navController.navigate(Screen.Login.route) { popUpTo(Screen.Welcome.route) { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                user = currentUser,
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onLogClick = { navController.navigate(Screen.ActivityLog.route) },
                onPasswordClick = { navController.navigate(Screen.ChangePassword.route) },
                onAdminClick = { navController.navigate(Screen.AdminManagement.route) },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Welcome.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(user = currentUser, onBack = { navController.popBackStack() })
        }
        composable(Screen.ChangePassword.route) {
            ChangePasswordScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ActivityLog.route) {
            ActivityLogScreen(
                logs = viewModel.activityLogs.collectAsState().value,
                onBack = { navController.popBackStack() },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Welcome.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(Screen.AdminManagement.route) {
            AdminManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun WelcomeScreen(onLoginClick: () -> Unit, onRegisterClick: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Secure User Management", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Authentication and authorization in one place.", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Login")
            }
            Button(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth()) {
                Text("Register")
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AppViewModel, onLoginSuccess: () -> Unit, onBack: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message.startsWith("Welcome")) {
            onLoginSuccess()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Login") })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            if (error.isNotEmpty()) Text(text = error, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.padding(top = 8.dp))
            if (message.isNotEmpty() && !message.startsWith("Welcome")) Text(text = message, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = {
                val validationError = ValidationUtils.validateLogin(username, password)
                if (validationError != null) {
                    error = validationError
                    return@Button
                }
                error = ""
                viewModel.login(username, password)
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Login")
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
        }
    }
}

@Composable
fun RegisterScreen(viewModel: AppViewModel, onSuccess: () -> Unit, onBack: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message == "Registration successful.") {
            onSuccess()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Register") })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            if (error.isNotEmpty()) Text(error, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.padding(top = 8.dp))
            if (message.isNotEmpty() && message != "Registration successful.") Text(message, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = {
                val validationError = ValidationUtils.validateRegistration(username, email, password, confirm)
                if (validationError != null) {
                    error = validationError
                    return@Button
                }
                error = ""
                viewModel.registerUser(username, email, password, confirm)
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Register")
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
        }
    }
}

@Composable
fun HomeScreen(user: UserEntity?, onProfileClick: () -> Unit, onLogClick: () -> Unit, onPasswordClick: () -> Unit, onAdminClick: () -> Unit, onLogout: () -> Unit) {
    val roleText = user?.role ?: Role.USER.name
    Scaffold(topBar = {
        TopAppBar(title = { Text("Home") })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Welcome, ${user?.username ?: "Guest"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Role: $roleText")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
                Button(onClick = onProfileClick, modifier = Modifier.fillMaxWidth()) { Text("Profile") }
                Button(onClick = onPasswordClick, modifier = Modifier.fillMaxWidth()) { Text("Change Password") }
                Button(onClick = onLogClick, modifier = Modifier.fillMaxWidth()) { Text("Activity Log") }
                if (user?.role == Role.ADMIN.name) {
                    Button(onClick = onAdminClick, modifier = Modifier.fillMaxWidth()) { Text("Admin Management") }
                }
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
            }
        }
    }
}

@Composable
fun ProfileScreen(user: UserEntity?, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Username", style = MaterialTheme.typography.labelMedium)
                    Text(user?.username ?: "-", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Email", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(user?.email ?: "-", style = MaterialTheme.typography.bodyLarge)
                    Text("Role", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(user?.role ?: Role.USER.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
    }
}

@Composable
fun ChangePasswordScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf("") }
    val operationMessage by viewModel.passwordChangeMessage.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Change Password") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
            OutlinedTextField(value = currentPassword, onValueChange = { currentPassword = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            if (validationMessage.isNotEmpty()) Text(validationMessage, modifier = Modifier.padding(top = 8.dp), color = Color.Red)
            if (operationMessage.isNotEmpty()) Text(operationMessage, modifier = Modifier.padding(top = 8.dp), color = if (operationMessage.contains("success", ignoreCase = true)) Color(0xFF1B5E20) else Color.Red)
            Button(onClick = {
                val validationError = ValidationUtils.validatePasswordChange(currentPassword, newPassword, confirmPassword)
                if (validationError != null) {
                    validationMessage = validationError
                    return@Button
                }
                validationMessage = ""
                viewModel.changePassword(currentPassword, newPassword, confirmPassword)
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Update Password")
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back") }
        }
    }
}

@Composable
fun ActivityLogScreen(logs: List<ActivityLogEntity>, onBack: () -> Unit, onLogout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Log") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs) { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(log.action, fontWeight = FontWeight.Bold)
                        Text("User: ${log.username}")
                        Text("Timestamp: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))}")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminManagementScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val users by viewModel.users.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadUsers()
        viewModel.loadAllLogs()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Admin Management") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users) { user ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(user.username, fontWeight = FontWeight.Bold)
                            Text(user.email)
                            Text("Role: ${user.role}")
                            Text("Locked: ${if (user.accountLocked) "Yes" else "No"}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { viewModel.toggleRole(user.id) }) {
                                    Text(if (user.role == Role.ADMIN.name) "Demote" else "Promote")
                                }
                                Button(onClick = { viewModel.unlockUser(user.id) }) { Text("Unlock") }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Back") }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureUserManagementTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SecureUserManagementApp()
                }
            }
        }
    }
}
