package cn.gov.xivpn2.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.widget.CompoundButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.LabelSubscription;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;
import cn.gov.xivpn2.xrayconfig.ProxyGroupSettings;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private DrawerLayout drawerLayout;
    private XiVPNService.XiVPNBinder binder;
    private XiVPNService.VPNStateListener vpnStatusListener;
    private MainActivityAdapter adapter;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (XiVPNService.XiVPNBinder) service;

            adapter.updateVpnState(binder.getState());

            binder.addListener(vpnStatusListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // bind and start vpn service
        bindService(new Intent(this, XiVPNService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (binder != null) binder.removeListener(vpnStatusListener);
        unbindService(connection);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.baseline_menu_24);
        }

        drawerLayout = findViewById(R.id.main);

        // recycler view

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));


        // adapter

        adapter = new MainActivityAdapter(new MainActivityAdapter.Listener() {
            @Override
            public void onSwitchCheckedChange(CompoundButton button, boolean isChecked) {

                // on switch checked change

                adapter.setMessage("");

                if (isChecked) {
                    // start vpn

                    // request vpn permission
                    Intent intent = XiVPNService.prepare(MainActivity.this);
                    if (intent != null) {
                        button.setChecked(false);
                        startActivityForResult(intent, 1);
                        return;
                    }

                    // check whether geoip / geosite database is downloaded
                    try {
                        boolean geoip = false;
                        boolean geosite = false;
                        List<RoutingRule> routingRules = Rules.readRules(getFilesDir());
                        for (RoutingRule routingRule : routingRules) {
                            for (String s : routingRule.ip) {
                                if (s.startsWith("geoip:")) {
                                    geoip = true;
                                }
                                if (s.startsWith("geosite:")) {
                                    geosite = true;
                                }
                            }
                            for (String s : routingRule.domain) {
                                if (s.startsWith("geoip:")) {
                                    geoip = true;
                                }
                                if (s.startsWith("geosite:")) {
                                    geosite = true;
                                }
                            }
                        }
                        if ((geoip && !new File(getFilesDir(), "geoip.dat").isFile()) || (geosite && !new File(getFilesDir(), "geosite.dat").isFile())) {
                            // ask the user to download geoip / geosite database
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(R.string.warning)
                                    .setMessage(R.string.geoip_not_downloaded)
                                    .setPositiveButton(R.string.download, (dialog, which) -> {
                                        startActivity(new Intent(MainActivity.this, GeoAssetsActivity.class));
                                    })
                                    .show();
                            button.setChecked(false);
                            return;
                        }
                    } catch (IOException e) {
                        Log.e("MainActivity", "read rules", e);
                    }

                    // start service
                    Intent intent2 = new Intent(MainActivity.this, XiVPNService.class);
                    intent2.setAction("cn.gov.xivpn2.START");
                    intent2.putExtra("always-on", false);
                    startForegroundService(intent2);

                } else {
                    // stop
                    Intent intent2 = new Intent(MainActivity.this, XiVPNService.class);
                    intent2.setAction("cn.gov.xivpn2.STOP");
                    intent2.putExtra("always-on", false);
                    startService(intent2);
                }
            }

            @Override
            public void onServerSelected(LabelSubscription group, LabelSubscription selected) {

                // on proxy group selection change

                Proxy proxyGroup = AppDatabase.getInstance().proxyDao().find(group.label, group.subscription);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Outbound<ProxyGroupSettings> proxyGroupSettings = gson.fromJson(proxyGroup.config, new TypeToken<Outbound<ProxyGroupSettings>>() {
                }.getType());

                proxyGroupSettings.settings.selected = selected;

                String json = gson.toJson(proxyGroupSettings);

                AppDatabase.getInstance().proxyDao().updateConfig(group.label, group.subscription, json);

                XiVPNService.markConfigStale(MainActivity.this);

            }
        });


        recyclerView.setAdapter(adapter);

        // request notification permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        2
                );
            }
        }

        // drawer

        NavigationView navigationView = findViewById(R.id.navView);
        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.proxies) {
                startActivity(new Intent(this, ProxiesActivity.class));
            }
            if (item.getItemId() == R.id.subscriptions) {
                startActivity(new Intent(this, SubscriptionsActivity.class));
            }
            if (item.getItemId() == R.id.settings) {
                startActivity(new Intent(this, PreferenceActivity.class));
            }
            if (item.getItemId() == R.id.rules) {
                startActivity(new Intent(this, RulesActivity.class));
            }
            if (item.getItemId() == R.id.dns_toolbox) {
                startActivity(new Intent(this, DNSToolbox.class));
            }
            if (item.getItemId() == R.id.dns) {
                startActivity(new Intent(this, DNSActivity.class));
            }
            drawerLayout.close();
            return false;
        });

        // vpn service listener
        vpnStatusListener = new XiVPNService.VPNStateListener() {
            @Override
            public void onStateChanged(XiVPNService.VPNState state) {
                Log.i("MainActivity", "onStatusChanged " + state.name());
                adapter.updateVpnState(state);
            }

            @Override
            public void onMessage(String msg) {
                adapter.setMessage(msg);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        // update proxy groups

        List<Proxy> proxies = findUsedProxyGroups();
        Map<LabelSubscription, Pair<List<LabelSubscription>, LabelSubscription>> map = new HashMap<>();
        for (Proxy proxy : proxies) {
            LabelSubscription key = new LabelSubscription(proxy.label, proxy.subscription);

            Gson gson = new Gson();
            Outbound<ProxyGroupSettings> proxyGroupSettings = gson.fromJson(proxy.config, new TypeToken<Outbound<ProxyGroupSettings>>() {
            }.getType());

            if (proxyGroupSettings.settings.selected == null) {
                // default to the first one
                // same behavior as XiVPNService
                proxyGroupSettings.settings.selected = proxyGroupSettings.settings.proxies.get(0);
            }

            map.put(key, Pair.create(proxyGroupSettings.settings.proxies, proxyGroupSettings.settings.selected));
        }

        adapter.setGroups(map);
    }

    private ArrayList<Proxy> findUsedProxyGroups() {
        ArrayList<Proxy> proxies = new ArrayList<>();
        HashSet<LabelSubscription> visited = new HashSet<>();

        // catch all
        SharedPreferences sp = getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
        String selectedLabel = sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)");
        String selectedSubscription = sp.getString("SELECTED_SUBSCRIPTION", "none");
        recurseUsedProxyGroups(new LabelSubscription(selectedLabel, selectedSubscription), proxies, visited);

        // routing
        try {
            List<RoutingRule> rules = Rules.readRules(getFilesDir());

            for (RoutingRule rule : rules) {
                recurseUsedProxyGroups(new LabelSubscription(rule.outboundLabel, rule.outboundSubscription), proxies, visited);
            }
        } catch (IOException e) {
            Log.wtf(TAG, "build xray config", e);
        }

        return proxies;
    }

    /**
     * Recursively find proxy groups used by newProxy.
     * @param proxies proxy groups
     */
    private void recurseUsedProxyGroups(LabelSubscription labelSub, ArrayList<Proxy> proxies, HashSet<LabelSubscription> visited) {
        if (visited.contains(labelSub)) {
            return;
        }
        visited.add(labelSub);

        Proxy newProxy = AppDatabase.getInstance().proxyDao().find(labelSub.label, labelSub.subscription);

        if (newProxy == null) {
            return;
        }

        if (newProxy.protocol.equals("proxy-group")) {
            // add the new proxy group to proxies
            proxies.add(newProxy);

            // recursively find its dependencies
            Gson gson = new Gson();
            Outbound<ProxyGroupSettings> proxyGroupSettings = gson.fromJson(newProxy.config, new TypeToken<Outbound<ProxyGroupSettings>>() {
            }.getType());

            for (LabelSubscription newLabelSub: proxyGroupSettings.settings.proxies) {
                recurseUsedProxyGroups(newLabelSub, proxies, visited);
            }
        } else if (newProxy.protocol.equals("proxy-chain")) {
            // recursively find its dependencies
            Gson gson = new Gson();
            Outbound<ProxyChainSettings> proxyChainSettings = gson.fromJson(newProxy.config, new TypeToken<Outbound<ProxyChainSettings>>() {
            }.getType());

            for (LabelSubscription newLabelSub: proxyChainSettings.settings.proxies) {
                recurseUsedProxyGroups(newLabelSub, proxies, visited);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        // drawer
        if (item.getItemId() == android.R.id.home) {
            if (drawerLayout.isOpen()) {
                drawerLayout.close();
            } else {
                drawerLayout.open();
            }
        }

        return super.onOptionsItemSelected(item);
    }


}