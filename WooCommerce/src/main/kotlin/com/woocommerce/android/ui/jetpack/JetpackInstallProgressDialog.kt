package com.woocommerce.android.ui.jetpack

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.databinding.DialogJetpackInstallProgressBinding
import com.woocommerce.android.extensions.*
import com.woocommerce.android.support.HelpActivity
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.jetpack.JetpackInstallViewModel.InstallStatus
import com.woocommerce.android.ui.jetpack.JetpackInstallViewModel.InstallStatus.*
import com.woocommerce.android.ui.jetpack.JetpackInstallViewModel.FailureType.*
import com.woocommerce.android.util.ChromeCustomTabUtils
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.util.DisplayUtils
import javax.inject.Inject

@AndroidEntryPoint
class JetpackInstallProgressDialog : DialogFragment(R.layout.dialog_jetpack_install_progress) {
    companion object {
        private const val TABLET_LANDSCAPE_WIDTH_RATIO = 0.35f
        private const val TABLET_LANDSCAPE_HEIGHT_RATIO = 0.8f
        private const val JETPACK_INSTALL_URL = "plugin-install.php?tab=plugin-information&plugin=jetpack"
        private const val JETPACK_ACTIVATE_URL = "plugins.php"
        private const val ICON_NOT_DONE = R.drawable.ic_progress_circle_start
        private const val ICON_DONE = R.drawable.ic_progress_circle_complete
    }

    @Inject lateinit var selectedSite: SelectedSite

    private val viewModel: JetpackInstallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use fullscreen style for all cases except tablet in landscape mode
        setStyle(STYLE_NO_TITLE, if (isTabletLandscape()) R.style.Theme_Woo_Dialog else R.style.Theme_Woo)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Specify transition animations
        dialog?.window?.attributes?.windowAnimations = R.style.Woo_Animations_Dialog

        val binding = DialogJetpackInstallProgressBinding.bind(view)

        with(binding.subtitle) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(context.getString(R.string.jetpack_install_start_default_name))

            selectedSite.get().name?.let {
                stringBuilder.append(" <b>${selectedSite.get().name}</b> ")
            }

            text = HtmlCompat.fromHtml(
                context.getString(R.string.jetpack_install_progress_subtitle, stringBuilder.toString()),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }

        binding.jetpackProgressActionButton.setOnClickListener {
            findNavController().navigateSafely(
                JetpackInstallProgressDialogDirections.actionJetpackInstallProgressDialogToDashboard()
            )
        }

        binding.contactButton.setOnClickListener {
            activity?.startHelpActivity(HelpActivity.Origin.JETPACK_INSTALLATION)
            AnalyticsTracker.track(Stat.JETPACK_INSTALL_CONTACT_SUPPORT_BUTTON_TAPPED)
        }

        setupObservers(binding)
    }

    override fun onStart() {
        super.onStart()
        if (isTabletLandscape()) {
            requireDialog().window!!.setLayout(
                (DisplayUtils.getWindowPixelWidth(requireContext()) * TABLET_LANDSCAPE_WIDTH_RATIO).toInt(),
                (DisplayUtils.getWindowPixelHeight(requireContext()) * TABLET_LANDSCAPE_HEIGHT_RATIO).toInt()
            )
        }
    }

    private fun setupObservers(binding: DialogJetpackInstallProgressBinding) {
        viewModel.viewStateLiveData.observe(viewLifecycleOwner) { old, new ->
            new.installStatus?.takeIfNotEqualTo(old?.installStatus) {
                updateInstallProgressUi(it, binding)
            }
        }
    }

    private fun updateInstallProgressUi(status: InstallStatus, binding: DialogJetpackInstallProgressBinding) {
        when (status) {
            is Installing -> {
                binding.step1Views.show()
                binding.step1Hide.hide()
                setViewImage(ICON_NOT_DONE, binding.icon2, binding.icon3, binding.icon4)
                setTextWeight(Typeface.BOLD, binding.message1)
                setTextWeight(Typeface.NORMAL, binding.message2, binding.message3, binding.message4)

                binding.jetpackProgressActionButton.hide()
            }
            is Activating -> {
                binding.step2Views.show()
                binding.step2Hide.hide()
                setViewImage(ICON_DONE, binding.icon1)
                setViewImage(ICON_NOT_DONE, binding.icon3, binding.icon4)
                setTextWeight(Typeface.BOLD, binding.message1, binding.message2)
                setTextWeight(Typeface.NORMAL, binding.message3, binding.message4)

                binding.jetpackProgressActionButton.hide()
            }
            is Connecting -> {
                binding.step3Views.show()
                binding.step3Hide.hide()
                setViewImage(ICON_DONE, binding.icon1, binding.icon2)
                setViewImage(ICON_NOT_DONE, binding.icon4)
                setTextWeight(Typeface.BOLD, binding.message1, binding.message2, binding.message3)
                setTextWeight(Typeface.NORMAL, binding.message4)

                binding.jetpackProgressActionButton.hide()
            }
            is Finished -> {
                binding.step4Views.show()
                binding.step4Hide.hide()
                setViewImage(ICON_DONE, binding.icon1, binding.icon2, binding.icon3, binding.icon4)
                setTextWeight(Typeface.BOLD, binding.message1, binding.message2, binding.message3, binding.message4)

                binding.jetpackProgressActionButton.show()
            }
            is Failed -> {
                handleFailedState(status.errorType, binding)
                handleWpAdminButton(status.errorType, binding.openAdminOrRetryButton)
            }
        }
    }

    private fun handleFailedState(
        errorType: JetpackInstallViewModel.FailureType,
        binding: DialogJetpackInstallProgressBinding
    ) {
        val ctx = binding.root.context

        // Title copy
        binding.title.text = ctx.getString(
            R.string.jetpack_install_progress_failed_title,
            when (errorType) {
                INSTALLATION -> ctx.getString(R.string.jetpack_install_progress_failed_reason_installation)
                ACTIVATION -> ctx.getString(R.string.jetpack_install_progress_failed_reason_activation)
                CONNECTION -> ctx.getString(R.string.jetpack_install_progress_failed_reason_connection)
            }
        )

        // Subtitle copy
        val subtitle = ctx.getString(R.string.jetpack_install_progress_failed_try)
        val sb = StringBuilder()
        sb.append(subtitle)
        if (errorType != CONNECTION) {
            sb.append(" ")
            sb.append(
                when (errorType) {
                    INSTALLATION -> {
                        ctx.getString(
                            R.string.jetpack_install_progress_failed_alternative,
                            ctx.getString(R.string.jetpack_install_progress_failed_alternative_install)
                        )
                    }
                    ACTIVATION -> {
                        ctx.getString(
                            R.string.jetpack_install_progress_failed_alternative,
                            ctx.getString(R.string.jetpack_install_progress_failed_alternative_activate)
                        )
                    }
                    else -> ""
                }
            )
        }
        binding.subtitle.text = sb.toString()

        // Button copy
        val btnText = when (errorType) {
            INSTALLATION -> {
                ctx.getString(
                    R.string.jetpack_install_progress_failed_option_wp_admin,
                    ctx.getString(R.string.jetpack_install_progress_option_wp_admin_install)
                )
            }
            ACTIVATION -> {
                ctx.getString(
                    R.string.jetpack_install_progress_failed_option_wp_admin,
                    ctx.getString(R.string.jetpack_install_progress_option_wp_admin_activate)
                )
            }
            CONNECTION -> ctx.getString(R.string.try_again)
        }
        binding.openAdminOrRetryButton.text = btnText

        // Visibilities
        binding.failureHide.hide()
        binding.contactButton.show()
        binding.openAdminOrRetryButton.show()
        binding.jetpackProgressActionButton.hide()
    }

    private fun handleWpAdminButton(errorType: JetpackInstallViewModel.FailureType, button: MaterialButton) {
        when (errorType) {
            INSTALLATION -> {
                button.setOnClickListener {
                    val installJetpackInWpAdminUrl = selectedSite.get().adminUrl + JETPACK_INSTALL_URL
                    ChromeCustomTabUtils.launchUrl(requireContext(), installJetpackInWpAdminUrl)

                    AnalyticsTracker.track(
                        stat = Stat.JETPACK_INSTALL_IN_WPADMIN_BUTTON_TAPPED,
                        properties = mapOf(AnalyticsTracker.KEY_JETPACK_INSTALLATION_SOURCE to "benefits_modal")
                    )
                }
            }
            ACTIVATION -> {
                button.setOnClickListener {
                    val activateJetpackInWpAdminUrl = selectedSite.get().adminUrl + JETPACK_ACTIVATE_URL
                    ChromeCustomTabUtils.launchUrl(requireContext(), activateJetpackInWpAdminUrl)
                }
            }
            else -> {
                // Add sync functionality.
            }
        }
    }

    private fun setViewImage(resId: Int, vararg views: ImageView) = views.forEach { it.setImageResource(resId) }
    private fun setTextWeight(weight: Int, vararg views: TextView) = views.forEach { it.setTypeface(null, weight) }

    private fun isTabletLandscape() = (DisplayUtils.isTablet(context) || DisplayUtils.isXLargeTablet(context)) &&
        DisplayUtils.isLandscape(context)
}
