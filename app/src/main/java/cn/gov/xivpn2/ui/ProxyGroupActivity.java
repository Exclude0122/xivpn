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
import androidx.appcompat.app.ActionBar;
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
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.ProxyDao;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.LabelSubscription;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;
import cn.gov.xivpn2.xrayconfig.ProxyGroupSettings;

public class ProxyGroupActivity extends AppCompatActivity {

    private final static String TAG = "ProxyGroupActivity";

    private final ArrayList<LabelSubscription> proxies = new ArrayList<>();
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

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.proxy_group);
        }

        label = getIntent().getStringExtra("LABEL");
        subscription = getIntent().getStringExtra("SUBSCRIPTION");

        // load config
        String config = getIntent().getStringExtra("CONFIG");
        if (config != null) {
            Gson gson = new Gson();
            Outbound<ProxyGroupSettings> outbound = gson.fromJson(config, new TypeToken<Outbound<ProxyGroupSettings>>() {

            }.getType());
            proxies.addAll(outbound.settings.proxies);
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
                LabelSubscription tmp = proxies.get(i);
                proxies.set(i, proxies.get(i - 1));
                proxies.set(i - 1, tmp);
                adapter.notifyItemRangeChanged(i - 1, 2);
            }

            @Override
            public void onDown(int i) {
                if (i == proxies.size() - 1) return;
                LabelSubscription tmp = proxies.get(i);
                proxies.set(i, proxies.get(i + 1));
                proxies.set(i + 1, tmp);
                adapter.notifyItemRangeChanged(i, 2);
            }

            @Override
            public void onDelete(int i) {
                proxies.remove(i);
                adapter.notifyItemRemoved(i);
                adapter.notifyItemRangeChanged(i, proxies.size() - i);
            }
        });
        recyclerView.setAdapter(adapter);

        adapter.setProxies(proxies);

        // fab
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            // add new proxy
            View view = LayoutInflater.from(this).inflate(R.layout.select_proxy, null);
            AutoCompleteTextView autoCompleteTextView = view.findViewById(R.id.edit_text);

            // find all proxies
            List<Proxy> proxies = AppDatabase.getInstance().proxyDao().findAll();
            ArrayList<String> selections = new ArrayList<>();
            for (Proxy proxy : proxies) {
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
                        LabelSubscription pc = new LabelSubscription();
                        pc.label = selected[0];
                        pc.subscription = selected[1];
                        this.proxies.add(pc);
                        adapter.notifyItemInserted(this.proxies.size() - 1);
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

            if (proxies.isEmpty()) {
                Toast.makeText(this, R.string.proxy_chain_empty, Toast.LENGTH_SHORT).show();
                return true;
            }

            Outbound<ProxyChainSettings> outbound = new Outbound<>();
            outbound.protocol = "proxy-group";
            outbound.settings = new ProxyChainSettings();
            outbound.settings.proxies = proxies;

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
                proxy.protocol = "proxy-group";
                proxyDao.add(proxy);
            }

            finish();
            return true;

        } else if (item.getItemId() == R.id.help) {

            new AlertDialog.Builder(this)
                    .setTitle(R.string.help)
                    .setMessage(R.string.proxy_group_help)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.proxychain_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }
}