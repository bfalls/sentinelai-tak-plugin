package com.sentinelai.tak.plugin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sentinelai.tak.plugin.config.SentinelConfig
import com.sentinelai.tak.plugin.config.SentinelConfigRepository
import com.sentinelai.tak.plugin.databinding.ActivitySentinelSettingsBinding

class SentinelSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySentinelSettingsBinding
    private lateinit var repository: SentinelConfigRepository

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
        val backendUrl = binding.backendUrlInput.text?.toString()?.trim().orEmpty()
        val apiKey = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        val timeoutInput = binding.timeoutInput.text?.toString()?.trim().orEmpty()
        val debugEnabled = binding.debugSwitch.isChecked

        val timeout = timeoutInput.toIntOrNull()
        if (timeout == null || timeout <= 0) {
            Toast.makeText(this, R.string.sentinel_timeout_validation_error, Toast.LENGTH_SHORT).show()
            return
        }

        val config = SentinelConfig(
            backendUrl = backendUrl,
            apiKey = apiKey,
            requestTimeoutSeconds = timeout,
            debugLoggingEnabled = debugEnabled,
        )

        repository.save(config)
        Toast.makeText(this, R.string.sentinel_settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
