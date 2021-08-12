package com.woocommerce.android.ui.products.addons

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.extensions.unwrap
import com.woocommerce.android.model.Order.Item.Attribute
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.products.addons.AddonTestFixtures.defaultProduct
import com.woocommerce.android.ui.products.addons.AddonTestFixtures.defaultProductAddon
import com.woocommerce.android.ui.products.addons.AddonTestFixtures.defaultWCOrderItemList
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.WCProductModel
import org.wordpress.android.fluxc.model.order.OrderIdentifier
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCProductStore
import kotlin.test.fail

class AddonRepositoryTest {
    private lateinit var repositoryUnderTest: AddonRepository

    private lateinit var orderStoreMock: WCOrderStore
    private lateinit var productStoreMock: WCProductStore
    private lateinit var selectedSiteMock: SelectedSite
    private lateinit var siteModelMock: SiteModel
    private lateinit var wcProductModelMock: WCProductModel

    private val localSiteID = 321
    private val remoteOrderID = 123L
    private val remoteProductID = 333L

    @Before
    fun setUp() {
        siteModelMock = mock {
            on { id }.doReturn(321)
        }
        orderStoreMock = mock()
        productStoreMock = mock()
        selectedSiteMock = mock {
            on { get() }.doReturn(siteModelMock)
        }

        repositoryUnderTest = AddonRepository(
            orderStoreMock,
            productStoreMock,
            selectedSiteMock
        )

        configureOrderResponse()
        configureProductResponse()
    }

    @Test
    fun `test`() {
        val expectedAttributeList = listOf(
            Attribute("test-key", "test-value")
        )

        val expectedAddonList = listOf(
            defaultProductAddon.copy(name = "test-addon-name")
        )

        repositoryUnderTest.fetchOrderAddonsData(remoteOrderID, remoteProductID)
            ?.unwrap { addons, attributes ->
                assertThat(addons).isNotEmpty
                assertThat(attributes).isNotEmpty
            } ?: fail()
    }

    private fun configureOrderResponse() {
        mock<WCOrderModel>().apply {
            whenever(getLineItemList()).thenReturn(defaultWCOrderItemList)
        }.let {
            whenever(
                orderStoreMock.getOrderByIdentifier(
                    OrderIdentifier(localSiteID, remoteOrderID)
                )
            ).thenReturn(it)
        }
    }

    private fun configureProductResponse() {
        whenever(
            productStoreMock.getProductByRemoteId(
                siteModelMock, remoteProductID
            )
        ).thenReturn(defaultProduct)
    }
}
