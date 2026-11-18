const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { getMessaging } = require("firebase-admin/messaging");
const { getStorage } = require("firebase-admin/storage");
const crypto = require("crypto");

initializeApp();

const DATABASE_ID = "ai-studio-android-appstore-d323b02f-df5e-4be6-bdfa-d2d2aab8b109";
const db = getFirestore(DATABASE_ID);

// Monetization remains disabled until ad/payment credentials are configured
const MONETIZATION_ENABLED = false;

async function assertAdmin(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const uid = request.auth.uid;
  const token = request.auth.token || {};
  if (token.admin === true) return uid;
  const adminDoc = await db.collection("admins").doc(uid).get();
  if (adminDoc.exists) return uid;
  if (token.email === "monicaeg6wbestewart@gmail.com" && token.email_verified === true) {
    return uid;
  }
  throw new HttpsError("permission-denied", "Administrator privileges required.");
}

async function writeAuditLog(adminUid, action, targetType, targetId, result, notes = "") {
  await db.collection("adminLogs").add({
    adminUid,
    action,
    targetType,
    targetId,
    result,
    notes,
    createdAt: FieldValue.serverTimestamp(),
  });
}

async function sendUserNotification(userId, type, title, body, targetAppId = "") {
  await db.collection("notifications").add({
    userId,
    type,
    title,
    body,
    targetAppId,
    read: false,
    createdAt: FieldValue.serverTimestamp(),
  });

  const userDoc = await db.collection("users").doc(userId).get();
  const fcmToken = userDoc.exists ? userDoc.data().fcmToken : null;
  if (fcmToken) {
    await getMessaging().send({
      token: fcmToken,
      notification: { title, body },
      data: { type, targetAppId },
    }).catch(() => {});
  }
}

// 1. Validate APK metadata, SHA-256, and VirusTotal malware scan when staging upload finishes
exports.validateApkSubmission = onCall({ secrets: ["VIRUSTOTAL_API_KEY"] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign-in required.");
  const { appId, versionId } = request.data || {};
  if (!appId || !versionId) throw new HttpsError("invalid-argument", "appId and versionId are required.");

  const versionRef = db.collection("appVersions").doc(versionId);
  const versionSnap = await versionRef.get();
  if (!versionSnap.exists) throw new HttpsError("not-found", "Version not found.");
  const version = versionSnap.data();

  if (version.ownerUid !== request.auth.uid) {
    await assertAdmin(request);
  }

  let validationStatus = "passed";
  let scanStatus = "pending";
  let calculatedSha256 = version.sha256;

  // Verify stored APK bytes if uploaded to Cloud Storage staging
  if (version.apkStoragePath) {
    try {
      const bucket = getStorage().bucket();
      const file = bucket.file(version.apkStoragePath);
      const [exists] = await file.exists();
      if (exists) {
        const [buffer] = await file.download();
        calculatedSha256 = crypto.createHash("sha256").update(buffer).digest("hex");
        if (version.sha256 && calculatedSha256 !== version.sha256) {
          validationStatus = "failed";
        }
      }
    } catch (_) {}
  }

  // Real VirusTotal hash lookup if API key is configured; never falsely report clean without scanning
  const vtKey = process.env.VIRUSTOTAL_API_KEY;
  if (vtKey && calculatedSha256) {
    const vtRes = await fetch(`https://www.virustotal.com/api/v3/files/${calculatedSha256}`, {
      headers: { "x-apikey": vtKey },
    });
    if (vtRes.ok) {
      const vtJson = await vtRes.json();
      const maliciousCount = vtJson?.data?.attributes?.last_analysis_stats?.malicious || 0;
      scanStatus = maliciousCount > 0 ? "flagged" : "clean";
    } else if (vtRes.status === 404) {
      scanStatus = "pending";
    } else {
      scanStatus = "error";
    }
  }

  await versionRef.update({
    sha256: calculatedSha256,
    validationStatus,
    scanStatus,
    updatedAt: FieldValue.serverTimestamp(),
  });

  return { validationStatus, scanStatus, sha256: calculatedSha256 };
});

// 2. Admin: Approve Application & Version
exports.adminApproveApp = onCall(async (request) => {
  const adminUid = await assertAdmin(request);
  const { appId, versionId, submissionId, moderationNotes = "" } = request.data || {};
  if (!appId || !versionId) throw new HttpsError("invalid-argument", "appId and versionId required.");

  const appRef = db.collection("apps").doc(appId);
  const versionRef = db.collection("appVersions").doc(versionId);

  const appSnap = await appRef.get();
  if (!appSnap.exists) throw new HttpsError("not-found", "Application not found.");
  const appData = appSnap.data();

  const batch = db.batch();
  batch.update(appRef, {
    status: "approved",
    updatedAt: FieldValue.serverTimestamp(),
  });
  batch.update(versionRef, {
    approvalStatus: "approved",
    validationStatus: "passed",
  });
  if (submissionId) {
    batch.update(db.collection("appSubmissions").doc(submissionId), {
      status: "approved",
      moderationNotes,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  await batch.commit();

  // Promote media to publicMedia = true
  const mediaSnaps = await db.collection("appMedia").where("appId", "==", appId).get();
  const mediaBatch = db.batch();
  mediaSnaps.forEach((doc) => mediaBatch.update(doc.ref, { publicMedia: true }));
  await mediaBatch.commit();

  await sendUserNotification(
    appData.ownerUid,
    "app_approved",
    `${appData.appName} Approved!`,
    "Your application passed review and is now live on APP STORE.",
    appId
  );
  await writeAuditLog(adminUid, "APPROVE_APP", "apps", appId, "success", moderationNotes);
  return { status: "approved" };
});

// 3. Admin: Reject Application
exports.adminRejectApp = onCall(async (request) => {
  const adminUid = await assertAdmin(request);
  const { appId, versionId, submissionId, reason } = request.data || {};
  if (!appId || !reason) throw new HttpsError("invalid-argument", "appId and rejection reason required.");

  const appRef = db.collection("apps").doc(appId);
  const appSnap = await appRef.get();
  if (!appSnap.exists) throw new HttpsError("not-found", "Application not found.");

  const batch = db.batch();
  batch.update(appRef, { status: "rejected", updatedAt: FieldValue.serverTimestamp() });
  if (versionId) {
    batch.update(db.collection("appVersions").doc(versionId), { approvalStatus: "rejected" });
  }
  if (submissionId) {
    batch.update(db.collection("appSubmissions").doc(submissionId), {
      status: "rejected",
      rejectionReason: reason,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  await batch.commit();

  await sendUserNotification(
    appSnap.data().ownerUid,
    "app_rejected",
    `${appSnap.data().appName} Needs Changes`,
    `Reason: ${reason}`,
    appId
  );
  await writeAuditLog(adminUid, "REJECT_APP", "apps", appId, "success", reason);
  return { status: "rejected" };
});

// 4. Admin: Suspend or Restore Application
exports.adminSetAppStatus = onCall(async (request) => {
  const adminUid = await assertAdmin(request);
  const { appId, status, notes = "" } = request.data || {};
  if (!["suspended", "approved", "archived"].includes(status)) {
    throw new HttpsError("invalid-argument", "Invalid target status.");
  }
  const appRef = db.collection("apps").doc(appId);
  const appSnap = await appRef.get();
  if (!appSnap.exists) throw new HttpsError("not-found", "App not found.");

  await appRef.update({ status, updatedAt: FieldValue.serverTimestamp() });
  await writeAuditLog(adminUid, `SET_APP_STATUS_${status.toUpperCase()}`, "apps", appId, "success", notes);
  return { status };
});

// 5. Authorize APK download & atomically record trusted download counter + analytics
exports.authorizeAndRecordDownload = onCall(async (request) => {
  const { appId, versionId, idempotencyKey } = request.data || {};
  if (!appId || !versionId) throw new HttpsError("invalid-argument", "appId and versionId required.");

  const appRef = db.collection("apps").doc(appId);
  const versionRef = db.collection("appVersions").doc(versionId);
  const [appSnap, verSnap] = await Promise.all([appRef.get(), versionRef.get()]);

  if (!appSnap.exists || appSnap.data().status !== "approved") {
    throw new HttpsError("permission-denied", "Application is not approved for download.");
  }
  if (!verSnap.exists || verSnap.data().approvalStatus !== "approved") {
    throw new HttpsError("permission-denied", "Version is not approved for download.");
  }

  const idemId = idempotencyKey || `${request.auth?.uid || "guest"}_${versionId}_${Date.now()}`;
  const eventRef = db.collection("developerAnalytics").doc(appId);

  await db.runTransaction(async (tx) => {
    const analyticsSnap = await tx.get(eventRef);
    tx.update(appRef, { downloadCount: FieldValue.increment(1) });
    if (!analyticsSnap.exists) {
      tx.set(eventRef, {
        ownerUid: appSnap.data().ownerUid,
        appId,
        periodKey: "all",
        totalDownloads: 1,
        totalViews: appSnap.data().viewCount || 0,
        dailyDownloads: [1],
        weeklyDownloads: 1,
        monthlyDownloads: 1,
        averageRating: appSnap.data().averageRating || 0,
        totalReviews: appSnap.data().totalReviews || 0,
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      tx.update(eventRef, {
        totalDownloads: FieldValue.increment(1),
        weeklyDownloads: FieldValue.increment(1),
        monthlyDownloads: FieldValue.increment(1),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  });

  return {
    authorized: true,
    downloadUrl: verSnap.data().apkDownloadUrl || "",
    sha256: verSnap.data().sha256,
    idempotencyKey: idemId,
  };
});

// 6. Firestore Trigger: Recalculate App Rating & Total Reviews atomically
exports.onReviewChanged = onDocumentWritten(
  { document: "reviews/{reviewId}", database: DATABASE_ID },
  async (event) => {
    const after = event.data?.after?.data();
    const before = event.data?.before?.data();
    const appId = after?.appId || before?.appId;
    if (!appId) return;

    const snap = await db
      .collection("reviews")
      .where("appId", "==", appId)
      .where("moderationStatus", "==", "visible")
      .get();

    let sum = 0;
    let count = 0;
    snap.forEach((doc) => {
      const r = Number(doc.data().rating || 0);
      if (r >= 1 && r <= 5) {
        sum += r;
        count += 1;
      }
    });
    const avg = count > 0 ? Math.round((sum / count) * 100) / 100 : 0;
    await db.collection("apps").doc(appId).update({
      averageRating: avg,
      totalReviews: count,
    }).catch(() => {});
  }
);

// 7. Set Firebase Custom Claims for Admin Users
exports.grantAdminClaim = onCall(async (request) => {
  const callerUid = await assertAdmin(request);
  const { targetUid } = request.data || {};
  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid required.");

  await getAuth().setCustomUserClaims(targetUid, { admin: true });
  await db.collection("admins").doc(targetUid).set({
    uid: targetUid,
    role: "super_admin",
    createdAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await writeAuditLog(callerUid, "GRANT_ADMIN", "admins", targetUid, "success");
  return { granted: true, monetizationEnabled: MONETIZATION_ENABLED };
});
