package com.woocommerce.android.ui.prefs.cardreader.detail

import androidx.lifecycle.SavedStateHandle
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.R
import com.woocommerce.android.cardreader.CardReader
import com.woocommerce.android.cardreader.CardReaderManager
import com.woocommerce.android.cardreader.CardReaderStatus
import com.woocommerce.android.cardreader.SoftwareUpdateAvailability
import com.woocommerce.android.model.UiString
import com.woocommerce.android.model.UiString.UiStringRes
import com.woocommerce.android.model.UiString.UiStringText
import com.woocommerce.android.ui.prefs.cardreader.detail.CardReaderDetailViewModel.ViewState.ConnectedState
import com.woocommerce.android.ui.prefs.cardreader.detail.CardReaderDetailViewModel.ViewState.Loading
import com.woocommerce.android.ui.prefs.cardreader.detail.CardReaderDetailViewModel.ViewState.NotConnectedState
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@ExperimentalCoroutinesApi
class CardReaderDetailViewModelTest : BaseUnitTest() {
    private val cardReaderManager: CardReaderManager = mock {
        onBlocking { softwareUpdateAvailability() }
            .thenReturn(MutableStateFlow(SoftwareUpdateAvailability.Initializing))
    }

    @Test
    fun `when view model init with connected state should emit loading view state`() {
        // GIVEN
        val status = MutableStateFlow(CardReaderStatus.Connected(mock()))
        whenever(cardReaderManager.readerStatus).thenReturn(status)

        // WHEN
        val viewModel = createViewModel()

        // THEN
        assertThat(viewModel.viewStateData.value).isInstanceOf(Loading::class.java)
    }

    @Test
    fun `when view model init with connected state and update up to date should emit connected view state`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // GIVEN
            initConnectedState()

            // WHEN
            val viewModel = createViewModel()

            // THEN
            assertThat(viewModel.viewStateData.value).isInstanceOf(ConnectedState::class.java)
        }

    @Test
    fun `when view model init with connected state should emit correct values of connected state`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // GIVEN
            initConnectedState()

            // WHEN
            val viewModel = createViewModel()

            // THEN
            verifyConnectedState(
                viewModel,
                UiStringText(READER_NAME),
                UiStringRes(R.string.card_reader_detail_connected_battery_percentage, listOf(UiStringText("2"))),
                updateAvailable = false
            )
        }

    @Test
    fun `when view model init with connected state and empty name should emit connected view state with fallbacks`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // GIVEN
            initConnectedState(readersName = null, batteryLevel = null)

            // WHEN
            val viewModel = createViewModel()
            viewModel.viewStateData.observeForever {}

            // THEN
            verifyConnectedState(
                viewModel,
                UiStringRes(R.string.card_reader_detail_connected_reader_unknown),
                null,
                updateAvailable = false
            )
        }

    @Test
    fun `when view model init with not connected state should emit not connected view state`() {
        // GIVEN
        val status = MutableStateFlow(CardReaderStatus.NotConnected)
        whenever(cardReaderManager.readerStatus).thenReturn(status)

        // WHEN
        val viewModel = createViewModel()

        // THEN
        assertThat(viewModel.viewStateData.value).isInstanceOf(NotConnectedState::class.java)
    }

    @Test
    fun `when view model init with not connected state should emit correct values not connected state`() {
        // GIVEN
        val status = MutableStateFlow(CardReaderStatus.NotConnected)
        whenever(cardReaderManager.readerStatus).thenReturn(status)

        // WHEN
        val viewModel = createViewModel()

        // THEN
        verifyNotConnectedState(viewModel)
    }

    @Test
    fun `when view model init with connecting state should emit not connected view state`() {
        // GIVEN
        val status = MutableStateFlow(CardReaderStatus.Connecting)
        whenever(cardReaderManager.readerStatus).thenReturn(status)

        // WHEN
        val viewModel = createViewModel()

        // THEN
        assertThat(viewModel.viewStateData.value).isInstanceOf(NotConnectedState::class.java)
    }

    @Test
    fun `when view model init with connected state should invoke software update availability`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // GIVEN
            val status = MutableStateFlow(CardReaderStatus.Connected(mock()))
            whenever(cardReaderManager.readerStatus).thenReturn(status)

            // WHEN
            createViewModel()

            // THEN
            verify(cardReaderManager).softwareUpdateAvailability()
        }

    @Test
    fun `when view model init with connected state and update available should emit connected state with update`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // GIVEN
            initConnectedState(updateAvailable = SoftwareUpdateAvailability.UpdateAvailable)

            // WHEN
            val viewModel = createViewModel()

            // THEN
            verifyConnectedState(
                viewModel,
                UiStringText(READER_NAME),
                UiStringRes(R.string.card_reader_detail_connected_battery_percentage, listOf(UiStringText("2"))),
                updateAvailable = true
            )
        }

    @Test
    fun `when view model init with connected state and check failed should emit connected state without update`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
            // GIVEN
            initConnectedState(updateAvailable = SoftwareUpdateAvailability.CheckForUpdatesFailed)

            // WHEN
            val viewModel = createViewModel()

            // THEN
            verifyConnectedState(
                viewModel,
                UiStringText(READER_NAME),
                UiStringRes(R.string.card_reader_detail_connected_battery_percentage, listOf(UiStringText("2"))),
                updateAvailable = false
            )
            assertThat(viewModel.event.value)
                .isEqualTo(Event.ShowSnackbar(R.string.card_reader_detail_connected_update_check_failed))
        }

    private fun verifyNotConnectedState(viewModel: CardReaderDetailViewModel) {
        val state = viewModel.viewStateData.value as NotConnectedState
        assertThat(state.headerLabel)
            .isEqualTo(UiStringRes(R.string.card_reader_detail_not_connected_header))
        assertThat(state.illustration)
            .isEqualTo(R.drawable.img_card_reader_not_connected)
        assertThat(state.firstHintLabel)
            .isEqualTo(UiStringRes(R.string.card_reader_detail_not_connected_first_hint_label))
        assertThat(state.secondHintLabel)
            .isEqualTo(UiStringRes(R.string.card_reader_detail_not_connected_second_hint_label))
        assertThat(state.connectBtnLabel)
            .isEqualTo(UiStringRes(R.string.card_reader_details_not_connected_connect_button_label))
    }

    private fun verifyConnectedState(
        viewModel: CardReaderDetailViewModel,
        readerName: UiString,
        batteryLevel: UiString?,
        updateAvailable: Boolean
    ) {
        val state = viewModel.viewStateData.value as ConnectedState
        assertThat(state.readerName).isEqualTo(readerName)
        assertThat(state.readerBattery).isEqualTo(batteryLevel)
        if (updateAvailable) {
            assertThat(state.enforceReaderUpdate)
                .isEqualTo(UiStringRes(R.string.card_reader_detail_connected_enforced_update_software))
            assertThat(state.primaryButtonState?.text)
                .isEqualTo(UiStringRes(R.string.card_reader_detail_connected_update_software))
            assertThat(state.secondaryButtonState?.text)
                .isEqualTo(UiStringRes(R.string.card_reader_detail_connected_disconnect_reader))
        } else {
            assertThat(state.enforceReaderUpdate).isNull()
            assertThat(state.primaryButtonState?.text)
                .isEqualTo(UiStringRes(R.string.card_reader_detail_connected_disconnect_reader))
            assertThat(state.secondaryButtonState?.text).isNull()
        }
    }

    private fun initConnectedState(
        readersName: String? = READER_NAME,
        batteryLevel: Float? = 1.6F,
        updateAvailable: SoftwareUpdateAvailability = SoftwareUpdateAvailability.UpToDate
    ) = coroutinesTestRule.testDispatcher.runBlockingTest {
        val reader: CardReader = mock {
            on { id }.thenReturn(readersName)
            on { currentBatteryLevel }.thenReturn(batteryLevel)
        }
        val status = MutableStateFlow(CardReaderStatus.Connected(reader))
        whenever(cardReaderManager.readerStatus).thenReturn(status)
        whenever(cardReaderManager.softwareUpdateAvailability()).thenReturn(MutableStateFlow(updateAvailable))
    }

    private fun createViewModel() = CardReaderDetailViewModel(
        cardReaderManager,
        SavedStateHandle()
    )

    private companion object {
        private const val READER_NAME = "CH3231H"
    }
}