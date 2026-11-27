package com.martronics.hermitic

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martronics.hermitic.core.security.AppLockManager
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.ui.navigation.HermiticNavHost
import com.martronics.hermitic.ui.theme.HermiticTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var credentialVault: CredentialVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val config by credentialVault.config.collectAsStateWithLifecycle()
            val isLocked by appLockManager.isLocked.collectAsStateWithLifecycle()

            LaunchedEffect(config.flagSecureEnabled) {
                if (config.flagSecureEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            HermiticTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    HermiticNavHost(isConfigured = config.isConfigured)
                    if (isLocked) {
                        var errorMessage by remember { mutableStateOf<String?>(null) }
                        val triggerPrompt = {
                            errorMessage = null
                            appLockManager.promptUnlock(
                                activity = this@MainActivity,
                                onError = { msg -> errorMessage = msg },
                            )
                        }

                        LaunchedEffect(Unit) {
                            triggerPrompt()
                        }

                        AppLockOverlay(
                            errorMessage = errorMessage,
                            onUnlockClick = triggerPrompt,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockManager.onForeground()
    }

    override fun onStop() {
        appLockManager.onBackground()
        super.onStop()
    }
}

@Composable
private fun AppLockOverlay(
    errorMessage: String?,
    onUnlockClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hermitic Locked",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "Authenticate to view Hermes sessions and credentials.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onUnlockClick) {
                Text("Unlock")
            }
        }
    }
}
