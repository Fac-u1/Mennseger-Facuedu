package com.example.security

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.crypto.CryptoManager
import com.example.data.repository.EphemeralGuestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

private const val TAG = "GuestSession"

data class EphemeralGuestSession(
    val uid: String,
    val displayName: String,
    val repository: EphemeralGuestRepository,
    val cryptoManager: CryptoManager
)

object GuestSessionController {
    private val _activeSession = MutableStateFlow<EphemeralGuestSession?>(null)
    val activeSession: StateFlow<EphemeralGuestSession?> = _activeSession.asStateFlow()

    fun startGuestSession(context: Context): EphemeralGuestSession {
        endAndWipeGuestSession(context)
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val guestUid = "guest_$suffix"
        val session = EphemeralGuestSession(
            uid = guestUid,
            displayName = "Invitado #$suffix",
            repository = EphemeralGuestRepository(guestUid),
            cryptoManager = CryptoManager(context.applicationContext, ephemeralOnly = true)
        )
        _activeSession.value = session
        Log.d(TAG, "Ephemeral guest session started: $guestUid")
        return session
    }

    fun endAndWipeGuestSession(context: Context) {
        val current = _activeSession.value
        if (current != null) {
            current.repository.wipeAllData()
            current.cryptoManager.wipeEphemeralKeys()
            _activeSession.value = null
            Log.d(TAG, "Guest account wiped from memory: ${current.uid}")
        }
        wipeDiskArtifacts(context)
    }

    fun wipeDiskArtifacts(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            appContext.cacheDir?.listFiles()?.forEach { file ->
                val name = file.name
                if (
                    name.startsWith("facuia_") ||
                    name.startsWith("voice_") ||
                    name.startsWith("audio_") ||
                    name == "exports"
                ) {
                    file.deleteRecursively()
                }
            }
        }
        runCatching {
            val docsDir = File(appContext.filesDir, "docs")
            if (docsDir.exists()) {
                docsDir.deleteRecursively()
            }
        }
    }
}

/**
 * Bound service whose onTaskRemoved hook executes when the user swipes the app from Recents,
 * guaranteeing immediate cleanup of the guest account and ephemeral temporary files.
 */
class GuestCleanupService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        GuestSessionController.endAndWipeGuestSession(applicationContext)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
