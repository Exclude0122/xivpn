package cn.gov.xivpn2.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.gov.xivpn2.database.AppDatabase
import cn.gov.xivpn2.database.Proxy
import cn.gov.xivpn2.database.Rules
import cn.gov.xivpn2.service.XiVPNService
import cn.gov.xivpn2.xrayconfig.LabelSubscription
import cn.gov.xivpn2.xrayconfig.Outbound
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings
import cn.gov.xivpn2.xrayconfig.ProxyGroupSettings
import cn.gov.xivpn2.xrayconfig.RoutingRule
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okio.IOException

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HomeViewModel"

    var vpnState by mutableStateOf(XiVPNService.VPNState.DISCONNECTED)
        private set

    var message by mutableStateOf("")
        private set

    /**
     * proxy group -> (servers in proxy group, selected server)
     */
    var groups by mutableStateOf<Map<LabelSubscription, Pair<List<LabelSubscription>, LabelSubscription>>>(emptyMap())
        private set

    // currently selected proxy group
    var activeTab by mutableStateOf<LabelSubscription?>(null)
        private set

    fun updateVpnState(state: XiVPNService.VPNState) {
        vpnState = state
    }

    fun updateMessage(msg: String) {
        message = msg
    }

    fun updateActiveTab(tab: LabelSubscription?) {
        activeTab = tab
    }

    fun refreshGroups() {

        // update proxy groups

        val map = HashMap<LabelSubscription, Pair<List<LabelSubscription>, LabelSubscription>>()
        for (proxy in findUsedProxyGroups()) {
            val key = LabelSubscription(proxy.label, proxy.subscription)

            val gson = Gson()
            val proxyGroupSettings: Outbound<ProxyGroupSettings> =
                gson.fromJson(proxy.config, object : TypeToken<Outbound<ProxyGroupSettings>>() {}.type)

            if (proxyGroupSettings.settings.selected == null) {
                // default to the first one
                // same behavior as XiVPNService
                proxyGroupSettings.settings.selected = proxyGroupSettings.settings.proxies[0]
            }

            map[key] = Pair(proxyGroupSettings.settings.proxies, proxyGroupSettings.settings.selected)
        }

        groups = map
        activeTab = map.keys.firstOrNull()
    }

    fun onServerSelected(group: LabelSubscription, selected: LabelSubscription) {

        // on proxy group selection change

        val proxyGroup = AppDatabase.getInstance().proxyDao().find(group.label, group.subscription)

        val gson = GsonBuilder().setPrettyPrinting().create()
        val proxyGroupSettings: Outbound<ProxyGroupSettings> =
            gson.fromJson(proxyGroup.config, object : TypeToken<Outbound<ProxyGroupSettings>>() {}.type)

        proxyGroupSettings.settings.selected = selected

        val json = gson.toJson(proxyGroupSettings)

        AppDatabase.getInstance().proxyDao().updateConfig(group.label, group.subscription, json)

        XiVPNService.markConfigStale(getApplication())

        // update in-memory state

        val current = groups[group] ?: return
        groups = groups.toMutableMap().apply {
            put(group, Pair(current.first, selected))
        }
    }

    private fun findUsedProxyGroups(): ArrayList<Proxy> {
        val proxies = ArrayList<Proxy>()
        val visited = HashSet<LabelSubscription>()

        // catch all
        val sp: SharedPreferences = getApplication<Application>().getSharedPreferences("XIVPN", Context.MODE_PRIVATE)
        val selectedLabel = sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)")
        val selectedSubscription = sp.getString("SELECTED_SUBSCRIPTION", "none")
        recurseUsedProxyGroups(LabelSubscription(selectedLabel, selectedSubscription), proxies, visited)

        // routing
        try {
            val rules = Rules.readRules(getApplication<Application>().filesDir)

            for (rule in rules) {
                recurseUsedProxyGroups(LabelSubscription(rule.outboundLabel, rule.outboundSubscription), proxies, visited)
            }
        } catch (e: IOException) {
            Log.wtf(TAG, "build xray config", e)
        }

        return proxies
    }

    /**
     * Recursively find proxy groups used by newProxy.
     * @param proxies proxy groups
     */
    private fun recurseUsedProxyGroups(labelSub: LabelSubscription, proxies: ArrayList<Proxy>, visited: HashSet<LabelSubscription>) {
        if (visited.contains(labelSub)) {
            return
        }
        visited.add(labelSub)

        val newProxy = AppDatabase.getInstance().proxyDao().find(labelSub.label, labelSub.subscription) ?: return

        if (newProxy.protocol == "proxy-group") {
            // add the new proxy group to proxies
            proxies.add(newProxy)

            // recursively find its dependencies
            val gson = Gson()
            val proxyGroupSettings: Outbound<ProxyGroupSettings> =
                gson.fromJson(newProxy.config, object : TypeToken<Outbound<ProxyGroupSettings>>() {}.type)

            for (newLabelSub in proxyGroupSettings.settings.proxies) {
                recurseUsedProxyGroups(newLabelSub, proxies, visited)
            }
        } else if (newProxy.protocol == "proxy-chain") {
            // recursively find its dependencies
            val gson = Gson()
            val proxyChainSettings: Outbound<ProxyChainSettings> =
                gson.fromJson(newProxy.config, object : TypeToken<Outbound<ProxyChainSettings>>() {}.type)

            for (newLabelSub in proxyChainSettings.settings.proxies) {
                recurseUsedProxyGroups(newLabelSub, proxies, visited)
            }
        }
    }
}
