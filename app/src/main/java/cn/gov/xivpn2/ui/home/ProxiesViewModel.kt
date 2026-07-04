package cn.gov.xivpn2.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cn.gov.xivpn2.database.Proxy

class ProxiesViewModel : ViewModel() {

    var allProxies by mutableStateOf<List<Proxy>>(emptyList())
        private set

    var currentLabel by mutableStateOf("No Proxy (Bypass Mode)")
        private set

    var currentSub by mutableStateOf("none")
        private set

    fun setProxies(list: List<Proxy>) {
        allProxies = list
    }

    fun setSelection(label: String, sub: String) {
        currentLabel = label
        currentSub = sub
    }
}
