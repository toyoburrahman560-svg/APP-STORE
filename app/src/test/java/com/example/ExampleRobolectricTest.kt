package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.ActiveDownloadState
import com.example.data.download.ApkDownloadService
import com.example.data.model.AppListing
import com.example.data.model.AppStatus
import com.example.data.model.DownloadPhase
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.formatBytes
import com.example.ui.components.formatEta
import com.example.ui.components.formatSpeed
import com.example.ui.viewmodel.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches APP STORE branding`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("APP STORE", appName)
  }

  @Test
  fun `default categories cover both games and apps`() {
    val categories = MarketplaceRepository.DefaultCategories
    assertTrue(categories.any { it.group == "games" })
    assertTrue(categories.any { it.group == "apps" })
    assertEquals(24, categories.size)
  }

  @Test
  fun `app status and sort order map cleanly`() {
    assertEquals(AppStatus.APPROVED, AppStatus.fromRaw("approved"))
    assertEquals(AppStatus.UNDER_REVIEW, AppStatus.fromRaw("UNDER_REVIEW"))
    assertEquals(AppStatus.PENDING, AppStatus.fromRaw("unknown_status"))
    assertEquals(4, SortOrder.entries.size)
  }

  @Test
  fun `download state telemetry and formatters work accurately`() {
    val state = ActiveDownloadState(
      appId = "app_demo",
      versionId = "app_demo_v1",
      appName = "Demo App",
      packageName = "com.demo.app",
      versionName = "1.0.0",
      versionCode = 1L,
      phase = DownloadPhase.PAUSED,
      bytesDownloaded = 2L * 1024L * 1024L,
      totalBytes = 4L * 1024L * 1024L,
      progressPercent = 50,
      speedBytesPerSec = 512L * 1024L,
      etaSeconds = 65L
    )
    assertEquals(512L, state.speedKbps)
    assertEquals("2.0 MB", formatBytes(state.bytesDownloaded))
    assertEquals("512 KB/s", formatSpeed(state.speedBytesPerSec))
    assertEquals("1m 5s left", formatEta(state.etaSeconds))
  }

  @Test
  fun `download service rejects unapproved apps strictly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val service = ApkDownloadService(context)
    val pendingApp = AppListing(
      appId = "unapproved_1",
      appName = "Unapproved App",
      packageName = "com.example.unapproved",
      status = "pending"
    )
    var captured: ActiveDownloadState? = null
    service.startDownload(pendingApp, null) { captured = it }
    assertEquals(DownloadPhase.FAILED, captured?.phase)
    assertFalse(service.isDownloadActiveFor("unapproved_1", 1L))
  }
}
