package com.woocommerce.android.ui.orders.notes

import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.navArgs
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.ADD_ORDER_NOTE_ADD_BUTTON_TAPPED
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.ADD_ORDER_NOTE_EMAIL_NOTE_TO_CUSTOMER_TOGGLED
import com.woocommerce.android.databinding.FragmentAddOrderNoteBinding
import com.woocommerce.android.extensions.navigateBackWithResult
import com.woocommerce.android.model.OrderNote
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.ui.dialog.WooDialog
import com.woocommerce.android.ui.main.MainActivity.Companion.BackPressListener
import com.woocommerce.android.ui.orders.notes.AddOrderNoteContract.Presenter
import com.woocommerce.android.util.AnalyticsUtils
import org.wordpress.android.fluxc.model.order.OrderIdentifier
import org.wordpress.android.util.ActivityUtils
import javax.inject.Inject

class AddOrderNoteFragment : BaseFragment(R.layout.fragment_add_order_note),
    AddOrderNoteContract.View,
    BackPressListener {
    companion object {
        const val TAG = "AddOrderNoteFragment"
        private const val FIELD_NOTE_TEXT = "note_text"
        private const val FIELD_IS_CUSTOMER_NOTE = "is_customer_note"
        private const val FIELD_IS_CONFIRMING_DISCARD = "is_confirming_discard"
        const val KEY_ADD_NOTE_RESULT = "key_add_note_result"
    }

    @Inject lateinit var presenter: Presenter
    @Inject lateinit var uiMessageResolver: UIMessageResolver

    private lateinit var orderId: OrderIdentifier
    private lateinit var orderNumber: String

    private var isConfirmingDiscard = false
    private var shouldShowDiscardDialog = true

    private var _binding: FragmentAddOrderNoteBinding? = null
    private val binding get() = _binding!!

    private val navArgs: AddOrderNoteFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAddOrderNoteBinding.bind(view)

        orderId = navArgs.orderId
        orderNumber = navArgs.orderNumber

        savedInstanceState?.let { state ->
            binding.addNoteSwitch.isChecked = state.getBoolean(FIELD_IS_CUSTOMER_NOTE)
            if (state.getBoolean(FIELD_IS_CONFIRMING_DISCARD)) {
                confirmDiscard()
            }
            state.getString(FIELD_NOTE_TEXT)?.let {
                binding.addNoteEditor.setText(it)
            }
        }

        if (orderId.isEmpty() || orderNumber.isEmpty()) {
            activity?.onBackPressed()
            return
        }

        if (presenter.hasBillingEmail(orderId)) {
            binding.addNoteSwitch.setOnCheckedChangeListener { _, isChecked ->
                AnalyticsTracker.track(
                        ADD_ORDER_NOTE_EMAIL_NOTE_TO_CUSTOMER_TOGGLED,
                        mapOf(AnalyticsTracker.KEY_STATE to AnalyticsUtils.getToggleStateLabel(isChecked)))

                val drawableId = if (isChecked) R.drawable.ic_note_public else R.drawable.ic_note_private
                binding.addNoteIcon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), drawableId))
            }
        } else {
            binding.addNoteSwitch.visibility = View.GONE
        }

        if (savedInstanceState == null) {
            binding.addNoteEditor.requestFocus()
            ActivityUtils.showKeyboard(binding.addNoteEditor)
        }

        presenter.takeView(this)
    }

    override fun getFragmentTitle() = getString(R.string.orderdetail_orderstatus_ordernum, navArgs.orderNumber)

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onStop() {
        super.onStop()
        WooDialog.onCleared()
        activity?.let {
            ActivityUtils.hideKeyboard(it)
        }
    }

    override fun onDestroyView() {
        presenter.dropView()
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_add, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                AnalyticsTracker.track(ADD_ORDER_NOTE_ADD_BUTTON_TAPPED)
                val noteText = getNoteText()
                if (noteText.isNotEmpty()) {
                    val orderNote = OrderNote(
                        isCustomerNote = binding.addNoteSwitch.isChecked,
                        note = noteText
                    )
                    shouldShowDiscardDialog = false
                    navigateBackWithResult(KEY_ADD_NOTE_RESULT, orderNote)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FIELD_NOTE_TEXT, getNoteText())
        outState.putBoolean(FIELD_IS_CUSTOMER_NOTE, binding.addNoteSwitch.isChecked)
        outState.putBoolean(FIELD_IS_CONFIRMING_DISCARD, isConfirmingDiscard)
        super.onSaveInstanceState(outState)
    }

    override fun getNoteText() = binding.addNoteEditor.text?.toString()?.trim() ?: ""

    /**
     * Prevent back press in the main activity if the user entered a note so we can confirm the discard
     */
    override fun onRequestAllowBackPress(): Boolean {
        return if (getNoteText().isNotEmpty() && shouldShowDiscardDialog) {
            confirmDiscard()
            false
        } else {
            true
        }
    }

    override fun confirmDiscard() {
        isConfirmingDiscard = true
        WooDialog.showDialog(
                requireActivity(),
                messageId = R.string.discard_message,
                positiveButtonId = R.string.discard,
                posBtnAction = DialogInterface.OnClickListener { _, _ ->
                    shouldShowDiscardDialog = false
                    activity?.onBackPressed()
                },
                negativeButtonId = R.string.keep_editing,
                negBtnAction = DialogInterface.OnClickListener { _, _ ->
                    isConfirmingDiscard = false
                })
    }

    override fun showAddOrderNoteSnack() {
        uiMessageResolver.getSnack(R.string.add_order_note_added).show()
    }

    override fun showAddOrderNoteErrorSnack() {
        uiMessageResolver.getSnack(R.string.add_order_note_error).show()
    }

    override fun showOfflineSnack() {
        uiMessageResolver.showOfflineSnack()
    }
}
