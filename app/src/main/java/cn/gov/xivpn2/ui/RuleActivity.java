package cn.gov.xivpn2.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AutoCompleteTextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class RuleActivity extends AppCompatActivity {

    private RoutingRule rule;
    private TextInputEditText label;
    private TextInputEditText domains;
    private TextInputEditText ips;
    private TextInputEditText port;
    private TextInputEditText protocols;
    private AutoCompleteTextView network;
    private AutoCompleteTextView outbound;
    private TextInputLayout outboundLayout;
    private List<Proxy> proxies;
    private int index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rule);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // bind views
        label = findViewById(R.id.edit_label);
        domains = findViewById(R.id.edit_domains);
        ips = findViewById(R.id.edit_ips);
        port = findViewById(R.id.edit_port);
        protocols = findViewById(R.id.edit_protocols);
        network = findViewById(R.id.edit_network);
        outbound = findViewById(R.id.edit_out);
        outboundLayout = findViewById(R.id.layout_out);

        // load rule
        index = getIntent().getIntExtra("INDEX", -1);

        try {
            if (index < 0) {
                // add new rule
                rule = new RoutingRule();
                rule.label = "New Rule";
                rule.outboundSubscription = "none";
                rule.outboundLabel = "No Proxy (Bypass Mode)";
                rule.domain = new ArrayList<>();
                rule.ip = new ArrayList<>();
                rule.port = "";
                rule.network = "tcp,udp";
                rule.protocol = new ArrayList<>();
                rule.outboundTag = "";
                getSupportActionBar().setTitle(R.string.new_rule);
            } else {
                // edit existing rule
                rule = Rules.readRules(getFilesDir()).get(index);
                getSupportActionBar().setTitle(R.string.edit_rule);
            }

            Log.d("RuleActivity", "rule " + rule.label);

            label.setText(rule.label);
            domains.setText(String.join("\n", rule.domain));
            ips.setText(String.join("\n", rule.ip));
            port.setText(rule.port);
            protocols.setText(String.join("\n", rule.protocol));

            network.setAdapter(new NonFilterableArrayAdapter(this, R.layout.list_item, List.of("tcp", "udp", "tcp,udp")));
            network.setText(rule.network);

            // outbound selector
            proxies = AppDatabase.getInstance().proxyDao().findAll();

            String selected = "";

            // fetch all proxies
            ArrayList<String> selections = new ArrayList<>();
            for (Proxy proxy : proxies) {
                String s = "";
                if (proxy.subscription.equals("none")) {
                    s = proxy.label;
                } else {
                    s = proxy.subscription + " | " + proxy.label;
                }
                if (rule.outboundLabel.equals(proxy.label) && rule.outboundSubscription.equals(proxy.subscription)) {
                    selected = s;
                }
                selections.add(s);
            }

            outbound.setAdapter(new NonFilterableArrayAdapter(this, R.layout.list_item, selections));
            outbound.setOnItemClickListener((parent, view, position, id) -> {
                rule.outboundSubscription = proxies.get(position).subscription;
                rule.outboundLabel = proxies.get(position).label;
            });

            outbound.setText(selected);

        } catch (IOException e) {
            Log.wtf("RuleActivity", "read rules", e);
        }


    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.proxy_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.save) {

            rule.label = label.getText().toString();
            rule.domain = Arrays.asList(domains.getText().toString().split("\n"));
            if (domains.getText().toString().isEmpty()) rule.domain = List.of();
            rule.ip = Arrays.asList(ips.getText().toString().split("\n"));
            if (ips.getText().toString().isEmpty()) rule.ip = List.of();
            rule.port = port.getText().toString();
            rule.network = network.getText().toString();
            rule.protocol = Arrays.asList(protocols.getText().toString().split("\n"));
            if (protocols.getText().toString().isEmpty()) rule.protocol = List.of();
            rule.outboundTag = "";

            try {
                List<RoutingRule> rules = Rules.readRules(getFilesDir());
                if (index < 0) {
                    rules.add(rule);
                } else {
                    rules.set(index, rule);
                }
                Rules.writeRules(getFilesDir(), rules);

                XiVPNService.markConfigStale(this);
            } catch (IOException e) {
                Log.wtf("RuleActivity", "save", e);
            }

            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}