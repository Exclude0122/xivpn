package cn.gov.xivpn2.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.ProxyDao;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.ProxyChain;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;

public class ProxyChainActivity extends AppCompatActivity {

    private final static String TAG = "ProxyChainActivity";

    private final ArrayList<ProxyChain> proxyChains = new ArrayList<>();
    private String label = "";
    private String subscription = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlackBackground.apply(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_proxy_chain);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.proxy_chain);

        label = getIntent().getStringExtra("LABEL");
        subscription = getIntent().getStringExtra("SUBSCRIPTION");

        // load config
        String config = getIntent().getStringExtra("CONFIG");
        if (config != null) {
            Gson gson = new Gson();
            Outbound<ProxyChainSettings> outbound = gson.fromJson(config, new TypeToken<Outbound<ProxyChainSettings>>() {

            }.getType());
            proxyChains.addAll(outbound.settings.proxies);
        }

        // recycler view

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        ProxyChainAdapter adapter = new ProxyChainAdapter();
        adapter.setListener(new ProxyChainAdapter.OnClickListener() {
            @Override
            public void onClick(int i) {

            }

            @Override
            public void onUp(int i) {
                if (i == 0) return;
                ProxyChain tmp = proxyChains.get(i);
                proxyChains.set(i, proxyChains.get(i - 1));
                proxyChains.set(i - 1, tmp);
                adapter.notifyItemRangeChanged(i - 1, 2);
            }

            @Override
            public void onDown(int i) {
                if (i == proxyChains.size() - 1) return;
                ProxyChain tmp = proxyChains.get(i);
                proxyChains.set(i, proxyChains.get(i + 1));
                proxyChains.set(i + 1, tmp);
                adapter.notifyItemRangeChanged(i, 2);
            }

            @Override
            public void onDelete(int i) {
                proxyChains.remove(i);
                adapter.notifyItemRemoved(i);
                adapter.notifyItemRangeChanged(i, proxyChains.size() - i);
            }
        });
        recyclerView.setAdapter(adapter);

        adapter.setProxies(proxyChains);

        // fab
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            // add new proxy
            View view = LayoutInflater.from(this).inflate(R.layout.select_proxy, null);
            AutoCompleteTextView autoCompleteTextView = view.findViewById(R.id.edit_text);

            // find all proxies
            List<Proxy> proxies = AppDatabase.getInstance().proxyDao().findAll();
            ArrayList<String> selections = new ArrayList<>();
            Iterator<Proxy> iterator = proxies.iterator();
            while (iterator.hasNext()) {
                Proxy proxy = iterator.next();
                if (proxy.protocol.equals("proxy-chain")) {
                    iterator.remove();
                    continue;
                }
                String s = "";
                if (proxy.subscription.equals("none")) {
                    s = proxy.label;
                } else {
                    s = proxy.subscription + " | " + proxy.label;
                }
                selections.add(s);
            }

            final String[] selected = {"", ""}; // label, subscription

            autoCompleteTextView.setAdapter(new NonFilterableArrayAdapter(this, R.layout.list_item, selections));
            autoCompleteTextView.setOnItemClickListener((parent, view1, position, id) -> {
                selected[1] = proxies.get(position).subscription;
                selected[0] = proxies.get(position).label;
            });

            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_proxy)
                    .setView(view)
                    .setPositiveButton(R.string.add, (dialog, which) -> {
                        if (selected[0].isEmpty() && selected[1].isEmpty()) return;

                        // add proxy to proxy chain
                        ProxyChain pc = new ProxyChain();
                        pc.label = selected[0];
                        pc.subscription = selected[1];
                        proxyChains.add(pc);
                        adapter.notifyItemInserted(proxyChains.size() - 1);
                    })
                    .show();
        });
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.save) {

            if (proxyChains.isEmpty()) {
                Toast.makeText(this, R.string.proxy_chain_empty, Toast.LENGTH_SHORT).show();
                return true;
            }

            Outbound<ProxyChainSettings> outbound = new Outbound<>();
            outbound.protocol = "proxy-chain";
            outbound.settings = new ProxyChainSettings();
            outbound.settings.proxies = proxyChains;

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(outbound);
            Log.d(TAG, json);

            // save
            ProxyDao proxyDao = AppDatabase.getInstance().proxyDao();
            if (proxyDao.exists(label, subscription) > 0) {
                // update
                proxyDao.updateConfig(label, subscription, json);
            } else {
                // insert
                Proxy proxy = new Proxy();
                proxy.subscription = subscription;
                proxy.label = label;
                proxy.config = json;
                proxy.protocol = "proxy-chain";
                proxyDao.add(proxy);
            }

            finish();
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.proxy_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }
}