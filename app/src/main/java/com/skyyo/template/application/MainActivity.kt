package com.skyyo.template.application

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.skyyo.template.R
import com.skyyo.template.application.persistance.DataStoreManager
import com.skyyo.template.application.persistance.room.AppDatabase
import com.skyyo.template.databinding.ActivityMainBinding
import com.skyyo.template.utils.eventDispatchers.NavigationDispatcher
import com.skyyo.template.utils.eventDispatchers.UnauthorizedEventDispatcher
import com.skyyo.template.utils.extensions.changeSystemBars
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

typealias onDestinationChanged = NavController.OnDestinationChangedListener

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val destinationChangedListener = onDestinationChanged { _, _, arguments ->
        binding.fragmentHost.changeSystemBars(arguments?.getBoolean("lightBars") ?: false)
        binding.bnv.isVisible = arguments?.getBoolean("bottomNavigationVisible") ?: false
    }
    private var readyToDismissSplash = false

    @Inject
    lateinit var navigationDispatcher: NavigationDispatcher

    @Inject
    lateinit var unauthorizedEventDispatcher: UnauthorizedEventDispatcher

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        observeSplashScreenVisibility()
        lockIntoPortrait()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge()
        lifecycleScope.apply {
            launch {
                withContext(Dispatchers.IO) {
                    val startDestination = provideStartDestination()
                    withContext(Dispatchers.Main) {
                        initNavigation(startDestination)
                        readyToDismissSplash = true
                    }
                }
            }
            launchWhenResumed { observeUnauthorizedEvent() }
            launchWhenResumed { observeNavigationCommands() }
        }
    }

    private fun observeSplashScreenVisibility() {
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (readyToDismissSplash) {
                    // The content is ready; start drawing.
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    true
                } else {
                    // The content is not ready; suspend.
                    false
                }
            }
        })
    }

    private fun initNavigation(startDestination: Int) {
        (supportFragmentManager.findFragmentById(R.id.fragmentHost) as NavHostFragment).also { navHost ->
            val navInflater = navHost.navController.navInflater
            val navGraph = navInflater.inflate(R.navigation.navigation_graph)
            navGraph.setStartDestination(startDestination)
            navHost.navController.graph = navGraph
            navController = navHost.navController
            navController.addOnDestinationChangedListener(destinationChangedListener)
            binding.bnv.setOnItemSelectedListener { item ->
                val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)
                builder.setPopUpTo(R.id.fragmentHome, inclusive = false, saveState = true)
                val options = builder.build()
                try {
                    navController.navigate(item.itemId, null, options)
                    true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
        }
    }

    private fun lockIntoPortrait() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private suspend fun provideStartDestination(): Int {
        val accessToken = dataStoreManager.getAccessToken()
        return if (accessToken == null) R.id.fragmentSignIn else R.id.fragmentHome
    }

    private suspend fun observeUnauthorizedEvent() {
        for (command in unauthorizedEventDispatcher.unauthorizedEventEmitter) {
            if (isFinishing) return
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
            @Suppress("GlobalCoroutineUsage")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                appDatabase.clearAllTables()
                dataStoreManager.clearData()
            }
            finish()
            startActivity(intent)
        }
    }

    private suspend fun observeNavigationCommands() {
        for (command in navigationDispatcher.navigationEmitter) command.invoke(navController)
    }

    fun returnToTopOfRootStack() {
        binding.bnv.selectedItemId = R.id.fragmentHome
    }
}
