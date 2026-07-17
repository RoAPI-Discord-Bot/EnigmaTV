package com.enigma.tv.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.StreamExtractor
import com.enigma.tv.data.StreamPlaybackResolver
import com.enigma.tv.data.StreamPlaybackTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

enum class TestStatus(val label: String) {
    IDLE("Ready"),
    RESOLVING("Finding stream..."),
    PLAYING("Loading stream..."),
    SUCCESS("Success"),
    FAILED("Failed")
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
    val error: String? = null,
    val hasCaptions: Boolean? = null
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

    fun runAllTests(context: Context, activity: Activity?) {
        if (_state.value.isRunning) return
        
        _state.update { it.copy(isRunning = true) }
        
        val repo = com.enigma.tv.data.StreamedRepository()
        
        viewModelScope.launch {
            val liveMatches = try {
                repo.loadEvents().filter { it.sources.isNotEmpty() }.take(2)
            } catch (e: Exception) {
                emptyList()
            }
            
            val liveTestCases = liveMatches.mapIndexedNotNull { index, match ->
                val source = match.sources.firstOrNull() ?: return@mapIndexedNotNull null
                val streams = try { repo.fetchStreams(source.source, source.id) } catch (e: Exception) { emptyList() }
                val embedUrl = streams.firstOrNull()?.embedUrl ?: return@mapIndexedNotNull null
                TestCase("live_game_$index", "Live Game: ${match.title}", DevTestType.LIVE_TV, embedUrl = embedUrl)
            }
            
            // Rebuild the tests list with the dynamic live games
            val baseTests = _state.value.tests.filter { !it.name.startsWith("Live Game:") }
            val testsToRun = (baseTests + liveTestCases).map { it.copy(status = TestStatus.IDLE, durationMs = 0, error = null, hasCaptions = null) }
            
            _state.update { it.copy(tests = testsToRun) }

            // Run tests SEQUENTIALLY to avoid crashing the device with 6 simultaneous ExoPlayers
            for (test in testsToRun) {
                Log.i("EnigmaDevTest", "Starting test: ${test.name}")
                updateTestStatus(test.id, TestStatus.RESOLVING)
                
                try {
                    var resolvedStream: com.enigma.tv.data.ResolvedStream? = null
                    
                    val resolveTime = measureTimeMillis {
                        resolvedStream = withContext(Dispatchers.IO) {
                            if (test.type == DevTestType.LIVE_TV) {
                                val url = test.embedUrl ?: return@withContext null
                                StreamExtractor(context).extractStreamUrl(
                                    embedUrl = url,
                                    activity = activity
                                )
                            } else {
                                val cType = if (test.type == DevTestType.MOVIE) ContentType.MOVIE else ContentType.TV
                                StreamPlaybackResolver.resolve(
                                    context = context,
                                    embedUrl = "",
                                    activity = activity,
                                    tmdbId = test.tmdbId,
                                    type = cType,
                                    season = 1,
                                    episode = 1
                                )
                            }
                        }
                    }

                    if (resolvedStream == null) {
                        Log.e("EnigmaDevTest", "[FAILED] ${test.name}: No stream found")
                        updateTestStatus(test.id, TestStatus.FAILED, resolveTime, "No stream found")
                        continue
                    }
                    
                    Log.i("EnigmaDevTest", "[RESOLVED] ${test.name} in ${resolveTime}ms. Testing playback...")
                    updateTestStatus(test.id, TestStatus.PLAYING, resolveTime)

                    val playTime = measureTimeMillis {
                        val result = StreamPlaybackTester.testPlayback(context, resolvedStream!!)
                        if (result.success) {
                            Log.i("EnigmaDevTest", "[SUCCESS] ${test.name} plays correctly. Captions: ${result.hasCaptions}")
                            updateTestStatus(
                                id = test.id,
                                status = TestStatus.SUCCESS,
                                duration = resolveTime,
                                hasCaptions = result.hasCaptions
                            )
                        } else {
                            Log.e("EnigmaDevTest", "[FAILED] ${test.name}: Playback failed - ${result.error}")
                            updateTestStatus(test.id, TestStatus.FAILED, resolveTime, "Playback: ${result.error}")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("EnigmaDevTest", "[ERROR] ${test.name}: ${e.message}")
                    updateTestStatus(test.id, TestStatus.FAILED, 0, e.message ?: "Unknown error")
                }
            }
            
            Log.i("EnigmaDevTest", "All tests completed.")
            _state.update { it.copy(isRunning = false) }
        }
    }
    
    private fun updateTestStatus(
        id: String,
        status: TestStatus,
        duration: Long = 0,
        error: String? = null,
        hasCaptions: Boolean? = null
    ) {
        _state.update { state ->
            val updatedTests = state.tests.map {
                if (it.id == id) {
                    it.copy(
                        status = status,
                        durationMs = if (duration > 0) duration else it.durationMs,
                        error = error ?: it.error,
                        hasCaptions = hasCaptions ?: it.hasCaptions
                    )
                } else it
            }
            state.copy(tests = updatedTests)
        }
    }
}
