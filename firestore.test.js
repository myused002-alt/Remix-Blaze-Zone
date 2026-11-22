const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validProfilePayload(uid, timestamp) {
  return {
    userId: uid,
    callsign: "BlazeHunter",
    rankPoints: 1000,
    totalMatches: 0,
    booyahs: 0,
    totalKills: 0,
    totalDamage: 0,
    goldCoins: 500,
    selectedCharacter: "HAYATO",
    selectedStarterSkin: "SCORCH",
    unlockedCharacters: ["HAYATO"],
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validMatchPayload(uid, timestamp) {
  return {
    userId: uid,
    mode: "CLASSIC",
    placement: 1,
    kills: 7,
    damageDealt: 1420,
    survivalSeconds: 185,
    rankPointsDelta: 45,
    coinsEarned: 180,
    victory: true,
    createdAt: timestamp,
  };
}

test("Unauthenticated user cannot read or write profiles or matches", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("matches").get()
  );
});

test("Authenticated owner can create and update own profile with Kotlin-mirrored payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  // Action 1: Record match stats
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      rankPoints: 1045,
      totalMatches: 1,
      booyahs: 1,
      totalKills: 7,
      totalDamage: 1420,
      goldCoins: 680,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Loadout update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedCharacter: "KELLY",
      unlockedCharacters: ["HAYATO", "KELLY"],
      goldCoins: 380,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc("extra_id").set(
      validProfilePayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );
});

test("Authenticated user cannot access or modify another user's profile or matches", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, created));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("matches")
      .doc("match_1")
      .set(validMatchPayload(BOB_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      goldCoins: 9999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("matches").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("matches")
      .doc("match_2")
      .set(validMatchPayload(BOB_UID, created))
  );
});

test("Authenticated owner can create and list match records, and cannot mutate them", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("matches")
      .doc("match_001")
      .set(validMatchPayload(ALICE_UID, created))
  );

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("matches")
      .doc("match_002")
      .set({
        ...validMatchPayload(ALICE_UID, created),
        id: "match_002",
      })
  );

  // List own matches succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").get()
  );

  // Match records are immutable
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("matches")
      .doc("match_001")
      .update({ kills: 20 })
  );
});
