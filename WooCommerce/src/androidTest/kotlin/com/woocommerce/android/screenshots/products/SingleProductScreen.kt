package com.woocommerce.android.screenshots.products

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.woocommerce.android.R
import com.woocommerce.android.screenshots.util.ProductData
import com.woocommerce.android.screenshots.util.Screen
import org.hamcrest.Matchers
import java.lang.Thread.sleep

class SingleProductScreen : Screen {
    companion object {
        const val PRODUCT_DETAIL_CONTAINER = R.id.productDetail_root
    }

    constructor() : super(PRODUCT_DETAIL_CONTAINER)

    fun goBackToProductsScreen(): ProductListScreen {
        pressBack()
        return ProductListScreen()
    }

    fun assertProductDetails(productData: ProductData): SingleProductScreen {
        // select the parent page container
        val view = Espresso.onView(ViewMatchers.withId(PRODUCT_DETAIL_CONTAINER))

        view.check(
            ViewAssertions.matches(
                ViewMatchers.hasDescendant(
                    // check that the page has an editText with the product name
                    Matchers.allOf(
                        ViewMatchers.withId(R.id.editText),
                        ViewMatchers.withText(productData.name),
                    )
                )
            )
        )
        return SingleProductScreen()
    }
}
