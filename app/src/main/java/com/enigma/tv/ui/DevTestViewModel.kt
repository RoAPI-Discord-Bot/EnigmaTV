package com.enigma.tv.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.StreamExtractor
import com.enigma.tv.data.StreamPlaybackResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

enum class TestStatus {
    IDLE, RUNNING, SUCCESS, FAILED
}

enum class DevTestType {
    MOVIE, TV_SHOW, LIVE_TV
}

data class TestCase(
    val id: String,
    val name: String,
    val type: DevTestType,
    val tmdbId: Int? = null,
    val embedUrl: String? = null,
    val status: TestStatus = TestStatus.IDLE,
    val durationMs: Long = 0,
    val error: String? = null
)

data class DevTestState(
    val tests: List<TestCase> = listOf(
        TestCase("1", "Backrooms (2026)", DevTestType.MOVIE, tmdbId = 1083381),
        TestCase("2", "Deadpool & Wolverine", DevTestType.MOVIE, tmdbId = 533535),
        TestCase("3", "The Boys S1 E1", DevTestType.TV_SHOW, tmdbId = 76479),
        TestCase("4", "Live Channel: ESPN", DevTestType.LIVE_TV, embedUrl = "https://embed.st/embed/admin/espn/1"),
        TestCase("5", "Live Game: Heat vs Magic", DevTestType.LIVE_TV, embedUrl = "https://embed.st/embed/admin/ppv-miami-heat-vs-orlando-magic/1"),
        TestCase("6", "Live Game: Angels vs Twins", DevTestType.LIVE_TV, embedUrl = "https://embed.st/embed/admin/ppv-los-angeles-angels-vs-minnesota-twins/1")
    ),
    val isRunning: Boolean = false
)

class DevTestViewModel : ViewModel() {
    private val _state = MutableStateFlow(DevTestState())
    val state: StateFlow<DevTestState> = _state.asStateFlow()

    fun runAllTests(context: Context, activity: Activity) {
        if (_state.value.isRunning) return
        
        _state.update { it.copy(isRunning = true) }
        
        val testsToRun = _state.value.tests.map { it.copy(status = TestStatus.IDLE, durationMs = 0, error = null) }
        _state.update { it.copy(tests = testsToRun) }

        viewModelScope.launch {
            val jobs = testsToRun.map { test ->
                launch {
                    updateTestStatus(test.id, TestStatus.RUNNING)
                    
                    try {
                        var success = false
                        val time = measureTimeMillis {
                            success = withContext(Dispatchers.IO) {
                                if (test.type == DevTestType.LIVE_TV) {
                                    val url = test.embedUrl ?: return@withContext false
                                    val resolved = StreamExtractor(context).extractStreamUrl(
                                        embedUrl = url,
                                        activity = activity
                                    )
                                    resolved != null
                                } else {
                                    val cType = if (test.type == DevTestType.MOVIE) ContentType.MOVIE else ContentType.TV
                                    val resolved = StreamPlaybackResolver.resolve(
                                        context = context,
                                        embedUrl = "",
                                        activity = activity,
                                        tmdbId = test.tmdbId,
                                        type = cType,
                                        season = 1,
                                        episode = 1
                                    )
                                    resolved != null
                                }
                            }
                        }
                        
                        if (success) {
                            updateTestStatus(test.id, TestStatus.SUCCESS, time)
                        } else {
                            updateTestStatus(test.id, TestStatus.FAILED, time, "No stream found")
                        }
                    } catch (e: Exception) {
                        updateTestStatus(test.id, TestStatus.FAILED, 0, e.message ?: "Unknown error")
                    }
                }
            }
            jobs.forEach { it.join() }
            _state.update { it.copy(isRunning = false) }
        }
    }
    
    private fun updateTestStatus(id: String, status: TestStatus, duration: Long = 0, error: String? = null) {
        _state.update { state ->
            val updatedTests = state.tests.map {
                if (it.id == id) it.copy(status = status, durationMs = duration, error = error) else it
            }
            state.copy(tests = updatedTests)
        }
    }
}
