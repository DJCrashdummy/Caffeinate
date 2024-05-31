package com.hifnawy.caffeinate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.databinding.DialogChooseThemeBinding
import com.hifnawy.caffeinate.services.KeepAwakeService
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.SharedPrefsManager

class MainActivity : AppCompatActivity(), SharedPrefsManager.SharedPrefsChangedListener, ServiceStatusObserver {

    @Suppress("PrivatePropertyName")
    private val LOG_TAG = MainActivity::class.simpleName
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }
    private val grantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.baseline_check_circle_24) }
    private val notGrantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.baseline_cancel_24) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(sharedPreferences.theme.value)
        if (DynamicColors.isDynamicColorAvailable() && sharedPreferences.isMaterialYouEnabled) DynamicColors.applyToActivityIfAvailable(this@MainActivity)

        enableEdgeToEdge()
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        with(binding) {
            val themeClickListener = View.OnClickListener { showChooseThemeDialog() }

            appThemeCard.setOnClickListener(themeClickListener)
            appThemeButton.setOnClickListener(themeClickListener)

            when (sharedPreferences.theme) {
                SharedPrefsManager.Theme.SYSTEM_DEFAULT -> appThemeButton.text = getString(R.string.app_theme_system_default)
                SharedPrefsManager.Theme.LIGHT          -> appThemeButton.text = getString(R.string.app_theme_system_light)
                SharedPrefsManager.Theme.DARK           -> appThemeButton.text = getString(R.string.app_theme_system_dark)
            }

            if (DynamicColors.isDynamicColorAvailable()) {
                val materialYouViewsClickListener = View.OnClickListener {
                    sharedPreferences.isMaterialYouEnabled = (!sharedPreferences.isMaterialYouEnabled).apply { materialYouSwitch.isChecked = this }

                    recreate()
                }

                materialYouCard.isEnabled = true
                materialYouTextView.isEnabled = true
                materialYouSubTextTextView.visibility = View.GONE
                materialYouSwitch.isEnabled = true
                materialYouSwitch.isChecked = sharedPreferences.isMaterialYouEnabled

                materialYouCard.setOnClickListener(materialYouViewsClickListener)
                materialYouSwitch.setOnClickListener(materialYouViewsClickListener)
            }

            caffeineButton.setOnClickListener {
                if (!sharedPreferences.isAllPermissionsGranted) return@setOnClickListener

                KeepAwakeService.startNextDuration(caffeinateApplication)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAllPermissionsGranted()) onIsAllPermissionsGrantedChanged(true)

        caffeinateApplication.keepAwakeServiceObservers.addObserver(caffeinateApplication::keepAwakeServiceObservers.name, this)
        caffeinateApplication.sharedPrefsObservers.addObserver(caffeinateApplication::sharedPrefsObservers.name, this)

        onServiceStatusUpdate(caffeinateApplication.lastStatusUpdate)
    }

    override fun onPause() {
        super.onPause()
        caffeinateApplication.keepAwakeServiceObservers.remove(this)
    }

    override fun onIsAllPermissionsGrantedChanged(value: Boolean) {
        with(binding) {
            val allowDimmingViewsClickListener = View.OnClickListener {
                sharedPreferences.isDimmingEnabled = (!sharedPreferences.isDimmingEnabled).apply { allowDimmingSwitch.isChecked = this }
            }

            caffeineButton.isEnabled = value
            allowDimmingCard.isEnabled = value
            allowDimmingTextView.isEnabled = value
            allowDimmingSubTextTextView.visibility = if (value) View.VISIBLE else View.GONE
            allowDimmingSwitch.isEnabled = value
            allowDimmingSwitch.isChecked = sharedPreferences.isDimmingEnabled

            allowDimmingCard.setOnClickListener(allowDimmingViewsClickListener)
            allowDimmingSwitch.setOnClickListener(allowDimmingViewsClickListener)
        }
    }

    override fun onIsDimmingEnabledChanged(value: Boolean) {
        binding.allowDimmingSwitch.isChecked = value
    }

    override fun onServiceStatusUpdate(status: ServiceStatus) {
        binding.caffeineButton.text = when (status) {
            is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
            is ServiceStatus.Running -> status.remaining.toFormattedTime()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 93 && grantResults.isNotEmpty() && (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            val snackbar = Snackbar.make(binding.root, getString(R.string.notifications_permission_required), Snackbar.LENGTH_INDEFINITE)

            snackbar.setAction(getString(R.string.go_to_settings)) {
                try {
                    // Open the specific App Info page:
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                } catch (e: ActivityNotFoundException) {
                    // Open the generic Apps page:
                    val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    startActivity(intent)
                }
            }

            snackbar.show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("BatteryLife")
    private fun isAllPermissionsGranted(): Boolean {
        with(binding) {
            var isAllPermissionsGranted = sharedPreferences.isAllPermissionsGranted

            batteryOptimizationTextView.text = getString(R.string.battery_optimization_granted)
            batteryOptimizationImageView.setImageDrawable(grantedDrawable)
            batteryOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            backgroundOptimizationTextView.text = getString(R.string.background_optimization_granted)
            backgroundOptimizationImageView.setImageDrawable(grantedDrawable)
            backgroundOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            notificationPermissionTextView.text = getString(R.string.notifications_permission_granted)
            notificationPermissionImageView.setImageDrawable(grantedDrawable)
            notificationPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            if (isAllPermissionsGranted) return true
            val requiredPermissions = listOf(checkBatteryOptimization(), checkBackgroundOptimization(), checkNotificationPermission())

            isAllPermissionsGranted = requiredPermissions.all { it }

            sharedPreferences.isAllPermissionsGranted = isAllPermissionsGranted
            return isAllPermissionsGranted
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization(): Boolean {
        with(binding) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            return if (!powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)) {
                batteryOptimizationCard.setOnClickListener {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:${applicationContext.packageName}")
                    })
                    requestBatteryOptimizationPermission()
                }
                batteryOptimizationTextView.text = getString(R.string.battery_optimization_not_granted)
                batteryOptimizationImageView.setImageDrawable(notGrantedDrawable)
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))

                false
            } else {
                true
            }
        }
    }

    private fun checkBackgroundOptimization(): Boolean {
        with(binding) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (activityManager.isBackgroundRestricted) {
                    backgroundOptimizationCard.setOnClickListener {
                        requestBatteryOptimizationPermission()
                    }
                    backgroundOptimizationTextView.text = getString(R.string.background_optimization_not_granted)
                    backgroundOptimizationImageView.setImageDrawable(notGrantedDrawable)
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        }
    }

    private fun checkNotificationPermission(): Boolean {
        with(binding) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionCard.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 93) }
                    notificationPermissionTextView.text = getString(R.string.notifications_permission_not_granted)
                    notificationPermissionImageView.setImageDrawable(notGrantedDrawable)
                    notificationPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))

                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        }
    }

    private fun requestBatteryOptimizationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_battery_optimization_needed_title))
            .setIcon(R.drawable.coffee_icon)
            .setCancelable(false)
            .setMessage(getString(R.string.dialog_battery_optimization_needed_message))
            .setPositiveButton(getString(R.string.dialog_button_ok)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${applicationContext.packageName}")))
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun showChooseThemeDialog() {
        with(binding) {
            var theme = sharedPreferences.theme
            val dialogBinding = DialogChooseThemeBinding.inflate(LayoutInflater.from(root.context))

            when (theme.value) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> {
                    dialogBinding.themeRadioGroup.check(R.id.themeSystemDefault)
                    appThemeButton.text = getString(R.string.app_theme_system_default)
                }

                AppCompatDelegate.MODE_NIGHT_NO            -> {
                    dialogBinding.themeRadioGroup.check(R.id.themeSystemLight)
                    appThemeButton.text = getString(R.string.app_theme_system_light)
                }

                AppCompatDelegate.MODE_NIGHT_YES           -> {
                    dialogBinding.themeRadioGroup.check(R.id.themeSystemDark)
                    appThemeButton.text = getString(R.string.app_theme_system_dark)
                }
            }

            dialogBinding.themeRadioGroup.setOnCheckedChangeListener { _, checkedRadioButtonId ->
                when (checkedRadioButtonId) {
                    R.id.themeSystemDefault -> theme = SharedPrefsManager.Theme.SYSTEM_DEFAULT
                    R.id.themeSystemLight   -> theme = SharedPrefsManager.Theme.LIGHT
                    R.id.themeSystemDark    -> theme = SharedPrefsManager.Theme.DARK
                }
            }

            MaterialAlertDialogBuilder(root.context)
                .setView(dialogBinding.root)
                .setIcon(R.drawable.baseline_coffee_32)
                .setPositiveButton(getString(R.string.dialog_button_ok)) { _, _ ->
                    sharedPreferences.theme = theme
                    AppCompatDelegate.setDefaultNightMode(theme.value)
                    recreate()
                }
                .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                .show()
        }
    }
}