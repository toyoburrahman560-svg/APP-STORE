import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-app.js";
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  addDoc,
  serverTimestamp,
  query,
  orderBy,
  limit,
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";
import {
  getStorage,
  ref,
  getDownloadURL,
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-storage.js";

// Provisioned Firebase Web Config — update apiKey/appId from Firebase Console -> Project Settings -> Web App
const firebaseConfig = {
  projectId: "gen-lang-client-0675216494",
  authDomain: "gen-lang-client-0675216494.firebaseapp.com",
  storageBucket: "gen-lang-client-0675216494.firebasestorage.app",
  apiKey: "REPLACE_WITH_WEB_API_KEY",
  appId: "REPLACE_WITH_WEB_APP_ID",
};

const DATABASE_ID = "ai-studio-android-appstore-d323b02f-df5e-4be6-bdfa-d2d2aab8b109";
const BOOTSTRAPPED_ADMIN_EMAIL = "monicaeg6wbestewart@gmail.com";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, DATABASE_ID);
const storage = getStorage(app);
const provider = new GoogleAuthProvider();

const $ = (id) => document.getElementById(id);

function showToast(msg) {
  const el = $("toast");
  el.textContent = msg;
  el.classList.remove("hidden");
  setTimeout(() => el.classList.add("hidden"), 3200);
}

async function verifyIsAdmin(user) {
  if (!user) return false;
  const token = await user.getIdTokenResult();
  if (token.claims?.admin === true) return true;
  if (user.email === BOOTSTRAPPED_ADMIN_EMAIL && user.emailVerified) return true;
  const adminSnap = await getDoc(doc(db, "admins", user.uid));
  return adminSnap.exists();
}

async function logAdminAction(action, targetType, targetId, result = "success", notes = "") {
  const uid = auth.currentUser?.uid || "unknown";
  await addDoc(collection(db, "adminLogs"), {
    adminUid: uid,
    action,
    targetType,
    targetId,
    result,
    notes,
    createdAt: serverTimestamp(),
  });
}

$("google-login-btn").addEventListener("click", async () => {
  $("login-error").classList.add("hidden");
  try {
    await signInWithPopup(auth, provider);
  } catch (err) {
    $("login-error").textContent = err.message;
    $("login-error").classList.remove("hidden");
  }
});

$("logout-btn").addEventListener("click", () => signOut(auth));

onAuthStateChanged(auth, async (user) => {
  if (!user) {
    $("login-screen").classList.remove("hidden");
    $("app-shell").classList.add("hidden");
    return;
  }
  const ok = await verifyIsAdmin(user);
  if (!ok) {
    await signOut(auth);
    $("login-error").textContent = "Unauthorized account. Admin custom claims or /admins/{uid} entry required.";
    $("login-error").classList.remove("hidden");
    return;
  }
  $("login-screen").classList.add("hidden");
  $("app-shell").classList.remove("hidden");
  $("admin-email-label").textContent = user.email;
  await loadAllAdminData();
});

document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const target = btn.dataset.section;
    document.querySelectorAll(".panel").forEach((p) => p.classList.add("hidden"));
    $(`sec-${target}`).classList.remove("hidden");
    $("section-title").textContent = btn.textContent;
  });
});

$("refresh-all-btn").addEventListener("click", loadAllAdminData);

let cachedUsers = [];
let cachedApps = [];
let cachedSubs = [];
let cachedVersions = [];
let activeReviewFilter = "all";
const resolvedApkLinks = new Map();

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function sanitizeHttpUrl(rawUrl) {
  const trimmed = String(rawUrl ?? "").trim();
  if (!trimmed) return "";
  try {
    const parsed = new URL(trimmed);
    return parsed.protocol === "https:" || parsed.protocol === "http:" ? parsed.toString() : "";
  } catch {
    return "";
  }
}

function formatBytes(bytes) {
  const num = Number(bytes || 0);
  if (!Number.isFinite(num) || num <= 0) return "";
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(0)} KB`;
  return `${(num / (1024 * 1024)).toFixed(1)} MB`;
}

function buildApkFileName(appData, version) {
  const base = (appData.packageName || appData.appName || appData.appId || "app")
    .replace(/[^a-zA-Z0-9._-]+/g, "_");
  const ver = (version?.versionName || appData.versionName || appData.versionCode || "1")
    .toString()
    .replace(/[^a-zA-Z0-9._-]+/g, "_");
  return `${base}-v${ver}.apk`;
}

function findVersionForApp(appData, sub, versions) {
  if (sub?.versionId) {
    const subMatch = versions.find((v) => v.versionId === sub.versionId || v.id === sub.versionId);
    if (subMatch) return subMatch;
  }
  const appVersions = versions.filter((v) => v.appId === appData.appId);
  if (!appVersions.length) return null;
  if (appData.status === "approved") {
    const exactApproved = appVersions.find(
      (v) => v.approvalStatus === "approved" && Number(v.versionCode) === Number(appData.versionCode)
    );
    if (exactApproved) return exactApproved;
    const anyApproved = appVersions
      .filter((v) => v.approvalStatus === "approved")
      .sort((a, b) => Number(b.versionCode || 0) - Number(a.versionCode || 0));
    if (anyApproved.length) return anyApproved[0];
  }
  return appVersions.sort((a, b) => Number(b.versionCode || 0) - Number(a.versionCode || 0))[0];
}

function getStorageCandidates(appData, version) {
  const candidates = [];
  const rawUrl = String(version?.apkDownloadUrl || "").trim();
  const storagePath = String(version?.apkStoragePath || "").trim();
  if (rawUrl.startsWith("gs://")) {
    candidates.push(rawUrl);
  }
  if (storagePath) {
    if (appData?.status === "approved") {
      const fileName = storagePath.split("/").pop() || buildApkFileName(appData, version);
      const versionId = version.versionId || version.id;
      if (appData.appId && versionId) {
        const publicPath = `public/apks/${appData.appId}/${versionId}/${fileName}`;
        if (publicPath !== storagePath) candidates.push(publicPath);
      }
    }
    candidates.push(storagePath);
  }
  return [...new Set(candidates)];
}

async function resolveApkUrlForVersion(appData, version, { persist = false, forceRefresh = false } = {}) {
  if (!version) return "";
  const key = version.versionId || version.id || appData.appId;
  const cached = resolvedApkLinks.get(key);
  if (!forceRefresh && cached?.status === "ready" && cached.url) {
    return cached.url;
  }

  const directUrl = sanitizeHttpUrl(version.apkDownloadUrl);
  if (directUrl && !forceRefresh) {
    resolvedApkLinks.set(key, { url: directUrl, status: "ready", error: "" });
    return directUrl;
  }

  const candidates = getStorageCandidates(appData, version);
  if (!candidates.length) {
    if (directUrl) {
      resolvedApkLinks.set(key, { url: directUrl, status: "ready", error: "" });
      return directUrl;
    }
    resolvedApkLinks.set(key, { url: "", status: "missing", error: "" });
    return "";
  }

  resolvedApkLinks.set(key, { url: directUrl || "", status: "resolving", error: "" });
  renderAppReviews(cachedApps, cachedSubs, cachedVersions);

  let lastError = "";
  for (const candidate of candidates) {
    try {
      const storageRef = ref(storage, candidate);
      const generatedUrl = sanitizeHttpUrl(await getDownloadURL(storageRef));
      if (generatedUrl) {
        resolvedApkLinks.set(key, { url: generatedUrl, status: "ready", error: "" });
        if (persist && version.id && generatedUrl.length <= 1000 && generatedUrl !== version.apkDownloadUrl) {
          version.apkDownloadUrl = generatedUrl;
          await updateDoc(doc(db, "appVersions", version.id), { apkDownloadUrl: generatedUrl }).catch(() => {});
        }
        renderAppReviews(cachedApps, cachedSubs, cachedVersions);
        return generatedUrl;
      }
    } catch (err) {
      lastError = err?.code || err?.message || "Failed to generate Firebase Storage URL";
    }
  }

  if (directUrl) {
    resolvedApkLinks.set(key, { url: directUrl, status: "ready", error: "" });
    renderAppReviews(cachedApps, cachedSubs, cachedVersions);
    return directUrl;
  }

  resolvedApkLinks.set(key, { url: "", status: "error", error: lastError });
  renderAppReviews(cachedApps, cachedSubs, cachedVersions);
  return "";
}

document.querySelectorAll("#review-status-filters .filter-pill").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll("#review-status-filters .filter-pill").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    activeReviewFilter = btn.dataset.filter || "all";
    renderAppReviews(cachedApps, cachedSubs, cachedVersions);
  });
});

async function loadAllAdminData() {
  const [usersSnap, devsSnap, appsSnap, subsSnap, versionsSnap, catsSnap, reviewsSnap, reportsSnap, logsSnap] = await Promise.all([
    getDocs(collection(db, "users")),
    getDocs(collection(db, "developerProfiles")),
    getDocs(collection(db, "apps")),
    getDocs(collection(db, "appSubmissions")),
    getDocs(collection(db, "appVersions")),
    getDocs(collection(db, "categories")),
    getDocs(collection(db, "reviews")),
    getDocs(collection(db, "reports")),
    getDocs(query(collection(db, "adminLogs"), orderBy("createdAt", "desc"), limit(50))),
  ]);

  cachedUsers = usersSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const devs = devsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  cachedApps = appsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  cachedSubs = subsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  cachedVersions = versionsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const cats = catsSnap.docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const reviews = reviewsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const reports = reportsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const logs = logsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));

  const totalDownloads = cachedApps.reduce((acc, a) => acc + (a.downloadCount || 0), 0);
  $("overview-kpis").innerHTML = [
    ["Total Users", cachedUsers.length],
    ["Developers", devs.length],
    ["Total Apps", cachedApps.length],
    ["Approved Apps", cachedApps.filter((a) => a.status === "approved").length],
    ["Pending Apps", cachedApps.filter((a) => a.status === "pending").length],
    ["Rejected Apps", cachedApps.filter((a) => a.status === "rejected").length],
    ["Total Downloads", totalDownloads],
    ["Open Reports", reports.filter((r) => r.status === "open").length],
  ].map(([k, v]) => `<div class="kpi-card"><div class="muted small">${k}</div><div class="val">${v}</div></div>`).join("");

  renderAppReviews(cachedApps, cachedSubs, cachedVersions);
  resolvePublishedAppLinks(cachedApps, cachedSubs, cachedVersions);
  renderUsers(cachedUsers);
  renderDevelopers(devs);
  renderCategories(cats);
  renderFeatured(cachedApps);
  renderReviewMod(reviews);
  renderReports(reports);
  renderAnalytics(cachedApps);
  renderAuditLogs(logs);
}

async function resolvePublishedAppLinks(apps, subs, versions) {
  const published = apps.filter((a) => a.status === "approved");
  await Promise.allSettled(
    published.map((appData) => {
      const sub = subs.find((s) => s.appId === appData.appId);
      const version = findVersionForApp(appData, sub, versions);
      if (!version) return Promise.resolve("");
      const directUrl = sanitizeHttpUrl(version.apkDownloadUrl);
      if (directUrl) {
        const key = version.versionId || version.id || appData.appId;
        resolvedApkLinks.set(key, { url: directUrl, status: "ready", error: "" });
        return Promise.resolve(directUrl);
      }
      return resolveApkUrlForVersion(appData, version, { persist: true });
    })
  );
  renderAppReviews(cachedApps, cachedSubs, cachedVersions);
}

function renderAppReviews(apps, subs, versions = []) {
  const filteredApps = apps.filter((appData) => {
    if (activeReviewFilter === "all") return true;
    if (activeReviewFilter === "approved") return appData.status === "approved";
    if (activeReviewFilter === "pending") {
      return ["pending", "under_review", "validating"].includes(appData.status);
    }
    if (activeReviewFilter === "rejected") {
      return ["rejected", "suspended"].includes(appData.status);
    }
    return true;
  });

  const publishedApps = apps.filter((a) => a.status === "approved");
  const readyLinkCount = publishedApps.filter((appData) => {
    const sub = subs.find((s) => s.appId === appData.appId);
    const version = findVersionForApp(appData, sub, versions);
    if (!version) return false;
    const key = version.versionId || version.id || appData.appId;
    return Boolean(resolvedApkLinks.get(key)?.url || sanitizeHttpUrl(version.apkDownloadUrl));
  }).length;
  const summaryEl = $("published-apk-summary");
  if (summaryEl) {
    summaryEl.textContent = `${publishedApps.length} published APK${publishedApps.length === 1 ? "" : "s"} • ${readyLinkCount} download link${readyLinkCount === 1 ? "" : "s"} ready`;
  }

  if (!filteredApps.length) {
    $("app-review-table").innerHTML = `<div style="padding:24px;text-align:center;" class="muted">No applications match this filter.</div>`;
    return;
  }

  const rows = filteredApps.map((appData) => {
    const sub = subs.find((s) => s.appId === appData.appId);
    const version = findVersionForApp(appData, sub, versions);
    const versionKey = version ? (version.versionId || version.id || appData.appId) : appData.appId;
    const linkState = resolvedApkLinks.get(versionKey);
    const directUrl = sanitizeHttpUrl(version?.apkDownloadUrl);
    const resolvedUrl = linkState?.url || directUrl;
    const apkFileName = buildApkFileName(appData, version);
    const sizeText = formatBytes(version?.apkSize || appData.apkSizeBytes);
    const shaShort = version?.sha256 ? `SHA-256: ${String(version.sha256).slice(0, 12)}...` : "";
    const metaParts = [sizeText, shaShort].filter(Boolean).join(" • ");
    const safeStatus = escapeHtml(appData.status || "pending");
    const pillClass = ["approved", "pending", "rejected", "suspended"].includes(appData.status)
      ? `pill pill-${appData.status}`
      : "pill";

    let downloadCellHtml = `<span class="muted small">Available after publish</span>`;
    if (appData.status === "approved") {
      if (resolvedUrl) {
        downloadCellHtml = `
          <div class="apk-link-box">
            <input class="apk-url-input" type="text" readonly value="${escapeHtml(resolvedUrl)}" onclick="this.select()" aria-label="Published APK Download URL" />
            <div class="apk-link-actions">
              <button type="button" class="btn-outline btn-sm" data-url="${escapeHtml(resolvedUrl)}" onclick="window.copyApkUrl(this)">Copy URL</button>
              <a class="btn-download btn-sm" href="${escapeHtml(resolvedUrl)}" download="${escapeHtml(apkFileName)}" target="_blank" rel="noopener noreferrer">Download APK</a>
              ${(version?.apkStoragePath || String(version?.apkDownloadUrl || "").startsWith("gs://")) ? `<button type="button" class="btn-outline btn-sm" data-appid="${escapeHtml(appData.appId)}" onclick="window.generateApkLink(this)">Refresh</button>` : ""}
            </div>
            ${metaParts ? `<div class="muted small">${escapeHtml(metaParts)}</div>` : ""}
          </div>
        `;
      } else if (linkState?.status === "resolving") {
        downloadCellHtml = `<span class="muted small">Generating download link...</span>`;
      } else if (version && (version.apkStoragePath || version.apkDownloadUrl)) {
        const hint = linkState?.error ? `Retry (${escapeHtml(linkState.error)})` : "Generate Link";
        downloadCellHtml = `
          <div class="apk-link-box">
            <div class="apk-link-actions">
              <button type="button" class="btn-primary btn-sm" data-appid="${escapeHtml(appData.appId)}" onclick="window.generateApkLink(this)">${hint}</button>
            </div>
            ${metaParts ? `<div class="muted small">${escapeHtml(metaParts)}</div>` : ""}
          </div>
        `;
      } else {
        downloadCellHtml = `<span class="muted small">No APK file or URL attached</span>`;
      }
    } else if (version && (directUrl || version.apkStoragePath)) {
      downloadCellHtml = `<span class="muted small">Staged • link unlocks on publish</span>`;
    }

    return `<tr>
      <td><strong>${escapeHtml(appData.appName)}</strong><br><span class="muted small">${escapeHtml(appData.packageName)}</span></td>
      <td>${escapeHtml(appData.developerName || appData.ownerUid)}</td>
      <td>v${escapeHtml(appData.versionName)} (${escapeHtml(appData.versionCode)})</td>
      <td><span class="${pillClass}">${safeStatus}</span></td>
      <td>${downloadCellHtml}</td>
      <td>
        <div class="action-stack">
          <button class="btn-success btn-sm" onclick="window.adminActApp('${escapeHtml(appData.appId)}', '${escapeHtml(sub?.versionId || version?.id || "")}', '${escapeHtml(sub?.id || "")}', 'approved')">Approve</button>
          <button class="btn-danger btn-sm" onclick="window.adminRejectApp('${escapeHtml(appData.appId)}', '${escapeHtml(sub?.versionId || version?.id || "")}', '${escapeHtml(sub?.id || "")}')">Reject</button>
          <button class="btn-outline btn-sm" onclick="window.adminSuspendApp('${escapeHtml(appData.appId)}')">Suspend</button>
        </div>
      </td>
    </tr>`;
  }).join("");

  $("app-review-table").innerHTML = `<table><thead><tr><th>App</th><th>Developer</th><th>Version</th><th>Status</th><th>Published APK Link</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
}

window.copyApkUrl = async (btnEl) => {
  const url = btnEl?.dataset?.url || "";
  if (!url) return;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url);
    } else {
      const ta = document.createElement("textarea");
      ta.value = url;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      document.body.removeChild(ta);
    }
    const prevText = btnEl.textContent;
    btnEl.textContent = "Copied!";
    setTimeout(() => { btnEl.textContent = prevText; }, 1800);
    showToast("APK download URL copied to clipboard!");
  } catch {
    showToast("Could not copy automatically — select the URL field to copy.");
  }
};

window.generateApkLink = async (btnEl) => {
  const appId = btnEl?.dataset?.appid || "";
  const appData = cachedApps.find((a) => a.appId === appId);
  if (!appData) return;
  const sub = cachedSubs.find((s) => s.appId === appId);
  const version = findVersionForApp(appData, sub, cachedVersions);
  if (!version) {
    showToast("No app version metadata found.");
    return;
  }
  const url = await resolveApkUrlForVersion(appData, version, { persist: true, forceRefresh: true });
  if (url) {
    showToast("Download link generated!");
  } else {
    showToast("Could not resolve APK download URL from Storage.");
  }
};

window.adminActApp = async (appId, versionId, subId, status) => {
  const appRef = doc(db, "apps", appId);
  const appSnap = await getDoc(appRef);
  await updateDoc(appRef, { status, updatedAt: serverTimestamp() });
  if (versionId) {
    const versionUpdate = { approvalStatus: status, validationStatus: "passed" };
    const matchedVersion = cachedVersions.find((v) => v.id === versionId || v.versionId === versionId);
    const matchedApp = cachedApps.find((a) => a.appId === appId) || (appSnap.exists() ? appSnap.data() : null);
    if (status === "approved" && matchedVersion && matchedApp && !sanitizeHttpUrl(matchedVersion.apkDownloadUrl)) {
      const resolvedUrl = await resolveApkUrlForVersion({ ...matchedApp, status: "approved" }, matchedVersion, { persist: false });
      if (resolvedUrl && resolvedUrl.length <= 1000) {
        versionUpdate.apkDownloadUrl = resolvedUrl;
      }
    }
    await updateDoc(doc(db, "appVersions", versionId), versionUpdate);
  }
  if (subId) await updateDoc(doc(db, "appSubmissions", subId), { status, updatedAt: serverTimestamp() });
  if (appSnap.exists()) {
    await addDoc(collection(db, "notifications"), {
      userId: appSnap.data().ownerUid,
      type: "app_approved",
      title: `${appSnap.data().appName} Approved`,
      body: "Your application is now live on APP STORE.",
      targetAppId: appId,
      read: false,
      createdAt: serverTimestamp(),
    });
  }
  await logAdminAction("APPROVE_APP", "apps", appId);
  showToast("Application approved!");
  await loadAllAdminData();
};

window.adminRejectApp = async (appId, versionId, subId) => {
  const reason = prompt("Enter rejection reason for the developer:");
  if (!reason) return;
  await updateDoc(doc(db, "apps", appId), { status: "rejected", updatedAt: serverTimestamp() });
  if (versionId) await updateDoc(doc(db, "appVersions", versionId), { approvalStatus: "rejected" });
  if (subId) await updateDoc(doc(db, "appSubmissions", subId), { status: "rejected", rejectionReason: reason, updatedAt: serverTimestamp() });
  await logAdminAction("REJECT_APP", "apps", appId, "success", reason);
  showToast("Application rejected.");
  await loadAllAdminData();
};

window.adminSuspendApp = async (appId) => {
  const reason = prompt("Suspension reason:");
  if (!reason) return;
  await updateDoc(doc(db, "apps", appId), { status: "suspended", updatedAt: serverTimestamp() });
  await logAdminAction("SUSPEND_APP", "apps", appId, "success", reason);
  showToast("Application suspended.");
  await loadAllAdminData();
};

function renderUsers(users) {
  const rows = users.map((u) => `<tr>
    <td>${u.displayName}</td><td>${u.email}</td><td>${u.developerStatus}</td><td><span class="pill">${u.accountStatus}</span></td>
    <td>
      <button class="btn-outline" onclick="window.setUserStatus('${u.id}', 'active')">Restore</button>
      <button class="btn-danger" onclick="window.setUserStatus('${u.id}', 'suspended')">Suspend</button>
      <button class="btn-danger" onclick="window.setUserStatus('${u.id}', 'banned')">Ban</button>
    </td>
  </tr>`).join("");
  $("users-table").innerHTML = `<table><thead><tr><th>Name</th><th>Email</th><th>Dev Status</th><th>Account</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
}

$("user-search-input").addEventListener("input", (e) => {
  const q = e.target.value.toLowerCase();
  renderUsers(cachedUsers.filter((u) => (u.displayName || "").toLowerCase().includes(q) || (u.email || "").toLowerCase().includes(q)));
});

window.setUserStatus = async (uid, accountStatus) => {
  await updateDoc(doc(db, "users", uid), { accountStatus, updatedAt: serverTimestamp() });
  await logAdminAction(`USER_${accountStatus.toUpperCase()}`, "users", uid);
  showToast(`User marked ${accountStatus}`);
  await loadAllAdminData();
};

function renderDevelopers(devs) {
  const rows = devs.map((d) => `<tr>
    <td>${d.displayName}</td><td>${d.contactEmail}</td><td><span class="pill">${d.status}</span></td>
    <td>
      <button class="btn-success" onclick="window.setDevStatus('${d.id}', 'verified')">Verify</button>
      <button class="btn-danger" onclick="window.setDevStatus('${d.id}', 'suspended')">Suspend</button>
    </td>
  </tr>`).join("");
  $("developers-table").innerHTML = `<table><thead><tr><th>Studio</th><th>Email</th><th>Status</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
}

window.setDevStatus = async (uid, status) => {
  await updateDoc(doc(db, "developerProfiles", uid), { status, updatedAt: serverTimestamp() });
  await updateDoc(doc(db, "users", uid), { developerStatus: status, updatedAt: serverTimestamp() });
  await logAdminAction(`DEV_${status.toUpperCase()}`, "developerProfiles", uid);
  showToast(`Developer ${status}`);
  await loadAllAdminData();
};

function renderCategories(cats) {
  const rows = cats.map((c) => `<tr>
    <td>${c.name} (${c.nameBn || ""})</td><td>${c.group}</td><td>${c.sortOrder}</td><td><span class="pill">${c.enabled}</span></td>
    <td>
      <button class="btn-outline" onclick="window.toggleCat('${c.id}', ${!c.enabled})">${c.enabled ? "Disable" : "Enable"}</button>
      <button class="btn-danger" onclick="window.deleteCat('${c.id}')">Delete</button>
    </td>
  </tr>`).join("");
  $("categories-table").innerHTML = `<table><thead><tr><th>Name</th><th>Group</th><th>Order</th><th>Enabled</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
}

$("add-cat-btn").addEventListener("click", async () => {
  const name = $("cat-name").value.trim();
  if (!name) return;
  const id = name.toLowerCase().replace(/[^a-z0-9]+/g, "_");
  await setDoc(doc(db, "categories", id), {
    name,
    nameBn: $("cat-name-bn").value.trim(),
    group: $("cat-group").value,
    iconName: "apps",
    sortOrder: parseInt($("cat-order").value || "1", 10),
    enabled: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  await logAdminAction("CREATE_CATEGORY", "categories", id);
  showToast("Category added");
  await loadAllAdminData();
});

$("seed-cats-btn").addEventListener("click", async () => {
  const defaults = [
    ["action", "Action", "অ্যাকশন", "games", "sports_esports", 1],
    ["adventure", "Adventure", "অ্যাডভেঞ্চার", "games", "explore", 2],
    ["racing", "Racing", "রেসিং", "games", "directions_car", 3],
    ["puzzle", "Puzzle", "পাজল", "games", "extension", 4],
    ["strategy", "Strategy", "স্ট্র্যাটেজি", "games", "psychology", 5],
    ["arcade", "Arcade", "আর্কেড", "games", "videogame_asset", 6],
    ["sports", "Sports", "স্পোর্টস", "games", "sports_soccer", 7],
    ["education", "Education", "শিক্ষা", "apps", "school", 8],
    ["entertainment", "Entertainment", "বিনোদন", "apps", "theaters", 9],
    ["photography", "Photography", "ফটোগ্রাফি", "apps", "photo_camera", 10],
    ["video_players", "Video Players", "ভিডিও প্লেয়ার", "apps", "smart_display", 11],
    ["music", "Music", "সংগীত", "apps", "music_note", 12],
    ["business", "Business", "ব্যবসা", "apps", "business_center", 13],
    ["productivity", "Productivity", "উৎপাদনশীলতা", "apps", "task_alt", 14],
    ["tools", "Tools", "টুলস", "apps", "build", 15],
    ["lifestyle", "Lifestyle", "লাইফস্টাইল", "apps", "spa", 16],
    ["finance", "Finance", "অর্থনীতি", "apps", "account_balance", 17],
    ["health_fitness", "Health & Fitness", "স্বাস্থ্য ও ফিটনেস", "apps", "fitness_center", 18],
    ["communication", "Communication", "যোগাযোগ", "apps", "forum", 19],
    ["shopping", "Shopping", "শপিং", "apps", "shopping_bag", 20],
    ["news", "News", "সংবাদ", "apps", "newspaper", 21],
    ["travel", "Travel", "ভ্রমণ", "apps", "flight", 22],
    ["personalization", "Personalization", "পার্সোনালাইজেশন", "apps", "palette", 23],
    ["other", "Other", "অন্যান্য", "apps", "apps", 24],
  ];
  await Promise.all(defaults.map(([id, name, nameBn, group, iconName, sortOrder]) =>
    setDoc(doc(db, "categories", id), { name, nameBn, group, iconName, sortOrder, enabled: true, createdAt: serverTimestamp(), updatedAt: serverTimestamp() })
  ));
  await logAdminAction("SEED_CATEGORIES", "categories", "all");
  showToast("Seeded 24 categories!");
  await loadAllAdminData();
});

window.toggleCat = async (id, enabled) => {
  await updateDoc(doc(db, "categories", id), { enabled, updatedAt: serverTimestamp() });
  await logAdminAction("TOGGLE_CATEGORY", "categories", id);
  await loadAllAdminData();
};

window.deleteCat = async (id) => {
  await deleteDoc(doc(db, "categories", id));
  await logAdminAction("DELETE_CATEGORY", "categories", id);
  await loadAllAdminData();
};

function renderFeatured(apps) {
  const rows = apps.filter((a) => a.status === "approved").map((a) => `<tr>
    <td>${a.appName}</td><td>${a.featured ? "Yes" : "No"}</td><td>${a.editorsChoice ? "Yes" : "No"}</td>
    <td>
      <button class="btn-outline" onclick="window.toggleFeat('${a.appId}', 'featured', ${!a.featured})">Toggle Featured</button>
      <button class="btn-outline" onclick="window.toggleFeat('${a.appId}', 'editorsChoice', ${!a.editorsChoice})">Toggle Editor's</button>
    </td>
  </tr>`).join("");
  $("featured-table").innerHTML = `<table><thead><tr><th>App</th><th>Featured</th><th>Editor's Choice</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
}

window.toggleFeat = async (appId, field, value) => {
  await updateDoc(doc(db, "apps", appId), { [field]: value, updatedAt: serverTimestamp() });
  await logAdminAction(`TOGGLE_${field.toUpperCase()}`, "apps", appId);
  await loadAllAdminData();
};

function renderReviewMod(reviews) {
  const rows = reviews.map((r) => `<tr>
    <td>${r.appId}</td><td>${r.userName}</td><td>${r.rating}★</td><td>${r.comment}</td><td><span class="pill">${r.moderationStatus}</span></td>
    <td><button class="btn-danger" onclick="window.removeReview('${r.id}')">Remove</button></td>
  </tr>`).join("");
  $("review-mod-table").innerHTML = `<table><thead><tr><th>App</th><th>User</th><th>Rating</th><th>Comment</th><th>Status</th><th>Action</th></tr></thead><tbody>${rows}</tbody></table>`;
}

window.removeReview = async (id) => {
  await updateDoc(doc(db, "reviews", id), { moderationStatus: "removed", updatedAt: serverTimestamp() });
  await logAdminAction("REMOVE_REVIEW", "reviews", id);
  await loadAllAdminData();
};

function renderReports(reports) {
  const rows = reports.map((r) => `<tr>
    <td>${r.targetType}:${r.targetId}</td><td>${r.reason}</td><td>${r.details}</td><td><span class="pill">${r.status}</span></td>
    <td>
      <button class="btn-success" onclick="window.resolveReport('${r.id}', 'resolved')">Resolve</button>
      <button class="btn-outline" onclick="window.resolveReport('${r.id}', 'dismissed')">Dismiss</button>
    </td>
  </tr>`).join("");
  $("reports-table").innerHTML = `<table><thead><tr><th>Target</th><th>Reason</th><th>Details</th><th>Status</th><th>Actions</th></tr></thead><tbody>${rows}</tbody></table>`;
}

window.resolveReport = async (id, status) => {
  await updateDoc(doc(db, "reports", id), { status, updatedAt: serverTimestamp() });
  await logAdminAction(`REPORT_${status.toUpperCase()}`, "reports", id);
  await loadAllAdminData();
};

$("send-notif-btn").addEventListener("click", async () => {
  const uid = $("notif-target-uid").value.trim();
  const title = $("notif-title").value.trim();
  const body = $("notif-body").value.trim();
  if (!title || !body) return;
  if (uid) {
    await addDoc(collection(db, "notifications"), { userId: uid, type: "announcement", title, body, read: false, createdAt: serverTimestamp() });
    await logAdminAction("SEND_NOTIFICATION", "notifications", uid);
  } else {
    await addDoc(collection(db, "announcements"), { title, body, active: true, createdAt: serverTimestamp() });
    await logAdminAction("SEND_ANNOUNCEMENT", "announcements", "global");
  }
  showToast("Notification published!");
});

function renderAnalytics(apps) {
  const rows = [...apps].sort((a, b) => (b.downloadCount || 0) - (a.downloadCount || 0)).map((a) => `<tr>
    <td>${a.appName}</td><td>${a.downloadCount || 0}</td><td>${a.viewCount || 0}</td><td>${a.averageRating || 0}★ (${a.totalReviews || 0})</td>
  </tr>`).join("");
  $("analytics-table").innerHTML = `<table><thead><tr><th>App</th><th>Downloads</th><th>Views</th><th>Rating</th></tr></thead><tbody>${rows}</tbody></table>`;
}

$("save-settings-btn").addEventListener("click", async () => {
  await setDoc(doc(db, "platformSettings", "global"), {
    platformName: $("set-platform-name").value.trim(),
    supportEmail: $("set-support-email").value.trim(),
    maxUploadSizeMb: parseInt($("set-max-mb").value || "250", 10),
    allowedExtensions: ["apk"],
    maintenanceMode: $("set-maintenance").checked,
    monetizationEnabled: $("set-monetization").checked,
    updatedAt: serverTimestamp(),
  }, { merge: true });
  await logAdminAction("UPDATE_SETTINGS", "platformSettings", "global");
  showToast("Settings saved!");
});

function renderAuditLogs(logs) {
  const rows = logs.map((l) => `<tr>
    <td>${l.adminUid}</td><td><span class="pill">${l.action}</span></td><td>${l.targetType}/${l.targetId}</td><td>${l.notes || ""}</td>
  </tr>`).join("");
  $("audit-logs-table").innerHTML = `<table><thead><tr><th>Admin</th><th>Action</th><th>Target</th><th>Notes</th></tr></thead><tbody>${rows}</tbody></table>`;
}
