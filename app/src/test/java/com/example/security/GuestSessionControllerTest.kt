package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestSessionControllerTest {

    @Test
    fun guestSession_encryptsAndWipesCompletelyOnExit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = GuestSessionController.startGuestSession(context)

        assertTrue(session.uid.startsWith("guest_"))
        assertNotNull(GuestSessionController.activeSession.value)

        // Ensure ephemeral keypair does NOT persist to SharedPreferences
        val prefs = context.getSharedPreferences("facuedu_e2ee_keys", Context.MODE_PRIVATE)
        assertEquals(0, prefs.all.size)

        val chatId = session.repository.createChat(
            title = "Bóveda Invitado",
            chatType = "vault",
            memberUids = listOf(session.uid)
        ).getOrThrow()

        val encrypted = session.cryptoManager.encryptText("Hola modo prueba", chatId)
        session.repository.sendMessage(
            chatId = chatId,
            senderName = session.displayName,
            memberUids = listOf(session.uid),
            ciphertext = encrypted.ciphertextBase64,
            iv = encrypted.ivBase64
        ).getOrThrow()

        val msgs = session.repository.observeMessages(chatId, session.uid).first()
        assertEquals(1, msgs.size)
        assertEquals("Hola modo prueba", session.cryptoManager.decryptText(msgs[0].ciphertext, msgs[0].iv, chatId))

        // End and wipe guest account
        GuestSessionController.endAndWipeGuestSession(context)

        assertNull(GuestSessionController.activeSession.value)
        assertEquals(0, session.repository.observeChats(session.uid).first().size)
        assertEquals(0, session.repository.observeMessages(chatId, session.uid).first().size)
    }
}
