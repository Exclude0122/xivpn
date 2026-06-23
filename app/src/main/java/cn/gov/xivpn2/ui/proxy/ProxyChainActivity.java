package cn.gov.xivpn2.ui.proxy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.ProxyDao;
import cn.gov.xivpn2.ui.BlackBackground;
import cn.gov.xivpn2.ui.ProxySelectActivity;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.LabelSubscription;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;

public class ProxyChainActivity extends AppCompatActivity {

    private final static String TAG = "ProxyChainActivity";

    private final ArrayList<LabelSubscription> proxyChains = new ArrayList<>();
    private String label = "";
    private String subscription = "";

    private ProxyChainAdapter adapter;
    private ActivityResultLauncher<Intent> proxySelectLauncher;

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

        adapter = new ProxyChainAdapter();
        adapter.setListener(new ProxyChainAdapter.OnClickListener() {
            @Override
            public void onClick(int i) {

            }

            @Override
            public void onUp(int i) {
                if (i == 0) return;
                LabelSubscription tmp = proxyChains.get(i);
                proxyChains.set(i, proxyChains.get(i - 1));
                proxyChains.set(i - 1, tmp);
                adapter.notifyItemRangeChanged(i - 1, 2);
            }

            @Override
            public void onDown(int i) {
                if (i == proxyChains.size() - 1) return;
                LabelSubscription tmp = proxyChains.get(i);
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

        // Register the proxy select launcher (excludes proxy-chain)
        proxySelectLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String labels = result.getData().getStringExtra(ProxySelectActivity.RESULT_LABEL);
                        String subscriptions = result.getData().getStringExtra(ProxySelectActivity.RESULT_SUBSCRIPTION);
                        if (labels == null || subscriptions == null) return;
                        proxyChains.add(new LabelSubscription(labels, subscriptions));
                        adapter.notifyItemInserted(proxyChains.size() - 1);
                    }
                }
        );

        // fab
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProxySelectActivity.class);
            intent.putExtra(ProxySelectActivity.EXTRA_MULTI, false);
            intent.putStringArrayListExtra(ProxySelectActivity.EXTRA_EXCLUDE_PROTOCOLS, new ArrayList<>(List.of("proxy-chain", "xray-json")));
            proxySelectLauncher.launch(intent);
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

        } else if (item.getItemId() == R.id.help) {

            new AlertDialog.Builder(this)
                    .setTitle(R.string.help)
                    .setMessage(R.string.proxy_chain_help)
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
