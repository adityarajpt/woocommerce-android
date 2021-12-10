package com.woocommerce.android.ui.main

import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.rule.ActivityTestRule
import com.woocommerce.android.BuildConfig
import com.woocommerce.android.helpers.InitializationRule
import com.woocommerce.android.helpers.TestBase
import com.woocommerce.android.screenshots.TabNavComponent
import com.woocommerce.android.screenshots.login.WelcomeScreen
import com.woocommerce.android.screenshots.products.ProductListScreen
import com.woocommerce.android.screenshots.reviews.ReviewsListScreen
import com.woocommerce.android.screenshots.util.MocksReader
import com.woocommerce.android.screenshots.util.ProductData
import com.woocommerce.android.screenshots.util.ReviewData
import com.woocommerce.android.screenshots.util.iterator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ProductsScreenAccessibilityTest : TestBase() {
    @get:Rule(order = 0)
    val rule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val initRule = InitializationRule()

    @get:Rule(order = 2)
    var activityRule = ActivityTestRule(MainActivity::class.java)

    @Before
    fun setUp() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
        WelcomeScreen
            .logoutIfNeeded()
            .selectLogin()
            .proceedWith(BuildConfig.SCREENSHOTS_URL)
            .proceedWith(BuildConfig.SCREENSHOTS_USERNAME)
            .proceedWith(BuildConfig.SCREENSHOTS_PASSWORD)
    }

    @Test
    fun checkProductScreenAccessibilityTest() {
        TabNavComponent().gotoProductsScreen()

        val productsJSONArray = MocksReader().readAllProductsToArray()

        for (productJSON in productsJSONArray.iterator()) {
            val productData = mapJSONToProduct(productJSON)

            ProductListScreen()
                .scrollToProduct(productData.name)
        }
    }

    @Test
    fun checkProductDetailScreenAccessibilityTest() {
        TabNavComponent().gotoProductsScreen()

        val productsJSONArray = MocksReader().readAllProductsToArray()

        for (productJSON in productsJSONArray.iterator()) {
            val productData = mapJSONToProduct(productJSON)

            ProductListScreen()
                .scrollToProduct(productData.name)
                .selectProductByName(productData.name)
                .goBackToProductsScreen()
        }
    }

    private fun mapJSONToProduct(productJSON: JSONObject): ProductData {
        return ProductData(
            id = productJSON.getInt("id"),
            name = productJSON.getString("name"),
            stockStatusRaw = productJSON.getString("stock_status"),
            priceDiscountedRaw = productJSON.getString("price"),
            priceRegularRaw = productJSON.getString("regular_price"),
            typeRaw = productJSON.getString("type"),
            rating = productJSON.getInt("average_rating"),
            reviewsCount = productJSON.getInt("rating_count")
        )
    }

}
