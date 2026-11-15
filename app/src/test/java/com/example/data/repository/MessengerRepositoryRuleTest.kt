package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessengerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserAndCreateEncryptedChat_authenticatedOwner_succeedsAndEmitsChat() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MessengerRepository(firestore)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProvisioned(
                uid = uid,
                displayName = "Alice Facuedu",
                email = ALICE_EMAIL,
                photoUrl = null,
                publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
                keyFingerprint = "AA:BB:CC:DD:EE:FF"
            )
        }
        assertTrue(profileResult.isSuccess)

        val chatResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChat(
                title = "Notas Cifradas",
                chatType = "vault",
                memberUids = listOf(uid)
            )
        }
        assertTrue(chatResult.isSuccess)
        val chatId = chatResult.getOrThrow()

        val chats = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeChats(uid).first { list -> list.any { it.id == chatId } }
        }
        assertTrue(chats.any { it.id == chatId && it.encryptionAlgo == "AES-256-GCM" })

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendMessage(
                chatId = chatId,
                senderName = "Alice",
                memberUids = listOf(uid),
                ciphertext = "U2FsdGVkX1+abc123==",
                iv = "MTIzNDU2Nzg5MDEy",
                messageType = "text"
            )
        }
        assertTrue(msgResult.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(chatId, uid).first { it.isNotEmpty() }
        }
        assertEquals(1, messages.size)
    }

    @Test
    fun crossUserChatAccess_bobCannotReadAliceChat() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MessengerRepository(firestore)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createChat(
                title = "Bóveda Privada",
                chatType = "vault",
                memberUids = listOf(aliceUid)
            ).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = MessengerRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMessages(chatId, bobUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreEx = caught?.unwrapFirestoreException()
        if (firestoreEx != null) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    @Test
    fun startAndEndCall_terminalStateLock_enforcesEndedRule() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = MessengerRepository(firestore)

        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createChat("Sala Video", "vault", listOf(uid)).getOrThrow()
        }
        val callId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.startCall("Alice", "Sala Video", chatId, listOf(uid), "video").getOrThrow()
        }

        val endResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateCallStatus(callId, "ended", 15L)
        }
        assertTrue(endResult.isSuccess)

        val reopenResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateCallStatus(callId, "active", 20L)
        }
        assertTrue(reopenResult.isFailure)
        val ex = reopenResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeChats_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = MessengerRepository(firestore)
        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChats("unauth_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_facuedu@test.com"
        const val BOB_EMAIL = "bob_facuedu@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
