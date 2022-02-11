package com.woocommerce.android.ui.orders.creation.fees

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentOrderCreationAddFeeBinding
import com.woocommerce.android.extensions.takeIfNotEqualTo
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.orders.creation.OrderCreationViewModel
import com.woocommerce.android.ui.orders.creation.fees.OrderCreationAddFeeViewModel.UpdateFee
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OrderCreationAddFeeFragment :
    BaseFragment(R.layout.fragment_order_creation_add_fee) {
    private val sharedViewModel by hiltNavGraphViewModels<OrderCreationViewModel>(R.id.nav_graph_order_creations)
    private val addFeeViewModel by viewModels<OrderCreationAddFeeViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(FragmentOrderCreationAddFeeBinding.bind(view)) {
            bindViews()
            setupObservers()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.menu_done, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_done -> {
                addFeeViewModel.onDoneSelected()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun getFragmentTitle() = getString(R.string.order_creation_add_fee)

    private fun FragmentOrderCreationAddFeeBinding.bindViews() {
        feeTypeSwitch.setOnCheckedChangeListener { _, isChecked ->
            addFeeViewModel.onPercentageSwitchChanged(isChecked)
        }
        feeEditText.value.observe(viewLifecycleOwner) {
            addFeeViewModel.onFeeInputValueChanged(it)
        }
    }

    private fun FragmentOrderCreationAddFeeBinding.setupObservers() {
        addFeeViewModel.viewStateData.observe(viewLifecycleOwner) { old, new ->
            new.feeInputValue.takeIfNotEqualTo(old?.feeInputValue) {
                feeEditText.setValueIfDifferent(it)
            }
            new.isPercentageSelected.takeIfNotEqualTo(old?.isPercentageSelected) {
                feeTypeSwitch.isChecked = it
            }
        }

        addFeeViewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is UpdateFee -> {
                    sharedViewModel.onNewFeeSubmitted(event.amount, event.feeType)
                    findNavController().navigateUp()
                }
            }
        }
    }
}