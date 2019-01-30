package com.woocommerce.android.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.support.annotation.StringRes
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.util.ActivityUtils
import com.woocommerce.android.util.WooAnimUtils
import com.woocommerce.android.util.WooAnimUtils.Duration
import kotlinx.android.synthetic.main.wc_empty_view.view.*
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.util.DisplayUtils

class WCEmptyView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {
    private var showNoCustomersImage = true
    private var siteModel: SiteModel? = null

    init {
        View.inflate(context, R.layout.wc_empty_view, this)
        orientation = LinearLayout.VERTICAL
        checkOrientation()
    }

    /**
     * the main activity has android:configChanges="orientation|screenSize" in the manifest, so we have to
     * handle screen rotation here
     */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        checkOrientation()
    }

    /**
     * Hide the "no customers" image in landscape since there isn't enough room for it on most devices
     */
    private fun checkOrientation() {
        val isLandscape = DisplayUtils.isLandscape(context)
        empty_view_image.visibility = if (showNoCustomersImage && !isLandscape) View.VISIBLE else View.GONE
    }

    fun setSite(site: SiteModel?) {
        siteModel = site
    }

    fun show(@StringRes messageId: Int, showImage: Boolean = true, showShareButton: Boolean = true) {
        showNoCustomersImage = showImage
        checkOrientation()

        empty_view_text.text = context.getText(messageId)

        if (showShareButton && siteModel != null) {
            empty_view_share_button.visibility = View.VISIBLE
            empty_view_share_button.setOnClickListener {
                // TODO: need to support ORDERS_LIST_SHARE_YOUR_STORE_BUTTON_TAPPED
                AnalyticsTracker.track(Stat.DASHBOARD_SHARE_YOUR_STORE_BUTTON_TAPPED)
                ActivityUtils.shareStoreUrl(context, siteModel!!.url)
            }
        } else {
            empty_view_share_button.visibility = View.GONE
        }

        if (visibility != View.VISIBLE) {
            WooAnimUtils.fadeIn(this, Duration.LONG)
        }
    }

    fun hide() {
        if (visibility == View.VISIBLE) {
            WooAnimUtils.fadeOut(this, Duration.LONG)
        }
    }

    // TODO: remove this before merging
    fun test(@StringRes messageId: Int) {
        show(messageId)
        Handler().postDelayed({
            hide()
            Handler().postDelayed({
                test(messageId)
            }, 2000)
        }, 2000)
    }
}
