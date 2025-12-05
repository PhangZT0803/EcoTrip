package com.user.ecotrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.user.ecotrip.ui.theme.EcoTripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 让 App 铺满全屏
        setContent {
            EcoTripTheme {
                // 1. 核心状态：检查用户现在是不是登录状态
                // Firebase.auth.currentUser 不为空，说明之前登录过
                var isLoggedIn by remember { mutableStateOf(Firebase.auth.currentUser != null) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLoggedIn) {
                        // ✅ 如果已登录，显示主界面 (带底部导航栏)
                        // 把 "退出登录" 的功能传进去，如果用户点了退出，isLoggedIn 变成 false
                        MainApp(onLogout = { isLoggedIn = false })
                    } else {
                        // ❌ 如果没登录，显示登录页
                        // 如果登录成功了，isLoggedIn 变成 true
                        LoginScreen(onLoginSuccess = { isLoggedIn = true })
                    }
                }
            }
        }
    }
}

// 2. 主界面组件 (包含底部导航栏)
@Composable
fun MainApp(onLogout: () -> Unit) {
    // 记录当前选中的是第几个页面 (0=挑战, 1=动态, 2=我的)
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                // 第 1 个按钮：挑战
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("挑战") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                // 第 2 个按钮：动态
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("动态") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                // 第 3 个按钮：我的
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { innerPadding ->
        // 根据选中的 Tab，显示不同的屏幕
        // padding 参数是为了防止内容被底部的导航栏挡住
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> ChallengeScreen() // 显示挑战列表
                1 -> ProfileScreen(onLogout = onLogout) // 显示个人中心 (把退出功能传给它)
            }
        }
    }
}