package com.woocommerce.android.ui.orders.creation

import android.os.Parcelable
import androidx.lifecycle.*
import com.woocommerce.android.R.string
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.*
import com.woocommerce.android.extensions.runWithContext
import com.woocommerce.android.model.Address
import com.woocommerce.android.model.Order
import com.woocommerce.android.model.Order.OrderStatus
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewOrderStatusSelector
import com.woocommerce.android.ui.orders.creation.CreateOrUpdateOrderDraft.OrderDraftUpdateStatus
import com.woocommerce.android.ui.orders.creation.navigation.OrderCreationNavigationTarget.*
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.ui.products.ParameterRepository
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.util.FeatureFlag
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class OrderCreationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers,
    private val orderDetailRepository: OrderDetailRepository,
    private val orderCreationRepository: OrderCreationRepository,
    private val mapItemToProductUiModel: MapItemToProductUiModel,
    private val createOrUpdateOrderDraft: CreateOrUpdateOrderDraft,
    private val createOrderItem: CreateOrderItem,
    parameterRepository: ParameterRepository
) : ScopedViewModel(savedState) {
    companion object {
        private const val PARAMETERS_KEY = "parameters_key"
    }

    val viewStateData = LiveDataDelegate(savedState, ViewState())
    private var viewState by viewStateData

    private val _orderDraft = savedState.getStateFlow(viewModelScope, Order.EMPTY)
    val orderDraft = _orderDraft
        .onEach {
            viewState = viewState.copy(
                isOrderValidForCreation = it.items.isNotEmpty() &&
                    it.shippingAddress != Address.EMPTY &&
                    it.billingAddress != Address.EMPTY
            )
        }
        .asLiveData()

    val orderStatusData: LiveData<OrderStatus> = _orderDraft
        .map { it.status }
        .distinctUntilChanged()
        .map { status ->
            withContext(dispatchers.io) {
                orderDetailRepository.getOrderStatus(status.value)
            }
        }.asLiveData()

    val products: LiveData<List<ProductUIModel>> = _orderDraft
        .map { order -> order.items.filter { it.quantity > 0 } }
        .distinctUntilChanged()
        .map { items ->
            items.map { item -> mapItemToProductUiModel(item) }
        }.asLiveData()

    private val retryOrderDraftUpdateTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val currentDraft
        get() = _orderDraft.value

    init {
        _orderDraft.update {
            it.copy(currency = parameterRepository.getParameters(PARAMETERS_KEY, savedState).currencyCode.orEmpty())
        }
        monitorOrderChanges()
    }

    fun onOrderStatusChanged(status: Order.Status) = _orderDraft.update { it.copy(status = status) }

    fun onCustomerNoteEdited(newNote: String) = _orderDraft.update { it.copy(customerNote = newNote) }

    fun onIncreaseProductsQuantity(id: Long) = _orderDraft.update { it.adjustProductQuantity(id, +1) }

    fun onDecreaseProductsQuantity(id: Long) = _orderDraft.update { it.adjustProductQuantity(id, -1) }

    fun onRemoveProduct(item: Order.Item) = _orderDraft.update {
        if (FeatureFlag.ORDER_CREATION_M2.isEnabled()) {
            it.adjustProductQuantity(item.uniqueId, -item.quantity.toInt())
        } else {
            it.updateItems(it.items - item)
        }
    }

    fun onProductSelected(remoteProductId: Long, variationId: Long? = null) {
        AnalyticsTracker.track(Stat.ORDER_PRODUCT_ADD)
        val uniqueId = variationId ?: remoteProductId
        viewModelScope.launch {
            _orderDraft.value.items.toMutableList().apply {
                val index = indexOfFirst { it.uniqueId == uniqueId }
                if (index != -1) {
                    val item = get(index)
                    set(index, item.copy(quantity = item.quantity + 1))
                    return@apply
                }
                // Create a new item
                val item = createOrderItem(remoteProductId, variationId)
                add(item)
            }.let { items -> _orderDraft.update { it.updateItems(items) } }
        }
    }

    fun onCustomerAddressEdited(billingAddress: Address, shippingAddress: Address) {
        if (billingAddress != _orderDraft.value.billingAddress) {
            AnalyticsTracker.track(Stat.ORDER_CUSTOMER_ADD_BILLING)
        }

        if (shippingAddress != _orderDraft.value.shippingAddress) {
            AnalyticsTracker.track(Stat.ORDER_CUSTOMER_ADD_SHIPPING)
        }

        _orderDraft.update {
            it.copy(
                billingAddress = billingAddress,
                shippingAddress = shippingAddress.takeIf { it != Address.EMPTY } ?: billingAddress
            )
        }
    }

    fun onEditOrderStatusClicked(currentStatus: OrderStatus) {
        launch(dispatchers.io) {
            orderDetailRepository
                .getOrderStatusOptions().toTypedArray()
                .runWithContext(dispatchers.main) {
                    triggerEvent(
                        ViewOrderStatusSelector(
                            currentStatus = currentStatus.statusKey,
                            orderStatusList = it
                        )
                    )
                }
        }
    }

    fun onCustomerClicked() {
        triggerEvent(EditCustomer)
    }

    fun onCustomerNoteClicked() {
        triggerEvent(EditCustomerNote)
    }

    fun onAddProductClicked() {
        triggerEvent(AddProduct)
    }

    fun onProductClicked(item: Order.Item) {
        triggerEvent(ShowProductDetails(item))
    }

    fun onRetryPaymentsClicked() {
        retryOrderDraftUpdateTrigger.tryEmit(Unit)
    }

    fun onCreateOrderClicked(order: Order) {
        viewModelScope.launch {
            viewState = viewState.copy(isProgressDialogShown = true)
            orderCreationRepository.placeOrder(order).fold(
                onSuccess = {
                    triggerEvent(ShowSnackbar(string.order_creation_success_snackbar))
                    triggerEvent(ShowCreatedOrder(it.id))
                },
                onFailure = {
                    viewState = viewState.copy(isProgressDialogShown = false)
                    triggerEvent(ShowSnackbar(string.order_creation_failure_snackbar))
                }
            )
        }
    }

    /**
     * Monitor order changes, and update the remote draft to update price totals
     */
    private fun monitorOrderChanges() {
        if (!FeatureFlag.ORDER_CREATION_M2.isEnabled()) return
        viewModelScope.launch {
            createOrUpdateOrderDraft(_orderDraft, retryOrderDraftUpdateTrigger)
                .collect { updateStatus ->
                    when (updateStatus) {
                        OrderDraftUpdateStatus.Ongoing ->
                            viewState = viewState.copy(isUpdatingOrderDraft = true, showOrderUpdateSnackbar = false)
                        OrderDraftUpdateStatus.Failed ->
                            viewState = viewState.copy(isUpdatingOrderDraft = false, showOrderUpdateSnackbar = true)
                        is OrderDraftUpdateStatus.Succeeded -> {
                            viewState = viewState.copy(isUpdatingOrderDraft = false, showOrderUpdateSnackbar = false)
                            _orderDraft.update { currentDraft ->
                                // Keep the user's selected status
                                updateStatus.order.copy(status = currentDraft.status)
                            }
                        }
                    }
                }
        }
    }

    @Parcelize
    data class ViewState(
        val isProgressDialogShown: Boolean = false,
        private val isOrderValidForCreation: Boolean = false,
        val isUpdatingOrderDraft: Boolean = false,
        val showOrderUpdateSnackbar: Boolean = false
    ) : Parcelable {
        @IgnoredOnParcel
        val canCreateOrder: Boolean = isOrderValidForCreation && !isUpdatingOrderDraft && !showOrderUpdateSnackbar
    }
}

data class ProductUIModel(
    val item: Order.Item,
    val imageUrl: String,
    val isStockManaged: Boolean,
    val stockQuantity: Double,
    val canDecreaseQuantity: Boolean
)
