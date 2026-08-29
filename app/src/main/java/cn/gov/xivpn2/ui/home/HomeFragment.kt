package cn.gov.xivpn2.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.gov.xivpn2.R
import cn.gov.xivpn2.database.Rules
import cn.gov.xivpn2.service.XiVPNService
import cn.gov.xivpn2.ui.GeoAssetsActivity
import cn.gov.xivpn2.ui.ui.theme.XiVPNTheme
import cn.gov.xivpn2.xrayconfig.LabelSubscription
import okio.IOException
import java.io.File

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }

    private var binder: XiVPNService.XiVPNBinder? = null

    private val vpnStatusListener = object : XiVPNService.VPNStateListener {
        override fun onStateChanged(state: XiVPNService.VPNState) {
            Log.i(TAG, "onStatusChanged $state")
            viewModel.updateVpnState(state)
        }

        override fun onMessage(msg: String) {
            viewModel.updateMessage(msg)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service as XiVPNService.XiVPNBinder
            viewModel.updateVpnState(binder!!.state)
            binder!!.addListener(vpnStatusListener)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
        }
    }

    override fun onStart() {
        super.onStart()
        // bind and start vpn service
        requireContext().bindService(Intent(requireContext(), XiVPNService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        binder?.removeListener(vpnStatusListener)
        requireContext().unbindService(connection)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this))
        composeView.setContent {
            XiVPNTheme {
                HomeScreen()
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        val vpnState = viewModel.vpnState
        val message = viewModel.message
        val groups = viewModel.groups
        val activeTab = viewModel.activeTab

        val connected = vpnState != XiVPNService.VPNState.DISCONNECTED

        val enabled = vpnState == XiVPNService.VPNState.CONNECTED || vpnState == XiVPNService.VPNState.DISCONNECTED
        val checked = when (vpnState) {
            XiVPNService.VPNState.CONNECTED -> true
            XiVPNService.VPNState.ESTABLISHING_VPN, XiVPNService.VPNState.STARTING_LIBXI -> true
            else -> false
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {

            if (connected && groups.isNotEmpty()) {

                // switch

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                        BigSwitch(checked = checked, enabled = enabled, onCheckedChange = { onSwitchCheckedChange(it) })

                    if (message.isNotEmpty()) {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // tabs

                val tabs = groups.keys.toList()

                PrimaryScrollableTabRow(
                    tabs.indexOfFirst { it == activeTab }.coerceAtLeast(0),
                    edgePadding = 0.dp,
                ) {
                    for (tab in tabs) {
                        Tab(
                            selected = tab == activeTab,
                            onClick = { viewModel.updateActiveTab(tab) },
                            text = { Text(tab.label) }
                        )
                    }
                }

                // servers

                val selected = activeTab?.let { groups[it] }

                LazyVerticalGrid(
                    modifier = Modifier.padding(6.dp),
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(count = selected?.first?.size ?: 0) { i ->
                        val proxy = selected!!.first[i]
                        val highlighted = selected.second == proxy

                        Card(
                            colors = CardDefaults.cardColors().copy(
                                containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.onServerSelected(activeTab!!, proxy)
                            }
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(proxy.label, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                Text(proxy.subscription ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

            } else {

                // switch only, centered

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BigSwitch(checked = checked, enabled = enabled, onCheckedChange = { onSwitchCheckedChange(it) })

                        if (message.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(message, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BigSwitch(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Box(
            modifier = Modifier.size(width = 104.dp, height = 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(2f)
            )
        }
    }

    override fun onResume() {
        super.onResume()

        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.app_name)

        viewModel.refreshGroups()
    }

    private fun onSwitchCheckedChange(isChecked: Boolean) {

        // on switch checked change

        viewModel.updateMessage("")

        if (isChecked) {
            // start vpn

            // request vpn permission
            val intent = XiVPNService.prepare(requireContext())
            if (intent != null) {
                startActivityForResult(intent, 200)
                return
            }

            // check whether geoip / geosite database is downloaded
            try {
                var geoip = false
                var geosite = false
                for (routingRule in Rules.readRules(requireContext().filesDir)) {
                    for (s in routingRule.ip) {
                        if (s.startsWith("geoip:")) {
                            geoip = true
                        }
                        if (s.startsWith("geosite:")) {
                            geosite = true
                        }
                    }
                    for (s in routingRule.domain) {
                        if (s.startsWith("geoip:")) {
                            geoip = true
                        }
                        if (s.startsWith("geosite:")) {
                            geosite = true
                        }
                    }
                }
                if ((geoip && !File(requireContext().filesDir, "geoip.dat").isFile) || (geosite && !File(requireContext().filesDir, "geosite.dat").isFile)) {
                    // ask the user to download geoip / geosite database
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.warning)
                        .setMessage(R.string.geoip_not_downloaded)
                        .setPositiveButton(R.string.download) { _, _ ->
                            startActivity(Intent(requireContext(), GeoAssetsActivity::class.java))
                        }
                        .show()
                    return
                }
            } catch (e: IOException) {
                Log.e(TAG, "read rules", e)
            }

            // start service
            val intent2 = Intent(requireContext(), XiVPNService::class.java)
            intent2.action = "cn.gov.xivpn2.START"
            intent2.putExtra("always-on", false)
            requireContext().startForegroundService(intent2)

        } else {
            // stop
            val intent2 = Intent(requireContext(), XiVPNService::class.java)
            intent2.action = "cn.gov.xivpn2.STOP"
            intent2.putExtra("always-on", false)
            requireContext().startService(intent2)
        }
    }
}
