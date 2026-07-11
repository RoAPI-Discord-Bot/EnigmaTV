package com.enigma.tv.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import com.enigma.tv.util.findActivity

@Composable
fun DevTestScreen(
    onClose: () -> Unit,
    viewModel: DevTestViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as Activity }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Developer Stream Testing",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.runAllTests(context, activity) },
                    enabled = !state.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isRunning) "Running..." else "Run Tests")
                }
            }
            
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.tests) { test ->
                TestItemRow(test)
            }
        }
    }
}

@Composable
fun TestItemRow(test: TestCase) {
    Surface(
        color = Color(0xFF1E1E1E),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = test.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Type: ${test.type.name}",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                if (test.error != null) {
                    Text(
                        text = "Error: ${test.error}",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (test.durationMs > 0) {
                    Text(
                        text = "${test.durationMs} ms",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
                
                when (test.status) {
                    TestStatus.IDLE -> {
                        Text("Ready", color = TextSecondary)
                    }
                    TestStatus.RUNNING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = EnigmaPurple,
                            strokeWidth = 2.dp
                        )
                    }
                    TestStatus.SUCCESS -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    TestStatus.FAILED -> {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Failed",
                            tint = Color.Red,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
