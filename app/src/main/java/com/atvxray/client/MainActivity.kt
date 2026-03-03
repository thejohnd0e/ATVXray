package com.atvxray.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var configRepo: ConfigRepository

    private lateinit var statusText: TextView
    private lateinit var errorText: TextView
    private lateinit var connectSwitch: Switch

    private var internalSwitchChange = false

    private val openTxtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importTxt(uri)
        }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService(AppConstants.ACTION_CONNECT)
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
                setSwitchChecked(false)
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_STATE) {
                val connected = intent.getBooleanExtra(AppConstants.EXTRA_CONNECTED, false)
                val error = intent.getStringExtra(AppConstants.EXTRA_ERROR)
                setSwitchChecked(connected)
                updateError(error)
                updateStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configRepo = ConfigRepository(applicationContext)

        statusText = findViewById(R.id.statusText)
        errorText = findViewById(R.id.errorText)
        connectSwitch = findViewById(R.id.connectSwitch)
        val importButton: Button = findViewById(R.id.importButton)

        importButton.setOnClickListener {
            openTxtLauncher.launch(arrayOf("text/plain", "text/*"))
        }

        connectSwitch.setOnCheckedChangeListener { _, checked ->
            if (internalSwitchChange) return@setOnCheckedChangeListener
            if (checked) {
                connectFlow()
            } else {
                startVpnService(AppConstants.ACTION_DISCONNECT)
            }
        }

        updateStatus()
        updateError(null)
        setSwitchChecked(VpnTunnelService.isConnected)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(stateReceiver, IntentFilter(AppConstants.ACTION_STATE))
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        setSwitchChecked(VpnTunnelService.isConnected)
    }

    private fun importTxt(uri: Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

        val link = extractFirstVless(text)
        if (link == null) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
            updateStatus()
            return
        }

        configRepo.saveVlessLink(link)
        Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun extractFirstVless(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("vless://", ignoreCase = true) }
    }

    private fun connectFlow() {
        if (!configRepo.hasConfig()) {
            Toast.makeText(this, R.string.need_config, Toast.LENGTH_SHORT).show()
            setSwitchChecked(false)
            updateStatus()
            return
        }

        val prep = VpnService.prepare(this)
        if (prep != null) {
            vpnPermissionLauncher.launch(prep)
        } else {
            startVpnService(AppConstants.ACTION_CONNECT)
        }
    }

    private fun startVpnService(action: String) {
        val intent = Intent(this, VpnTunnelService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateStatus() {
        val text = when {
            VpnTunnelService.isConnected -> getString(R.string.status_connected)
            !configRepo.hasConfig() -> getString(R.string.status_no_config)
            else -> getString(R.string.status_disconnected)
        }
        statusText.text = text
        statusText.setTextColor(
            when (text) {
                getString(R.string.status_connected) -> 0xFF4ADE80.toInt()
                getString(R.string.status_no_config) -> 0xFFFFC857.toInt()
                else -> 0xFFFF5A5A.toInt()
            }
        )
    }

    private fun updateError(error: String?) {
        errorText.text = if (error.isNullOrBlank()) "" else "Error: $error"
    }

    private fun setSwitchChecked(checked: Boolean) {
        internalSwitchChange = true
        connectSwitch.isChecked = checked
        internalSwitchChange = false
    }
}
