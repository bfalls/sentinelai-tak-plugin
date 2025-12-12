package com.sentinelai.tak.plugin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sentinelai.tak.plugin.config.SentinelConfig
import com.sentinelai.tak.plugin.config.SentinelConfigRepository
import com.sentinelai.tak.plugin.databinding.ActivitySentinelSettingsBinding
import com.sentinelai.tak.plugin.network.SentinelApiClient
import com.sentinelai.tak.plugin.network.SentinelApiErrorKind
import com.sentinelai.tak.plugin.network.SentinelApiException
import kotlinx.coroutines.launch

class SentinelSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySentinelSettingsBinding
    private lateinit var repository: SentinelConfigRepository
    private val apiClient: SentinelApiClient by lazy { SentinelApiClient(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySentinelSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.sentinel_settings_title)

        repository = SentinelConfigRepository(this)
        populateFields(repository.load())

        binding.saveButton.setOnClickListener { saveSettings() }
        binding.cancelButton.setOnClickListener { finish() }
        binding.testConnectionButton.setOnClickListener { testConnection() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun populateFields(config: SentinelConfig) {
        binding.backendUrlInput.setText(config.backendUrl)
        binding.apiKeyInput.setText(config.apiKey)
        binding.timeoutInput.setText(config.requestTimeoutSeconds.toString())
        binding.debugSwitch.isChecked = config.debugLoggingEnabled
    }

    private fun saveSettings() {
        val config = buildConfigFromInputs() ?: return

        repository.save(config)
        Toast.makeText(this, R.string.sentinel_settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun buildConfigFromInputs(): SentinelConfig? {
        val backendUrl = binding.backendUrlInput.text?.toString()?.trim().orEmpty()
        val apiKey = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        val timeoutInput = binding.timeoutInput.text?.toString()?.trim().orEmpty()
        val debugEnabled = binding.debugSwitch.isChecked

        val timeout = timeoutInput.toIntOrNull()
        if (timeout == null || timeout <= 0) {
            Toast.makeText(this, R.string.sentinel_timeout_validation_error, Toast.LENGTH_SHORT).show()
            return null
        }

        return SentinelConfig(
            backendUrl = backendUrl,
            apiKey = apiKey,
            requestTimeoutSeconds = timeout,
            debugLoggingEnabled = debugEnabled,
        )
    }

    private fun testConnection() {
        val config = buildConfigFromInputs()
        if (config == null) {
            binding.testConnectionProgress.isVisible = false
            binding.testConnectionButton.isEnabled = true
            binding.testConnectionStatus.text = getString(
                R.string.sentinel_test_connection_failure,
                getString(R.string.sentinel_timeout_validation_error),
            )
            return
        }

        binding.testConnectionProgress.isVisible = true
        binding.testConnectionButton.isEnabled = false
        binding.testConnectionStatus.text = getString(R.string.sentinel_testing_connection)
        binding.testConnectionStatus.setTextColor(
            ContextCompat.getColor(this, android.R.color.darker_gray),
        )

        lifecycleScope.launch {
            try {
                apiClient.healthCheck(config)
                binding.testConnectionStatus.text = getString(R.string.sentinel_test_connection_success)
                binding.testConnectionStatus.setTextColor(
                    ContextCompat.getColor(this@SentinelSettingsActivity, android.R.color.holo_green_dark),
                )
            } catch (ex: Exception) {
                val message = readableHealthError(ex)
                binding.testConnectionStatus.text = getString(
                    R.string.sentinel_test_connection_failure,
                    message,
                )
                binding.testConnectionStatus.setTextColor(
                    ContextCompat.getColor(this@SentinelSettingsActivity, android.R.color.holo_red_dark),
                )
            } finally {
                binding.testConnectionProgress.isVisible = false
                binding.testConnectionButton.isEnabled = true
            }
        }
    }

    private fun readableHealthError(ex: Exception): String {
        if (ex !is SentinelApiException) {
            return ex.localizedMessage ?: getString(R.string.mission_analysis_status_failed)
        }

        return when (ex.kind) {
            SentinelApiErrorKind.CONFIG -> getString(R.string.mission_analysis_error_config)
            SentinelApiErrorKind.NETWORK -> getString(R.string.mission_analysis_error_network)
            SentinelApiErrorKind.TIMEOUT -> getString(R.string.mission_analysis_error_timeout)
            SentinelApiErrorKind.BACKEND, SentinelApiErrorKind.PARSE ->
                getString(R.string.mission_analysis_error_backend)
            SentinelApiErrorKind.UNKNOWN -> ex.message ?: getString(R.string.mission_analysis_status_failed)
        }
    }
}
