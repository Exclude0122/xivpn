package cn.gov.xivpn2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.DNS;
import cn.gov.xivpn2.xrayconfig.DNSServer;
import cn.gov.xivpn2.xrayconfig.ProxyChain;
import cn.gov.xivpn2.xrayconfig.XrayDNS;

public class DNSServersActivity extends AppCompatActivity {

    private final static String TAG = "DNSServersActivity";
    private final List<DNSServer> dnsServers = new ArrayList<>();
    private DNSServersAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlackBackground.apply(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dnsservers);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.dns_servers);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new DNSServersAdapter(dnsServers, new DNSServersAdapter.OnClickListener() {
            @Override
            public void onClick(int i) {
                Intent intent = new Intent(DNSServersActivity.this, DNSServerActivity.class);
                intent.putExtra("INDEX", i);
                startActivity(intent);
            }

            @Override
            public void onUp(int i) {
                if (i == 0) return;
                DNSServer tmp = dnsServers.get(i);
                dnsServers.set(i, dnsServers.get(i - 1));
                dnsServers.set(i - 1, tmp);
                adapter.notifyItemRangeChanged(i - 1, 2);

                save();
            }

            @Override
            public void onDown(int i) {
                if (i == dnsServers.size() - 1) return;
                DNSServer tmp = dnsServers.get(i);
                dnsServers.set(i, dnsServers.get(i + 1));
                dnsServers.set(i + 1, tmp);
                adapter.notifyItemRangeChanged(i, 2);

                save();
            }

            @Override
            public void onDelete(int i) {
                if (dnsServers.size() == 1) {
                    Toast.makeText(DNSServersActivity.this, R.string.at_least_one_dns_server, Toast.LENGTH_SHORT).show();
                    return;
                }
                dnsServers.remove(i);
                adapter.notifyItemRemoved(i);
                adapter.notifyItemRangeChanged(i, dnsServers.size() - i);

                save();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);


        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {

            Intent intent = new Intent(DNSServersActivity.this, DNSServerActivity.class);
            intent.putExtra("INDEX", -1);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        refresh();
    }

    private void refresh() {
        try {
            XrayDNS xrayDNS = DNS.readDNSSettings(getFilesDir());
            int size = dnsServers.size();
            dnsServers.clear();
            adapter.notifyItemRangeRemoved(0, size);
            dnsServers.addAll(xrayDNS.servers);
            adapter.notifyItemRangeInserted(0, dnsServers.size());
        } catch (IOException e) {
            Log.e(TAG, "read dns settings", e);
        }
    }

    private void save() {
        try {
            XrayDNS xrayDNS = DNS.readDNSSettings(getFilesDir());

            xrayDNS.servers = dnsServers;

            DNS.writeDNSSettings(getFilesDir(), xrayDNS);
        } catch (IOException e) {
            Log.e(TAG, "write dns settings", e);
            Toast.makeText(this, R.string.could_not_save_dns, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dns_servers_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }
}