package com.enigma.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DevTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.enigma.tv.action.RUN_DEV_TESTS") {
            Log.i("EnigmaDevTest", "Received ADB command to run dev tests")
            
            // We use a CoroutineScope to run the tests in the background.
            // Note: Since this requires an Activity context for some WebViews,
            // we pass null for activity. This means some streams (like live games
            // that rely on WebView hook injections) might fail if they strictly require an Activity.
            // But it's sufficient for basic API-based stream resolution (Movies/TV).
            
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val viewModel = com.enigma.tv.ui.DevTestViewModel()
                    viewModel.runAllTests(context.applicationContext, null)
                    
                    // Wait until it's done
                    while (viewModel.state.value.isRunning) {
                        kotlinx.coroutines.delay(500)
                    }
                    Log.i("EnigmaDevTest", "ADB tests completed successfully")
                } catch (e: Exception) {
                    Log.e("EnigmaDevTest", "ADB tests failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
