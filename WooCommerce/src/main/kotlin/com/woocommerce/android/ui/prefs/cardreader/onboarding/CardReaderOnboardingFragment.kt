package com.woocommerce.android.ui.prefs.cardreader.onboarding

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentCardReaderOnboardingBinding
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.util.WooAnimUtils

class CardReaderOnboardingFragment : BaseFragment(R.layout.fragment_card_reader_onboarding) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCardReaderOnboardingBinding.bind(view)
        // showOnboardingFragment(binding)
        showOnboardingLayout(binding, R.layout.fragment_card_reader_onboarding_loading)
    }

    /*private fun showOnboardingFragment(binding: FragmentCardReaderOnboardingBinding) {
        val fragment = CardReaderOnboardingLoadingFragment.newInstance()
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.activity_fade_in,
                R.anim.activity_fade_out,
                R.anim.activity_fade_in,
                R.anim.activity_fade_out
            )
            .replace(binding.container.id, fragment)
            .commitAllowingStateLoss()
    }*/

    private fun showOnboardingLayout(binding: FragmentCardReaderOnboardingBinding, @LayoutRes layoutRes: Int) {
        val duration = WooAnimUtils.Duration.LONG

        // create a fade-in animation which inflates the desired layout when started
        val fadeInAnim = WooAnimUtils.getFadeInAnim(binding.container, duration)
        fadeInAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                LayoutInflater.from(requireActivity()).inflate(layoutRes, binding.container, true)
            }
        })

        // if there are existing views we need to fade them out then start the fade-in animation,
        // otherwise we just start the fade-in animation which will inflate the layout
        if (binding.container.childCount > 0) {
            val fadeOutAnim = WooAnimUtils.getFadeOutAnim(binding.container, duration)
            fadeOutAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.container.removeAllViews()
                    fadeInAnim.start()
                }
            })
            fadeOutAnim.start()
        } else {
            fadeInAnim.start()
        }
    }


    override fun getFragmentTitle() = resources.getString(R.string.payment_onboarding_title)
}
