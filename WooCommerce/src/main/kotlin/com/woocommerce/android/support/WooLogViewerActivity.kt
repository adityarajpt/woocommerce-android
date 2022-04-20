package com.woocommerce.android.support

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.woocommerce.android.R
import com.woocommerce.android.databinding.ActivityLogviewerBinding
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.util.AppThemeUtils
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T
import com.woocommerce.android.util.copyToClipboard
import org.wordpress.android.util.ToastUtils

class WooLogViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityLogviewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WooThemeWithBackground {
                    WooLogViewerScreen(
                        isDarkThemeEnabled = AppThemeUtils.isDarkThemeActive(this@WooLogViewerActivity),
                        onBackPress = { finish() },
                        onCopyButtonClick = { copyAppLogToClipboard() },
                        onShareButtonClick = { shareAppLog() }
                    )
                }
            }
        }
    }

    private fun shareAppLog() {
        WooLog.addDeviceInfoEntry(T.DEVICE, WooLog.LogLevel.w)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, WooLog.toString())
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " " + title)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (ex: android.content.ActivityNotFoundException) {
            ToastUtils.showToast(this, R.string.logviewer_share_error)
        }
    }

    private fun copyAppLogToClipboard() {
        try {
            WooLog.addDeviceInfoEntry(T.DEVICE, WooLog.LogLevel.w)
            copyToClipboard("AppLog", WooLog.toString())
            ToastUtils.showToast(this, R.string.logviewer_copied_to_clipboard)
        } catch (e: Exception) {
            WooLog.e(T.UTILS, e)
            ToastUtils.showToast(this, R.string.logviewer_error_copy_to_clipboard)
        }
    }
}
