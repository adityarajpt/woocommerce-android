package com.woocommerce.android.ui.inbox

import androidx.annotation.ColorRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.R
import com.woocommerce.android.ui.inbox.domain.InboxNote
import com.woocommerce.android.ui.inbox.domain.InboxNote.NoteType.SURVEY
import com.woocommerce.android.ui.inbox.domain.InboxNote.Status.ACTIONED
import com.woocommerce.android.ui.inbox.domain.InboxNoteAction
import com.woocommerce.android.ui.inbox.domain.InboxRepository
import com.woocommerce.android.util.DateUtils
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.wordpress.android.util.DateTimeUtils
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val dateUtils: DateUtils,
    private val inboxRepository: InboxRepository,
    savedState: SavedStateHandle,
) : ScopedViewModel(savedState) {
    companion object {
        const val DEFAULT_DISMISS_LABEL = "Dismiss" // Inbox notes are not localised and always displayed in English
    }

    private val _inboxState = MutableLiveData<InboxState>()
    val inboxState: LiveData<InboxState> = _inboxState

    init {
        _inboxState.value = InboxState(isLoading = true)
        viewModelScope.launch {
            inboxRepository.fetchInboxNotes()
            inboxNotesLocalUpdates().collectLatest { _inboxState.value = it }
        }
    }

    fun dismissAllNotes() {
        viewModelScope.launch {
            inboxRepository.dismissAllNotesForCurrentSite()
        }
    }

    private fun inboxNotesLocalUpdates() =
        inboxRepository.observeInboxNotes()
            .map { inboxNotes ->
                val notes = inboxNotes.map { it.toInboxNoteUi() }
                InboxState(isLoading = false, notes = notes)
            }

    private fun InboxNote.toInboxNoteUi() =
        InboxNoteUi(
            id = id,
            title = title,
            description = description,
            dateCreated = formatNoteCreationDate(dateCreated),
            isSurvey = type == SURVEY,
            isActioned = status == ACTIONED,
            actions = mapInboxActionsToUi(),
        )

    private fun InboxNote.mapInboxActionsToUi(): List<InboxNoteActionUi> {
        if (type == SURVEY && status == ACTIONED) {
            return emptyList()
        }

        val noteActionsUi = actions
            .map { it.toInboxActionUi(id) }
            .toMutableList()

        addDismissActionIfMissing(noteActionsUi)

        return noteActionsUi
    }

    private fun InboxNote.addDismissActionIfMissing(noteActionsUi: MutableList<InboxNoteActionUi>) {
        if (!actionsHaveDismiss(noteActionsUi)) {
            noteActionsUi.add(
                InboxNoteActionUi(
                    id = 0,
                    parentNoteId = id,
                    label = DEFAULT_DISMISS_LABEL,
                    textColor = R.color.color_surface_variant,
                    url = "",
                    onClick = { _, noteId -> dismissNote(noteId) }
                )
            )
        }
    }

    private fun actionsHaveDismiss(noteActionsUi: List<InboxNoteActionUi>) =
        noteActionsUi.any { it.label == DEFAULT_DISMISS_LABEL }

    private fun InboxNoteAction.toInboxActionUi(parentNoteId: Long) =
        InboxNoteActionUi(
            id = id,
            parentNoteId = parentNoteId,
            label = label,
            textColor = getActionTextColor(),
            url = url,
            onClick = ::handleInboxNoteAction
        )

    private fun handleInboxNoteAction(actionId: Long, noteId: Long) {
        val clickedNote = inboxState.value?.notes?.firstOrNull { noteId == it.id }
        clickedNote?.let {
            when {
                it.isSurvey -> markSurveyAsAnswered(clickedNote.id, actionId)
                else -> openActionUrl(clickedNote, actionId)
            }
        }
    }

    private fun openActionUrl(clickedNote: InboxNoteUi, actionId: Long) {
        val clickedAction = clickedNote.actions.firstOrNull { actionId == it.id }
        clickedAction?.let {
            if (it.url.isNotEmpty()) {
                viewModelScope.launch {
                    inboxRepository.markInboxNoteAsActioned(clickedNote.id, actionId)
                }
                triggerEvent(InboxNoteActionEvent.OpenUrlEvent(it.url))
            }
        }
    }

    private fun markSurveyAsAnswered(noteId: Long, actionId: Long) {
        viewModelScope.launch {
            inboxRepository.markInboxNoteAsActioned(noteId, actionId)
        }
    }

    private fun dismissNote(noteId: Long) {
        viewModelScope.launch {
            inboxRepository.dismissNote(noteId)
        }
    }

    private fun InboxNoteAction.getActionTextColor() =
        if (isPrimary) R.color.color_secondary
        else R.color.color_surface_variant

    @SuppressWarnings("MagicNumber", "ReturnCount")
    private fun formatNoteCreationDate(createdDate: String): String {
        val creationDate = dateUtils.getDateFromIso8601String(createdDate)
        val now = Date()

        val minutes = DateTimeUtils.minutesBetween(now, creationDate)
        when {
            minutes < 1 -> return resourceProvider.getString(R.string.inbox_note_recency_now)
            minutes < 60 -> return resourceProvider.getString(R.string.inbox_note_recency_minutes, minutes)
        }
        val hours = DateTimeUtils.hoursBetween(now, creationDate)
        when {
            hours == 1 -> return resourceProvider.getString(R.string.inbox_note_recency_one_hour)
            hours < 24 -> return resourceProvider.getString(R.string.inbox_note_recency_hours, hours)
        }
        val days = DateTimeUtils.daysBetween(now, creationDate)
        when {
            days == 1 -> return resourceProvider.getString(R.string.inbox_note_recency_one_day)
            days < 30 -> return resourceProvider.getString(R.string.inbox_note_recency_days, days)
        }
        return resourceProvider.getString(
            R.string.inbox_note_recency_date_time,
            dateUtils.toDisplayMMMddYYYYDate(creationDate?.time ?: 0) ?: ""
        )
    }

    data class InboxState(
        val isLoading: Boolean = false,
        val notes: List<InboxNoteUi> = emptyList()
    )

    data class InboxNoteUi(
        val id: Long,
        val title: String,
        val description: String,
        val dateCreated: String,
        val isSurvey: Boolean,
        val isActioned: Boolean,
        val actions: List<InboxNoteActionUi>
    )

    data class InboxNoteActionUi(
        val id: Long,
        val parentNoteId: Long,
        val label: String,
        @ColorRes val textColor: Int,
        val url: String,
        val isDismissing: Boolean = false,
        val onClick: (Long, Long) -> Unit
    )

    sealed class InboxNoteActionEvent : MultiLiveEvent.Event() {
        data class OpenUrlEvent(val url: String) : InboxNoteActionEvent()
    }
}
