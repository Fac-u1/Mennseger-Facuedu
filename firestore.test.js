const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list chats or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("calls").get());
});

test("Authenticated user can create and read own profile, cannot read Bob's private profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "SHA256-ALICE-FP-01",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Bob cannot read Alice's private PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      isAdmin: true,
      updatedAt: nowTs(),
    })
  );
});

test("Encrypted chat & message creation, member isolation, and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const chatRef = aliceDb.collection("chats").doc("chat_alice_1");
  await assertSucceeds(
    chatRef.set({
      createdBy: ALICE_UID,
      title: "Notas Cifradas",
      chatType: "vault",
      memberUids: [ALICE_UID],
      encryptionAlgo: "AES-256-GCM",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Alice sends encrypted message
  await assertSucceeds(
    chatRef.collection("messages").doc("msg_1").set({
      chatId: "chat_alice_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID],
      ciphertext: "YWJjZGVmMTIzNDU2Nzg5MA==",
      iv: "MTIzNDU2Nzg5MDEy",
      messageType: "text",
      createdAt: nowTs(),
    })
  );

  // Aligned query succeeds for Alice
  await assertSucceeds(
    aliceDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Unfiltered query fails (rules are not filters)
  await assertFails(aliceDb.collection("chats").get());

  // Bob cannot read Alice's private chat or message
  await assertFails(bobDb.collection("chats").doc("chat_alice_1").get());
  await assertFails(bobDb.collection("chats").doc("chat_alice_1").collection("messages").doc("msg_1").get());

  // Messages are append-only (update denied even for sender)
  await assertFails(
    chatRef.collection("messages").doc("msg_1").update({
      ciphertext: "dGFtcGVyZWQ=",
    })
  );
});

test("Video call session enforces terminal state locking on ended calls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const callRef = aliceDb.collection("calls").doc("call_1");

  await assertSucceeds(
    callRef.set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeName: "Bob",
      chatId: "chat_alice_1",
      memberUids: [ALICE_UID],
      callType: "video",
      status: "active",
      isEncrypted: true,
      durationSeconds: 0,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    callRef.update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: nowTs(),
    })
  );

  // Terminal state lock: cannot update after status is 'ended'
  await assertFails(
    callRef.update({
      status: "active",
      durationSeconds: 50,
      updatedAt: nowTs(),
    })
  );
});

test("FacuIA turns are strictly user-scoped and append-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("aiTurns").doc("turn_1").set({
      userId: ALICE_UID,
      role: "user",
      mode: "rewrite",
      text: "Cambia esta frase a tono profesional",
      modelUsed: "gemini-3.8-flash",
      createdAt: nowTs(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("aiTurns").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("aiTurns").doc("turn_1").update({
      text: "Modified",
    })
  );
});
