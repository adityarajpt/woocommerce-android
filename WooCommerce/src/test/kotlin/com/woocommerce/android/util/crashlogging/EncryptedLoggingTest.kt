package com.woocommerce.android.util.crashlogging

import com.nhaarman.mockitokotlin2.argForWhich
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.ENCRYPTED_LOGGING_UPLOAD_FAILED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.ENCRYPTED_LOGGING_UPLOAD_SUCCESSFUL
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.util.CoroutineTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.EncryptedLogAction.RESET_UPLOAD_STATES
import org.wordpress.android.fluxc.action.EncryptedLogAction.UPLOAD_LOG
import org.wordpress.android.fluxc.store.EncryptedLogStore
import org.wordpress.android.fluxc.store.EncryptedLogStore.OnEncryptedLogUploaded
import org.wordpress.android.fluxc.store.EncryptedLogStore.UploadEncryptedLogError
import org.wordpress.android.fluxc.store.EncryptedLogStore.UploadEncryptedLogPayload
import org.wordpress.android.fluxc.utils.AppLogWrapper
import org.wordpress.android.util.helpers.logfile.LogFileProviderInterface
import java.io.File

class EncryptedLoggingTest {
    private lateinit var sut: EncryptedLogging

    private val eventBusDispatcher: Dispatcher = mock()
    private val encryptedLogStore: EncryptedLogStore = mock()
    private val logFileProvider: LogFileProviderInterface = mock()
    private val networkStatus: NetworkStatus = mock()
    private val analyticsTracker: AnalyticsTrackerWrapper = mock()
    private val logger: AppLogWrapper = mock()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Before
    fun setUp() {
        sut = EncryptedLogging(
            eventBusDispatcher = eventBusDispatcher,
            dispatchers = coroutinesTestRule.testDispatchers,
            encryptedLogStore = encryptedLogStore,
            logFileProvider = logFileProvider,
            networkStatus = networkStatus,
            analyticsTracker = analyticsTracker,
            logger = logger
        )
    }

    @Test
    fun `should reset upload states on initialization`() {
        verify(eventBusDispatcher, times(1)).dispatch(argForWhich {
            payload == null && type == RESET_UPLOAD_STATES
        })
    }

    @Test
    fun `should not enqueue upload when log file does not exist`() {
        whenever(logFileProvider.getLogFiles()).thenReturn(emptyList())

        sut.enqueue("uuid", false)

        verify(eventBusDispatcher, never()).dispatch(argForWhich {
            type == UPLOAD_LOG
        })
    }

    @Test
    fun `should enqueue logs upload when log file is available and there's network connection`() {
        val uuid = "uuid"
        val tempFile = File("temp")
        val startImmediately = false
        whenever(logFileProvider.getLogFiles()).thenReturn(listOf(tempFile))
        whenever(networkStatus.isConnected()).thenReturn(true)

        sut.enqueue("uuid", startImmediately)

        verify(eventBusDispatcher, times(1)).dispatch(argForWhich {
            (payload as? UploadEncryptedLogPayload).let {
                it?.shouldStartUploadImmediately == startImmediately &&
                    it.uuid == uuid &&
                    it.file == tempFile
            } && type == UPLOAD_LOG
        })
    }

    // If the connection is not available, we shouldn't try to upload immediately
    @Test
    fun `should not start upload immediately when requested but there's no network connection`() {
        whenever(logFileProvider.getLogFiles()).thenReturn(listOf(File("temp")))
        whenever(networkStatus.isConnected()).thenReturn(false)

        sut.enqueue("uuid", true)

        verify(eventBusDispatcher, times(1)).dispatch(argForWhich {
            (payload as? UploadEncryptedLogPayload).let {
                it?.shouldStartUploadImmediately == false
            }
        })
    }

    @Test
    fun `should start upload immediately when requested and there's network connection`() {
        whenever(logFileProvider.getLogFiles()).thenReturn(listOf(File("temp")))
        whenever(networkStatus.isConnected()).thenReturn(true)

        sut.enqueue("uuid", true)

        verify(eventBusDispatcher, times(1)).dispatch(argForWhich {
            (payload as? UploadEncryptedLogPayload).let {
                it?.shouldStartUploadImmediately == true
            }
        })
    }

    @Test
    fun `should track successful upload`() {
        sut.onEncryptedLogUploaded(event = OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully("", File("temp")))

        verify(analyticsTracker, times(1)).track(ENCRYPTED_LOGGING_UPLOAD_SUCCESSFUL)
    }

    @Test
    fun `should track failed upload when sending won't be retried`() {
        val event = OnEncryptedLogUploaded.EncryptedLogFailedToUpload(
            "",
            File("temp"),
            UploadEncryptedLogError.InvalidRequest,
            willRetry = false
        )

        sut.onEncryptedLogUploaded(event = event)

        verify(analyticsTracker, times(1)).track(
            ENCRYPTED_LOGGING_UPLOAD_FAILED,
            mapOf("error_type" to event.error.javaClass.simpleName)
        )
    }

    @Test
    fun `should not track failed upload when sending will be retried`() {
        val event = OnEncryptedLogUploaded.EncryptedLogFailedToUpload(
            "",
            File("temp"),
            UploadEncryptedLogError.InvalidRequest,
            willRetry = true
        )

        sut.onEncryptedLogUploaded(event = event)

        verifyZeroInteractions(analyticsTracker)
    }
}
