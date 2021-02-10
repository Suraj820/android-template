package com.skyyo.template.features.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.skyyo.template.databinding.FragmentHomeBinding
import com.skyyo.template.extensions.longToast
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applySystemWindowInsetsToPadding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val viewModel by viewModels<HomeViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!::binding.isInitialized) {
            binding = FragmentHomeBinding.inflate(inflater)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()
        with(binding) {
            tvTitle.text = "Home Screen Title"
            btnDoSmth.text = "Sign In"
            btnDoSmth.setOnClickListener { viewModel.goSignIn() }
        }
        observeEvents()
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            for (event in viewModel.events)
                when (event) {
                    is UpdateProgress -> {
                    }
                    is ShowLongToast -> longToast(getString(event.stringId))
                }
        }
    }

    private fun applyInsets() {
        binding.tvTitle.applySystemWindowInsetsToPadding(top = true)
        // etc
    }
}
