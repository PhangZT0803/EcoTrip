package com.user.ecotrip

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import android.util.Log // 导入 Log 用于调试

// ==========================================================
// 辅助函数：用户凭证的本地存储
// ==========================================================
fun saveUserCredentials(context: Context, uid: String, email: String) {
    val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("user_uid", uid)
        putString("user_email", email)
        putBoolean("is_logged_in", true)
        apply()
    }
}

// ==========================================================
// 辅助函数：将用户基本信息写入 Firestore，同时检查旧数据并继承积分
// ==========================================================
fun saveUserToFirestore(context: Context, uid: String, email: String, onDone: () -> Unit) {
    val db = Firebase.firestore
    val userRef = db.collection("users").document(uid)
    val userEmail = email.toLowerCase()

    userRef.get().addOnSuccessListener { document ->
        if (document.exists()) {
            // 1. 老用户：已在 Firestore，直接完成
            onDone()
        } else {
            // 2. 新用户：检查 legacy_users 集合是否有旧数据
            // ⚠️ 注意：这里假设你的旧数据已导入到 Firestore 的 legacy_users 集合中
            db.collection("legacy_users").document(userEmail).get()
                .addOnSuccessListener { legacyDoc ->
                    var initialPoints = 0
                    var initialName = "Eco User"

                    if (legacyDoc.exists()) {
                        // 发现旧数据，继承积分和名字
                        initialPoints = legacyDoc.getLong("points")?.toInt() ?: 0
                        initialName = legacyDoc.getString("name") ?: initialName
                        Log.d("MIGRATION", "继承了旧用户 $userEmail 的 $initialPoints 积分")
                    }

                    // 创建新的 Firebase 用户记录
                    val newUser = hashMapOf(
                        "uid" to uid,
                        "name" to initialName,
                        "email" to userEmail,
                        "points" to initialPoints, // ✅ 继承积分！
                        "createdAt" to System.currentTimeMillis()
                    )

                    // 将新用户记录写入 Firestore
                    userRef.set(newUser).addOnSuccessListener {
                        onDone()
                    }
                        .addOnFailureListener {
                            onDone() // 写入失败也要让 App 进入主页
                        }
                }
                .addOnFailureListener {
                    // 如果 legacy_users 集合不存在或查询失败，创建默认用户
                    val newUser = hashMapOf(
                        "uid" to uid,
                        "name" to "Eco User",
                        "email" to userEmail,
                        "points" to 0,
                        "createdAt" to System.currentTimeMillis()
                    )
                    userRef.set(newUser).addOnSuccessListener { onDone() }
                }
        }
    }
}


// ==========================================================
// 核心登录/注册屏幕
// ==========================================================
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = Firebase.auth

    // 输入框的状态
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }

    // 登录或注册逻辑
    fun handleAuth() {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Email 和密码不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true

        val authTask = if (isRegisterMode) {
            // 注册模式
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            // 登录模式
            auth.signInWithEmailAndPassword(email, password)
        }

        authTask.addOnCompleteListener { task ->
            isLoading = false
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) {
                    saveUserCredentials(context, user.uid, user.email ?: "")

                    // 👇 核心：将用户信息同步到 Firestore，并处理旧数据继承
                    saveUserToFirestore(context, user.uid, user.email ?: "") {
                        Toast.makeText(context, if (isRegisterMode) "注册成功，已自动登录！" else "登录成功！", Toast.LENGTH_LONG).show()
                        onLoginSuccess()
                    }
                }
            } else {
                Toast.makeText(context, "认证失败: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 界面 UI
    // ... (UI 部分保持不变)
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE0F2F1)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("EcoTrip ${if (isRegisterMode) "注册" else "登录"}",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00695C))
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { handleAuth() },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) Text("处理中...") else Text(if (isRegisterMode) "注册并登录" else "登录")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isRegisterMode) "已有账号？点击登录" else "没有账号？点击注册",
            color = Color.Gray,
            modifier = Modifier.clickable { isRegisterMode = !isRegisterMode }
        )
    }
}