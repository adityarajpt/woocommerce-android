package com.woocommerce.android.ui.orders.details

import com.woocommerce.android.extensions.semverCompareTo
import com.woocommerce.android.model.Order
import com.woocommerce.android.util.FeatureFlag
import javax.inject.Inject

class ShippingLabelOnboardingRepository @Inject constructor(
    private val orderDetailRepository: OrderDetailRepository
) {
   private companion object {
       // The required version to support shipping label creation
       const val SUPPORTED_WCS_VERSION = "1.25.11"
       const val SUPPORTED_WCS_CURRENCY = "USD"
       const val SUPPORTED_WCS_COUNTRY = "US"
   }

    val isShippingPluginReady: Boolean by lazy {
        val pluginInfo = orderDetailRepository.getWooServicesPluginInfo()
        pluginInfo.isInstalled && pluginInfo.isActive &&
            (pluginInfo.version ?: "0.0.0").semverCompareTo(SUPPORTED_WCS_VERSION) >= 0
    }

    fun shouldShowInstallWcShippingFor(order: Order): Boolean {
        return !isShippingPluginReady
            && orderDetailRepository.getStoreCountryCode() == SUPPORTED_WCS_COUNTRY
            && order.currency == SUPPORTED_WCS_CURRENCY
            && !order.isCashPayment
            && !hasVirtualProductsOnly(order)
            && FeatureFlag.WC_SHIPPING_BANNER.isEnabled()
    }

    private fun hasVirtualProductsOnly(order: Order): Boolean {
        return if (order.items.isNotEmpty()) {
            val remoteProductIds = order.getProductIds()
            orderDetailRepository.hasVirtualProductsOnly(remoteProductIds)
        } else false
    }
}
