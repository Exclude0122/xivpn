package cn.gov.xivpn2.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.service.sharelink.MarshalProxyException;

public class ProxiesActivity extends AppCompatActivity {


    private ProxiesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_proxies);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.proxies);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        this.adapter = new ProxiesAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // on list item clicked
        adapter.setOnLongClickListener((view, proxy, i) -> {
            if (proxy.protocol.equals("freedom") || proxy.protocol.equals("blackhole") || proxy.protocol.equals("dns")) {
                // freedom / blackhole / dns is not removable and editable
                return;
            }

            PopupMenu popupMenu = new PopupMenu(this, view);
            popupMenu.inflate(R.menu.proxies_popup);
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.delete) {

                    // delete
                    AppDatabase.getInstance().proxyDao().delete(proxy.label, proxy.subscription);

                    try {
                        Rules.resetDeletedProxies(getSharedPreferences("XIVPN", MODE_PRIVATE), getApplicationContext().getFilesDir());
                    } catch (IOException e) {
                        Log.e("ProxiesActivity", "reset deleted proxies", e);
                    }

                    XiVPNService.markConfigStale(this);

                    refresh();

                } else if (item.getItemId() == R.id.edit) {
                    // edit
                    Class<? extends AppCompatActivity> cls = null;
                    switch (proxy.protocol) {
                        case "shadowsocks":
                            cls = ShadowsocksActivity.class;
                            break;
                        case "vmess":
                            cls = VmessActivity.class;
                            break;
                        case "vless":
                            cls = VlessActivity.class;
                            break;
                        case "trojan":
                            cls = TrojanActivity.class;
                            break;
                        case "wireguard":
                            cls = WireguardActivity.class;
                            break;
                        case "proxy-chain":
                            cls = ProxyChainActivity.class;
                            break;
                        case "proxy-group":
                            cls = ProxyGroupActivity.class;
                            break;
                        case "http":
                            cls = HttpActivity.class;
                            break;
                        case "socks":
                            cls = Socks5Activity.class;
                            break;
                        case "hysteria":
                            cls = HysteriaActivity.class;
                            break;
                    }

                    if (cls != null) {
                        Intent intent = new Intent(this, cls);
                        intent.putExtra("LABEL", proxy.label);
                        intent.putExtra("SUBSCRIPTION", proxy.subscription);
                        intent.putExtra("CONFIG", proxy.config);
                        startActivity(intent);
                    }

                } else if (item.getItemId() == R.id.copy_share_link) {
                    String link;
                    try {
                        link = SubscriptionWork.marshalProxy(proxy);
                    } catch (MarshalProxyException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    ClipboardManager clipboardManager = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", link));
                }

                return true;
            });
            popupMenu.show();
        });

        adapter.setOnClickListener((view, proxy, i) -> {
            if (proxy.protocol.equals("dns")) return; // dns can not be the default outbound

            SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
            Rules.setCatchAll(sp, proxy.label, proxy.subscription);
            adapter.setChecked(proxy.label, proxy.subscription);

            XiVPNService.markConfigStale(this);
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.clear();
        adapter.addProxies(AppDatabase.getInstance().proxyDao().findAll());
        SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
        adapter.setChecked(
                sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)"),
                sp.getString("SELECTED_SUBSCRIPTION", "none")
        );


    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        } else if (item.getItemId() == R.id.from_clipboard) {

            View view = LayoutInflater.from(this).inflate(R.layout.edit_text, null);
            TextInputEditText editText2 = view.findViewById(R.id.edit_text);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.import_form_clipboard)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {

                        String s = editText2.getText().toString();
                        if (s.isEmpty()) {
                            return;
                        }

                        if (!SubscriptionWork.parseLine(s, "none")) {
                            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.proxy_added, Toast.LENGTH_SHORT).show();

                            XiVPNService.markConfigStale(this);
                            refresh();
                        }

                    }).show();

            view.requestFocus();

            return true;
        } else if (item.getItemId() == R.id.shadowsocks || item.getItemId() == R.id.vmess || item.getItemId() == R.id.socks5 || item.getItemId() == R.id.vless || item.getItemId() == R.id.trojan || item.getItemId() == R.id.wireguard || item.getItemId() == R.id.proxy_chain || item.getItemId() == R.id.proxy_group || item.getItemId() == R.id.http || item.getItemId() == R.id.hysteria) {

            // add

            View view = LayoutInflater.from(this).inflate(R.layout.label_edit_text, null);
            TextInputEditText editText = view.findViewById(R.id.edit_text);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.label)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {

                        String label = String.valueOf(editText.getText());
                        if (label.isEmpty() || AppDatabase.getInstance().proxyDao().exists(label, "none") > 0) {
                            Toast.makeText(this, getResources().getText(R.string.conflict_label), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Class<? extends AppCompatActivity> cls = null;
                        if (item.getItemId() == R.id.shadowsocks) {
                            cls = ShadowsocksActivity.class;
                        } else if (item.getItemId() == R.id.vmess) {
                            cls = VmessActivity.class;
                        } else if (item.getItemId() == R.id.vless) {
                            cls = VlessActivity.class;
                        } else if (item.getItemId() == R.id.trojan) {
                            cls = TrojanActivity.class;
                        } else if (item.getItemId() == R.id.wireguard) {
                            cls = WireguardActivity.class;
                        } else if (item.getItemId() == R.id.proxy_chain) {
                            cls = ProxyChainActivity.class;
                        } else if (item.getItemId() == R.id.proxy_group) {
                            cls = ProxyGroupActivity.class;
                        } else if (item.getItemId() == R.id.http) {
                            cls = HttpActivity.class;
                        } else if (item.getItemId() == R.id.socks5) {
                            cls = Socks5Activity.class;
                        } else if (item.getItemId() == R.id.hysteria) {
                            cls = HysteriaActivity.class;
                        }

                        Intent intent = new Intent(this, cls);
                        intent.putExtra("LABEL", label);
                        intent.putExtra("SUBSCRIPTION", "none");
                        startActivity(intent);

                    }).show();

            return true;
        } else if (item.getItemId() == R.id.help) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.help)
                    .setMessage(R.string.proxies_help)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.proxies_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }


}