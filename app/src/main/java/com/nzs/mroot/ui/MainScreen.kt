package com.nzs.mroot.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzs.mroot.R
import df.root.ExploitRunner
import df.root.IReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "dfroot"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val deContext = remember { context.createDeviceProtectedStorageContext() }
    val coroutineScope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var isDeviceHooked by remember { mutableStateOf(File("/dev/df").exists()) }
    var outputLogs by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val isSuManagerInstalled = remember {
        ExploitRunner.resolveManager(deContext) { /* no-op reporter */ } != null
    }
    var showSuRequiredDialog by remember { mutableStateOf(!isSuManagerInstalled) }

    LaunchedEffect(outputLogs) {
        if (outputLogs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    if (showSuRequiredDialog) {
        AlertDialog(
            onDismissRequest = { /* Non-cancelable */ },
            title = {
                Text(
                    text = "SU Manager Required",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "SU Manager is not installed.\n\n" +
                            "Samsung devices: install from github.com/diabl0w/KernelSU\n\n" +
                            "Other devices: github.com/tiann/KernelSU, github.com/KernelSU-Next/KernelSU-Next, or github.com/KOWX712/KernelSU",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("Exit")
                }
            }
        )
    }

    fun runExploit() {
        if (isRunning) return
        isRunning = true
        outputLogs = ""

        coroutineScope.launch {
            com.nzs.mroot.util.AppLogger.appendLine(context, "=== Starting exploit execution ===")
            val reporter = IReporter { msg ->
                com.nzs.mroot.util.AppLogger.append(context, msg)
                outputLogs += msg
            }

            var rc = -1
            try {
                rc = withContext(Dispatchers.IO) {
                    ExploitRunner.run(deContext, reporter)
                }

                val resultMsg = when (rc) {
                    0 -> "SUCCESS"
                    1 -> "FAILED: ksud exited with error"
                    2 -> "FAILED: check logs"
                    else -> "FAILED: failed to patch files"
                }
                com.nzs.mroot.util.AppLogger.appendLine(context, "=== Execution finished: $resultMsg (rc=$rc) ===")
                Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "exploit exception", e)
                val exMsg = "\nexception: $e\n"
                com.nzs.mroot.util.AppLogger.appendLine(context, "EXCEPTION ERROR: $e\n${e.stackTraceToString()}")
                outputLogs += exMsg
                Toast.makeText(context, "DFRoot Exception: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isRunning = false
                isDeviceHooked = File("/dev/df").exists()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MRoot",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "iSkyline7 (Credits:: @diabl0w)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "DIRTYFRAG (CVE-2026-43284)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Button(
                        onClick = { runExploit() },
                        enabled = !isRunning && !isDeviceHooked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Exploit...")
                        } else if (isDeviceHooked) {
                            Text("Already Hooked (/dev/df exists)")
                        } else {
                            Text("Launch Root")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = if (outputLogs.isEmpty()) "Ready. Press 'Launch Root' to start." else outputLogs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (outputLogs.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }
        }
    }
}
