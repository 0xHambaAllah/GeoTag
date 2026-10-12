package com.geotag.app.ui.screens.auth

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geotag.app.R
import com.geotag.app.ui.components.AppFooter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val webClientId = context.getString(R.string.default_web_client_id)

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Memproses akun Google...", Toast.LENGTH_SHORT).show()
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Toast.makeText(context, "Akun diterima: ${account.email}", Toast.LENGTH_SHORT).show()
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                Toast.makeText(context, "Menghubungi Firebase...", Toast.LENGTH_SHORT).show()
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { 
                        Toast.makeText(context, "Login berhasil!", Toast.LENGTH_SHORT).show()
                        onLoginSuccess() 
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        val msg = "Login Firebase gagal: ${e.message}"
                        errorMessage = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
            } catch (e: ApiException) {
                isLoading = false
                val msg = "Google Sign-In gagal: Code ${e.statusCode} - ${e.message}"
                errorMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } else {
            isLoading = false
            Toast.makeText(context, "Login dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "GeoTag",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tandai lokasi dengan foto\nNavigasi kapan saja",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    googleSignInClient.signOut().addOnCompleteListener {
                        launcher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Masuk dengan Google", style = MaterialTheme.typography.labelLarge)
            }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        AppFooter()
    }
}
