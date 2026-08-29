package cn.gov.xivpn2.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cn.gov.xivpn2.R
import cn.gov.xivpn2.database.AppDatabase
import cn.gov.xivpn2.database.Proxy
import cn.gov.xivpn2.service.SubscriptionWork
import cn.gov.xivpn2.service.sharelink.MarshalProxyException
import cn.gov.xivpn2.ui.proxy.HttpActivity
import cn.gov.xivpn2.ui.proxy.HysteriaActivity
import cn.gov.xivpn2.ui.proxy.ProxyChainActivity
import cn.gov.xivpn2.ui.proxy.ProxyGroupActivity
import cn.gov.xivpn2.ui.proxy.ShadowsocksActivity
import cn.gov.xivpn2.ui.proxy.Socks5Activity
import cn.gov.xivpn2.ui.proxy.TrojanActivity
import cn.gov.xivpn2.ui.proxy.VlessActivity
import cn.gov.xivpn2.ui.proxy.VmessActivity
import cn.gov.xivpn2.ui.proxy.WireguardActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap
import cn.gov.xivpn2.database.Rules
import cn.gov.xivpn2.service.XiVPNService
import cn.gov.xivpn2.ui.QRScanActivity
import cn.gov.xivpn2.ui.ui.theme.XiVPNTheme
import com.google.android.material.textfield.TextInputEditText
import okio.IOException
import java.util.Objects


class ProxiesFragment2 : Fragment() {

    private val viewModel: ProxiesViewModel by lazy {
        ViewModelProvider(this)[ProxiesViewModel::class.java]
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_proxies2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val sp = requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE)
        refresh()

        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this))
        composeView.setContent {
            XiVPNTheme {

                var selectedIndex by remember { mutableIntStateOf(0) }

                val subscriptions = viewModel.allProxies
                    .map { p -> p.subscription ?: "none" }
                    .filter { p -> "none" != p }
                    .toSet()
                    .toList()

                LaunchedEffect(subscriptions.size) {
                    if (selectedIndex > subscriptions.size + 1) {
                        selectedIndex = 0
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {

                    // tabs

                    PrimaryScrollableTabRow (
                        selectedIndex,
                        edgePadding = 0.dp,
                    ) {
                        Tab(selected = selectedIndex == 0, onClick = { selectedIndex = 0 }, text = { Text(getString(R.string.all)) })
                        Tab(selected = selectedIndex == 1, onClick = { selectedIndex = 1 }, text = { Text(getString(R.string.default_)) })

                        for ((idx, s) in subscriptions.withIndex()) {
                            Tab(selected = selectedIndex == idx, onClick = { selectedIndex = idx + 2 }, text = { Text(s) })
                        }
                    }

                    // proxies

                    var selectedSub = ""
                    if (selectedIndex == 1) {
                        selectedSub = "none"
                    } else if (selectedIndex >= 2) {
                        selectedSub = subscriptions[selectedIndex - 2]
                    }

                    val filteredProxies = viewModel.allProxies.filter { p -> selectedSub == "" || selectedSub == p.subscription }.filter { p -> p.protocol != "dns" }.toMutableList()

                    LazyVerticalGrid(
                        modifier = Modifier.padding(6.dp),
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {

                        items(count = filteredProxies.size) { i ->
                            val p = filteredProxies[i]
                            val highlighted = p.subscription == viewModel.currentSub && p.label == viewModel.currentLabel
                            var menuExpanded by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // card

                                Card(
                                    colors = CardDefaults.cardColors().copy(
                                        containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                Rules.setCatchAll(sp, p.label, p.subscription)
                                                XiVPNService.markConfigStale(requireContext())
                                                viewModel.setSelection(p.label, p.subscription)
                                            },
                                            onLongClick = { menuExpanded = true }
                                        )
                                        .fillMaxSize()
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        if (p.protocol == "freedom" && p.subscription == "none") {
                                            Text("Direct", maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                        } else {
                                            Text(p.label, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                        }

                                        Text(p.protocol.lowercase(), style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // dropdown menu

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {

                                    // edit

                                    DropdownMenuItem(
                                        text = { Text(getString(R.string.edit)) },
                                        onClick = { menuExpanded = false; onEditProxy(p) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(getString(R.string.share)) },
                                        onClick = { menuExpanded = false; onShareProxy(p) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(getString(R.string.delete)) },
                                        onClick = { menuExpanded = false; onDeleteProxy(p) }
                                    )
                                }

                            }
                        }

                    }

                }


            }
        }

    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        viewModel.setProxies(AppDatabase.getInstance().proxyDao().findAll())
        val sp = requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE)
        viewModel.setSelection(
            sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)") ?: "No Proxy (Bypass Mode)",
            sp.getString("SELECTED_SUBSCRIPTION", "none") ?: "none",
        )
    }

    private fun onDeleteProxy(p: Proxy) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.warning)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.ok) { dialog, which ->

                AppDatabase.getInstance().proxyDao().delete(p.label, p.subscription);

                try {
                    Rules.resetDeletedProxies(requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE), requireContext().getFilesDir());
                } catch (e: IOException) {
                    Log.e("ProxiesFragment", "reset deleted proxies", e);
                }

                XiVPNService.markConfigStale(requireContext());

                refresh();
            }
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private fun onEditProxy(p: Proxy) {
        var cls: Class<out AppCompatActivity?>? = null
        when (p.protocol) {
            "shadowsocks" -> cls = ShadowsocksActivity::class.java
            "vmess" -> cls = VmessActivity::class.java
            "vless" -> cls = VlessActivity::class.java
            "trojan" -> cls = TrojanActivity::class.java
            "wireguard" -> cls = WireguardActivity::class.java
            "proxy-chain" -> cls = ProxyChainActivity::class.java
            "proxy-group" -> cls = ProxyGroupActivity::class.java
            "http" -> cls = HttpActivity::class.java
            "socks" -> cls = Socks5Activity::class.java
            "hysteria" -> cls = HysteriaActivity::class.java
        }

        if (cls != null) {
            val intent = Intent(requireContext(), cls)
            intent.putExtra("LABEL", p.label)
            intent.putExtra("SUBSCRIPTION", p.subscription)
            intent.putExtra("CONFIG", p.config)
            startActivity(intent)
        }
    }

    private fun onShareProxy(p: Proxy) {
        val link: String
        try {
            link = SubscriptionWork.marshalProxy(p)
        } catch (e: MarshalProxyException) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            return
        }

        var bmp: Bitmap? = null
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(link, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0..<width) {
                for (y in 0..<height) {
                    bmp[x, y] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
        } catch (e: WriterException) {
            Log.e("ProxiesFragment", "could not generate qr code", e)
            return
        }

        val imageView = ImageView(requireContext())
        imageView.setImageBitmap(bmp)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.share)
            .setView(imageView)
            .setPositiveButton(R.string.copy_share_link) { dialog, which ->
                val clipboardManager: ClipboardManager =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", link))
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.proxies_activity, menu);
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.from_clipboard) {

            // import from clipboard

            val view = LayoutInflater.from(requireContext()).inflate(R.layout.edit_text, null);
            val editText2: TextInputEditText = view.findViewById(R.id.edit_text);

            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.import_form_clipboard)
                    .setView(view)
                    .setPositiveButton(R.string.ok) { dialog, which ->

                        val s = Objects.requireNonNull(editText2.getText()).toString()
                        if (s.isEmpty()) {
                            return@setPositiveButton;
                        }

                        try {
                            SubscriptionWork.parseLine(s, "none");
                            Toast.makeText(
                                requireContext(),
                                R.string.proxy_added,
                                Toast.LENGTH_SHORT
                            ).show();
                            XiVPNService.markConfigStale(requireContext());
                            refresh();
                        } catch (e: Exception) {
                            Log.e("ProxiesFragment", "parse line", e);

                            AlertDialog.Builder (requireContext())
                                .setTitle(R.string.invalid_link)
                                .setMessage(e.message)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                        }

                    }.show();

            view.requestFocus();

            return true;
        } else if (id == R.id.shadowsocks || id == R.id.vmess || id == R.id.socks5 || id == R.id.vless || id == R.id.trojan || id == R.id.wireguard || id == R.id.proxy_chain || id == R.id.proxy_group || id == R.id.http || id == R.id.hysteria) {

            // add

            val view = LayoutInflater.from(requireContext()).inflate(R.layout.label_edit_text, null);
            val editText: TextInputEditText = view.findViewById(R.id.edit_text);

            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.label)
                    .setView(view)
                    .setPositiveButton(R.string.ok, { dialog, which ->

                        val label = editText.text.toString();
                        if (label.isEmpty() || AppDatabase.getInstance().proxyDao().exists(label, "none") > 0) {
                            Toast.makeText(requireContext(), getResources().getText(R.string.conflict_label), Toast.LENGTH_SHORT).show();
                            return@setPositiveButton;
                        }

                        var cls: Class<*>? = null;
                        if (id == R.id.shadowsocks) {
                            cls = ShadowsocksActivity::class.java;
                        } else if (id == R.id.vmess) {
                            cls = VmessActivity::class.java;
                        } else if (id == R.id.vless) {
                            cls = VlessActivity::class.java;
                        } else if (id == R.id.trojan) {
                            cls = TrojanActivity::class.java;
                        } else if (id == R.id.wireguard) {
                            cls = WireguardActivity::class.java;
                        } else if (id == R.id.proxy_chain) {
                            cls = ProxyChainActivity::class.java;
                        } else if (id == R.id.proxy_group) {
                            cls = ProxyGroupActivity::class.java;
                        } else if (id == R.id.http) {
                            cls = HttpActivity::class.java;
                        } else if (id == R.id.socks5) {
                            cls = Socks5Activity::class.java;
                        } else if (id == R.id.hysteria) {
                            cls = HysteriaActivity::class.java;
                        }

                        val intent = Intent(requireContext(), cls)
                        intent.putExtra("LABEL", label);
                        intent.putExtra("SUBSCRIPTION", "none");
                        startActivity(intent);

                    }).show();

            return true;
        } else if (id == R.id.help) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.help)
                    .setMessage(R.string.proxies_help)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;

        } else if (id == R.id.qrcode) {
            startActivity(Intent(requireContext(), QRScanActivity::class.java));
            return true;
        }
        return false
    }

}