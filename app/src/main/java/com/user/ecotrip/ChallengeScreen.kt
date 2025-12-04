package com.user.ecotrip

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage // 引用存储库
import java.io.ByteArrayOutputStream
import java.util.UUID

// 1. 数据模型
data class Challenge(
    val id: String = "",
    val title: String = "",
    val points: Int = 0,
    val desc: String = ""
)

@Composable
fun ChallengeScreen() {
    val challengesList = remember { mutableStateListOf<Challenge>() }
    val context = LocalContext.current

    // 状态变量
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedChallenge by remember { mutableStateOf<Challenge?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // 📷 相机启动器
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) capturedBitmap = bitmap
    }

    // 📡 读取数据
    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        db.collection("challenges").get()
            .addOnSuccessListener { result ->
                challengesList.clear()
                for (document in result) {
                    val challenge = document.toObject(Challenge::class.java).copy(id = document.id)
                    challengesList.add(challenge)
                }
            }
    }

    // 🚀 上传逻辑：图片 -> Storage, 链接 -> Firestore
    fun uploadSubmission() {
        if (capturedBitmap == null || selectedChallenge == null) return
        isUploading = true

        // 1. 压缩图片
        val outputStream = ByteArrayOutputStream()
        capturedBitmap?.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val data = outputStream.toByteArray()

        // 2. 生成文件名 (使用 UUID 防止重名)
        val fileName = "submissions/${UUID.randomUUID()}.jpg"
        val storageRef = Firebase.storage.reference.child(fileName)

        // 3. 上传到 Storage
        storageRef.putBytes(data)
            .addOnSuccessListener {
                // 4. 拿到下载链接
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()

                    // 5. 写入数据库
                    val submission = hashMapOf(
                        "challengeTitle" to selectedChallenge?.title,
                        "points" to selectedChallenge?.points,
                        "photoUrl" to downloadUrl,
                        "status" to "Pending",
                        "timestamp" to System.currentTimeMillis()
                    )

                    Firebase.firestore.collection("submissions")
                        .add(submission)
                        .addOnSuccessListener {
                            isUploading = false
                            selectedChallenge = null
                            capturedBitmap = null
                            Toast.makeText(context, "上传成功！积分 +${submission["points"]}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                isUploading = false
                Toast.makeText(context, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- 界面 ---
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "EcoTrip 挑战",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(challengesList) { challenge ->
                    ChallengeItem(challenge) {
                        selectedChallenge = challenge
                        capturedBitmap = null
                    }
                }
            }
        }

        // 弹窗
        if (selectedChallenge != null) {
            AlertDialog(
                onDismissRequest = { selectedChallenge = null },
                title = { Text("挑战: ${selectedChallenge?.title}") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (capturedBitmap == null) {
                            Button(onClick = { cameraLauncher.launch(null) }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Text(" 拍张照")
                            }
                        } else {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { uploadSubmission() },
                        enabled = capturedBitmap != null && !isUploading
                    ) {
                        if (isUploading) Text("上传中...") else Text("确认提交")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedChallenge = null }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
fun ChallengeItem(c: Challenge, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = c.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = c.desc, fontSize = 14.sp, color = Color.Gray)
            }
            // 绿色的相机图标
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF00695C))
        }
    }
}