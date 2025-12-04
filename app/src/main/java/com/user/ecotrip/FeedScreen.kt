package com.user.ecotrip

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import android.util.Log

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = Firebase.auth
    var isLoading by remember { mutableStateOf(false) }

    // 1. 配置 Google 登录
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.))
        .requestEmail()
        .build()

    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    // 2. 登录结果回调
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    isLoading = true
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                // 登录成功，保存用户数据
                                saveUserToFirestore(auth.currentUser) {
                                    isLoading = false
                                    onLoginSuccess()
                                }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Firebase 认证失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("Login", "Google sign in failed", e)
                Toast.makeText(context, "登录失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3. 界面 UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0F2F1)), // 浅绿背景
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "EcoTrip",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00695C)
        )
        Text(
            text = "让地球更绿一点 🌱",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Button(
            onClick = { launcher.launch(googleSignInClient.signInIntent) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isLoading) {
                Text("登录中...", color = Color.Gray)
            } else {
                Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 4. 用户存档逻辑 (带数据继承)
fun saveUserToFirestore(user: com.google.firebase.auth.FirebaseUser?, onDone: () -> Unit) {
    if (user == null) return

    val db = Firebase.firestore
    val userRef = db.collection("users").document(user.uid)
    val userEmail = user.email ?: ""

    userRef.get().addOnSuccessListener { document ->
        if (document.exists()) {
            onDone() // 老用户直接进
        } else {
            // 新用户：检查有没有 MySQL 的遗产
            db.collection("users_legacy").document(userEmail).get()
                .addOnSuccessListener { legacyDoc ->
                    var initialPoints = 0
                    if (legacyDoc.exists()) {
                        initialPoints = legacyDoc.getLong("points")?.toInt() ?: 0
                    }

                    val newUser = hashMapOf(
                        "uid" to user.uid,
                        "name" to (user.displayName ?: "Eco User"),
                        "email" to userEmail,
                        "avatar" to (user.photoUrl?.toString() ?: ""),
                        "points" to initialPoints, // ✅ 继承积分
                        "createdAt" to System.currentTimeMillis()
                    )
                    userRef.set(newUser).addOnSuccessListener { onDone() }
                }
        }
    }
}