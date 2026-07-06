package com.enigma.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class WatchPartyState(
    val roomCode: String? = null,
    val isHost: Boolean = false,
    val isActive: Boolean = false,
    val memberCount: Int = 1,
    val syncPositionMs: Long = -1L,
    val syncIsPlaying: Boolean = true,
    val error: String? = null,
    val showDialog: Boolean = false
)

/**
 * Manages a Watch Party room using Firebase Realtime Database.
 *
 * "Loose sync" model: host broadcasts position every 2 seconds.
 * Guests seek if they drift more than 5 seconds from the host.
 */
class WatchPartyViewModel : ViewModel() {
    private val db = FirebaseDatabase.getInstance()

    private val _state = MutableStateFlow(WatchPartyState())
    val state: StateFlow<WatchPartyState> = _state.asStateFlow()

    private var syncListener: ValueEventListener? = null
    private var roomRef = db.getReference("watch_parties")

    /** Generate a random 5-digit room code and create the room as host */
    fun hostRoom(initialPositionMs: Long = 0L) {
        val code = (10000..99999).random().toString()
        viewModelScope.launch {
            try {
                val ref = roomRef.child(code)
                ref.setValue(mapOf(
                    "positionMs" to initialPositionMs,
                    "isPlaying" to true,
                    "members" to 1,
                    "hostSeed" to System.currentTimeMillis()
                )).await()
                _state.update { it.copy(
                    roomCode = code,
                    isHost = true,
                    isActive = true,
                    error = null
                )}
                listenToRoom(code)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to create room: ${e.message}") }
            }
        }
    }

    /** Join an existing room as a guest */
    fun joinRoom(code: String) {
        viewModelScope.launch {
            try {
                val snapshot = roomRef.child(code).get().await()
                if (!snapshot.exists()) {
                    _state.update { it.copy(error = "Room not found") }
                    return@launch
                }
                // Increment member count
                val curMembers = snapshot.child("members").getValue(Long::class.java) ?: 1
                roomRef.child(code).child("members").setValue(curMembers + 1).await()
                _state.update { it.copy(
                    roomCode = code,
                    isHost = false,
                    isActive = true,
                    error = null
                )}
                listenToRoom(code)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to join room: ${e.message}") }
            }
        }
    }

    /** Called by the host to broadcast current playback state */
    fun broadcastState(positionMs: Long, isPlaying: Boolean) {
        val code = _state.value.roomCode ?: return
        if (!_state.value.isHost) return
        roomRef.child(code).updateChildren(mapOf(
            "positionMs" to positionMs,
            "isPlaying" to isPlaying
        ))
    }

    /** Listen to room updates */
    private fun listenToRoom(code: String) {
        val ref = roomRef.child(code)
        syncListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posMs = snapshot.child("positionMs").getValue(Long::class.java) ?: return
                val playing = snapshot.child("isPlaying").getValue(Boolean::class.java) ?: true
                val members = snapshot.child("members").getValue(Long::class.java)?.toInt() ?: 1
                _state.update { it.copy(
                    syncPositionMs = posMs,
                    syncIsPlaying = playing,
                    memberCount = members
                )}
            }
            override fun onCancelled(error: DatabaseError) {
                _state.update { it.copy(error = error.message) }
            }
        }
        ref.addValueEventListener(syncListener!!)
    }

    fun showDialog() = _state.update { it.copy(showDialog = true) }
    fun hideDialog() = _state.update { it.copy(showDialog = false) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun leaveRoom() {
        val code = _state.value.roomCode ?: return
        syncListener?.let { roomRef.child(code).removeEventListener(it) }
        syncListener = null
        if (_state.value.isHost) {
            // Delete the room
            roomRef.child(code).removeValue()
        } else {
            val curMembers = _state.value.memberCount
            if (curMembers > 1) roomRef.child(code).child("members").setValue(curMembers - 1)
        }
        _state.update { WatchPartyState() }
    }

    override fun onCleared() {
        super.onCleared()
        leaveRoom()
    }
}
