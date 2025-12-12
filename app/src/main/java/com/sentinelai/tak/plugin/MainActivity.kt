package com.sentinelai.tak.plugin

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.sentinelai.tak.plugin.databinding.ActivityMainBinding
import com.sentinelai.tak.plugin.mission.MissionAnalysisFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(binding.fragmentContainer.id, MissionAnalysisFragment.newInstance())
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.sentinel_ai_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sentinel_ai -> {
                // Fragment is already visible; no-op for now.
                true
            }

            R.id.menu_sentinel_settings -> {
                startActivity(Intent(this, SentinelSettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
