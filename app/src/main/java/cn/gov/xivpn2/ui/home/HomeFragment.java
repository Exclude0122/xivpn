package cn.gov.xivpn2.ui.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import cn.gov.xivpn2.ui.GeoAssetsActivity;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.LabelSubscription;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;
import cn.gov.xivpn2.xrayconfig.ProxyGroupSettings;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class HomeFragment extends Fragment {
    private final String TAG = "HomeFragment";
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
    public void onStart() {
        super.onStart();
        // bind and start vpn service
        requireContext().bindService(new Intent(requireContext(), XiVPNService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (binder != null) binder.removeListener(vpnStatusListener);
        requireContext().unbindService(connection);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // recycler view

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));


        // adapter

        adapter = new MainActivityAdapter(new MainActivityAdapter.Listener() {
            @Override
            public void onSwitchCheckedChange(CompoundButton button, boolean isChecked) {

                // on switch checked change

                adapter.setMessage("");

                if (isChecked) {
                    // start vpn

                    // request vpn permission
                    Intent intent = XiVPNService.prepare(requireContext());
                    if (intent != null) {
                        button.setChecked(false);
                        startActivityForResult(intent, 200);
                        return;
                    }

                    // check whether geoip / geosite database is downloaded
                    try {
                        boolean geoip = false;
                        boolean geosite = false;
                        List<RoutingRule> routingRules = Rules.readRules(requireContext().getFilesDir());
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
                        if ((geoip && !new File(requireContext().getFilesDir(), "geoip.dat").isFile()) || (geosite && !new File(requireContext().getFilesDir(), "geosite.dat").isFile())) {
                            // ask the user to download geoip / geosite database
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.warning)
                                    .setMessage(R.string.geoip_not_downloaded)
                                    .setPositiveButton(R.string.download, (dialog, which) -> {
                                        startActivity(new Intent(requireContext(), GeoAssetsActivity.class));
                                    })
                                    .show();
                            button.setChecked(false);
                            return;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "read rules", e);
                    }

                    // start service
                    Intent intent2 = new Intent(requireContext(), XiVPNService.class);
                    intent2.setAction("cn.gov.xivpn2.START");
                    intent2.putExtra("always-on", false);
                    requireContext().startForegroundService(intent2);

                } else {
                    // stop
                    Intent intent2 = new Intent(requireContext(), XiVPNService.class);
                    intent2.setAction("cn.gov.xivpn2.STOP");
                    intent2.putExtra("always-on", false);
                    requireContext().startService(intent2);
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

                XiVPNService.markConfigStale(requireContext());

            }
        });


        recyclerView.setAdapter(adapter);

        // vpn service listener
        vpnStatusListener = new XiVPNService.VPNStateListener() {
            @Override
            public void onStateChanged(XiVPNService.VPNState state) {
                Log.i(TAG, "onStatusChanged " + state.name());
                adapter.updateVpnState(state);
            }

            @Override
            public void onMessage(String msg) {
                adapter.setMessage(msg);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.app_name);
        }

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
        SharedPreferences sp = requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
        String selectedLabel = sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)");
        String selectedSubscription = sp.getString("SELECTED_SUBSCRIPTION", "none");
        recurseUsedProxyGroups(new LabelSubscription(selectedLabel, selectedSubscription), proxies, visited);

        // routing
        try {
            List<RoutingRule> rules = Rules.readRules(requireContext().getFilesDir());

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

    @Nullable
    private ActionBar getSupportActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        }
        return null;
    }
}
