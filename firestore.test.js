const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_dev_001";
const BOB_UID = "bob_user_002";
const ADMIN_UID = "admin_super_999";

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

async function seedAdmin() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "admin@appstore.dev",
      role: "super_admin",
      createdAt: new Date(Date.now() - 10000),
    });
  });
}

test("Users collection: PII isolation, self-registration, and privilege escalation prevention", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const past = new Date(Date.now() - 5000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());

  // Reject self-assigned admin or verified developer on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      accountType: "admin",
      developerStatus: "none",
      accountStatus: "active",
      createdAt: past,
      lastLoginAt: past,
    })
  );

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      accountType: "user",
      developerStatus: "none",
      accountStatus: "active",
      createdAt: past,
      lastLoginAt: past,
    })
  );

  // Cross-user PII read fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Valid partial update (requesting developer access none -> pending)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      developerStatus: "pending",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Tampering accountStatus fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "banned",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Apps catalog: public reads only approved apps; developers cannot self-approve; admin can approve", async () => {
  await seedAdmin();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Developer creates pending app
  await assertSucceeds(
    aliceDb.collection("apps").doc("app_1").set({
      appId: "app_1",
      ownerUid: ALICE_UID,
      appName: "Focus Flow",
      packageName: "com.alice.focusflow",
      categoryId: "productivity",
      categoryName: "Productivity",
      categoryGroup: "apps",
      shortDescription: "Minimalist timer",
      description: "A clean productivity focus timer for Android devices.",
      developerName: "Alice Studio",
      versionName: "1.0.0",
      versionCode: 1,
      apkSizeBytes: 5242880,
      minSdk: 24,
      targetSdk: 35,
      ageRating: "Everyone",
      status: "pending",
      featured: false,
      editorsChoice: false,
      trendingScore: 0,
      averageRating: 0,
      totalReviews: 0,
      downloadCount: 0,
      viewCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Developer cannot create directly as approved
  await assertFails(
    aliceDb.collection("apps").doc("app_bypass").set({
      appId: "app_bypass",
      ownerUid: ALICE_UID,
      appName: "Bypass App",
      packageName: "com.alice.bypass",
      categoryId: "tools",
      description: "Trying to bypass admin approval workflow.",
      versionName: "1.0.0",
      versionCode: 1,
      status: "approved",
      averageRating: 0,
      downloadCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Unauthenticated user cannot read pending app or unfiltered list
  await assertFails(unauthDb.collection("apps").doc("app_1").get());
  await assertFails(unauthDb.collection("apps").get());

  // Unauthenticated query with status == approved succeeds (returns empty before approval)
  await assertSucceeds(unauthDb.collection("apps").where("status", "==", "approved").get());

  // Developer cannot self-approve
  await assertFails(
    aliceDb.collection("apps").doc("app_1").update({
      status: "approved",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin approves app_1
  await assertSucceeds(
    adminDb.collection("apps").doc("app_1").update({
      status: "approved",
      featured: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Now unauthenticated user can read approved app
  await assertSucceeds(unauthDb.collection("apps").doc("app_1").get());
  await assertSucceeds(unauthDb.collection("apps").where("status", "==", "approved").get());
});

test("Reviews, wishlists, downloads, and analytics security enforcement", async () => {
  await seedAdmin();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("apps").doc("app_1").set({
      appId: "app_1",
      ownerUid: ALICE_UID,
      appName: "Focus Flow",
      packageName: "com.alice.focusflow",
      categoryId: "productivity",
      description: "A clean productivity focus timer for Android devices.",
      versionName: "1.0.0",
      versionCode: 1,
      status: "approved",
      averageRating: 0,
      downloadCount: 0,
      createdAt: past,
      updatedAt: past,
    });
  });

  // Pre-existence get() before creating review succeeds
  await assertSucceeds(bobDb.collection("reviews").doc("app_1_" + BOB_UID).get());

  // One review per user enforced by doc ID appId_uid
  await assertSucceeds(
    bobDb.collection("reviews").doc("app_1_" + BOB_UID).set({
      appId: "app_1",
      userId: BOB_UID,
      userName: "Bob",
      rating: 5,
      comment: "Super helpful and smooth!",
      helpfulCount: 0,
      moderationStatus: "visible",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Helpful vote pre-check + vote + increment helpfulCount
  const voteDocId = "app_1_" + BOB_UID + "_" + ALICE_UID;
  await assertSucceeds(aliceDb.collection("reviewVotes").doc(voteDocId).get());
  await assertSucceeds(
    aliceDb.collection("reviewVotes").doc(voteDocId).set({
      reviewId: "app_1_" + BOB_UID,
      userId: ALICE_UID,
      helpful: true,
      createdAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("reviews").doc("app_1_" + BOB_UID).update({
      helpfulCount: 1,
    })
  );

  // Spoofed review ID rejected
  await assertFails(
    bobDb.collection("reviews").doc("arbitrary_review_id").set({
      appId: "app_1",
      userId: BOB_UID,
      userName: "Bob",
      rating: 5,
      comment: "Spam review",
      helpfulCount: 0,
      moderationStatus: "visible",
      createdAt: past,
      updatedAt: past,
    })
  );

  // App owner Alice can post developer reply
  await assertSucceeds(
    aliceDb.collection("reviews").doc("app_1_" + BOB_UID).update({
      developerReply: "Thank you for the feedback!",
      developerReplyAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Wishlist scoped to owner
  await assertSucceeds(
    bobDb.collection("wishlists").doc(BOB_UID + "_app_1").set({
      userId: BOB_UID,
      appId: "app_1",
      appName: "Focus Flow",
      createdAt: past,
    })
  );
  await assertFails(aliceDb.collection("wishlists").where("userId", "==", BOB_UID).get());
  await assertSucceeds(bobDb.collection("wishlists").where("userId", "==", BOB_UID).get());

  // Developer cannot tamper developerAnalytics
  await assertFails(
    aliceDb.collection("developerAnalytics").doc("app_1").set({
      ownerUid: ALICE_UID,
      appId: "app_1",
      totalDownloads: 999999,
      totalViews: 999999,
      updatedAt: past,
    })
  );

  // Download history lifecycle: downloading -> paused -> verified, owner read/delete, non-owner blocked
  const dlDocId = BOB_UID + "_app_1_v1";
  await assertSucceeds(bobDb.collection("downloads").doc(dlDocId).get());
  await assertSucceeds(
    bobDb.collection("downloads").doc(dlDocId).set({
      userId: BOB_UID,
      appId: "app_1",
      versionId: "app_1_v1",
      appName: "Focus Flow",
      packageName: "com.alice.focusflow",
      versionName: "1.0.0",
      versionCode: 1,
      fileSizeBytes: 5242880,
      downloadedBytes: 1048576,
      sha256: "abc123",
      status: "paused",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(aliceDb.collection("downloads").doc(dlDocId).get());
  await assertSucceeds(
    bobDb.collection("downloads").doc(dlDocId).update({
      status: "verified",
      downloadedBytes: 5242880,
      fileSizeBytes: 5242880,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Signed-in user can increment approved app downloadCount by exact +1, but NOT +5 or other fields
  await assertSucceeds(
    bobDb.collection("apps").doc("app_1").update({
      downloadCount: 1,
    })
  );
  await assertFails(
    bobDb.collection("apps").doc("app_1").update({
      downloadCount: 6,
    })
  );

  // Owner can remove their download record
  await assertSucceeds(bobDb.collection("downloads").doc(dlDocId).delete());
});

test("Bootstrapped admin email requires email_verified == true", async () => {
  const spoofedAdmin = testEnv.authenticatedContext("spoof_uid", {
    email: "monicaeg6wbestewart@gmail.com",
    email_verified: false,
  }).firestore();
  const realAdmin = testEnv.authenticatedContext("real_uid", {
    email: "monicaeg6wbestewart@gmail.com",
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 5000);

  await assertFails(
    spoofedAdmin.collection("categories").doc("action").set({
      name: "Action",
      group: "games",
      iconName: "sports_esports",
      sortOrder: 1,
      enabled: true,
      createdAt: past,
    })
  );

  await assertSucceeds(
    realAdmin.collection("categories").doc("action").set({
      name: "Action",
      group: "games",
      iconName: "sports_esports",
      sortOrder: 1,
      enabled: true,
      createdAt: past,
    })
  );
});
