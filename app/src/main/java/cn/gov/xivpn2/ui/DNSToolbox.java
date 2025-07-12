package cn.gov.xivpn2.ui;

import android.net.ConnectivityManager;
import android.net.DnsResolver;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.gov.xivpn2.R;

public class DNSToolbox extends AppCompatActivity {

    private final Map<Integer, String> RCODES = Map.of(
            0, "OK",
            1, "Format error - The name server was unable to interpret the query.",
            2, "Server failure - The name server was unable to process this query due to a problem with the name server.",
            3, "Name Error - Domain name referenced in the query does not exist.",
            4, "Not Implemented - The name server does not support the requested kind of query.",
            5, "Refused - The name server refuses to perform the specified operation for policy reasons."
    );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlackBackground.apply(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dnstoolbox);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.dns_toolbox);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.dns_requires_api_29)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                    .show();
            return;
        }

        DnsResolver instance = DnsResolver.getInstance();

        MaterialButton btn = findViewById(R.id.btn);
        MaterialButton btn2 = findViewById(R.id.btn2);
        TextInputEditText qnameEdittext = findViewById(R.id.qname);
        AutoCompleteTextView qtypeEdittext = findViewById(R.id.qtype);
        MaterialCheckBox checkboxNoStore = findViewById(R.id.no_cache_store);
        MaterialCheckBox checkboxNoLookup = findViewById(R.id.no_cache_lookup);
        MaterialCheckBox checkboxNoRetry = findViewById(R.id.no_retry);
        TextView textView = findViewById(R.id.text);

        btn.setOnClickListener(v -> {

            boolean noStore = checkboxNoStore.isChecked();
            boolean noRetry = checkboxNoRetry.isChecked();
            boolean noLookup = checkboxNoLookup.isChecked();
            int flags = DnsResolver.FLAG_EMPTY;
            if (noStore) flags |= DnsResolver.FLAG_NO_CACHE_STORE;
            if (noRetry) flags |= DnsResolver.FLAG_NO_RETRY;
            if (noLookup) flags |= DnsResolver.FLAG_NO_CACHE_LOOKUP;

            String qname = qnameEdittext.getText().toString();
            String qtype = qtypeEdittext.getText().toString();

            if (qname.isBlank()) {
                return;
            }

            btn.setEnabled(false);
            textView.setText("");

            print("Question: " +qtype + " " + qname);

            long start = System.currentTimeMillis();

            try {
                instance.query(null, qname, qtype.equals("A") ? DnsResolver.TYPE_A : DnsResolver.TYPE_AAAA, flags, getMainExecutor(), null, new DnsResolver.Callback<>() {
                    @Override
                    public void onAnswer(@NonNull List<InetAddress> answers, int rcode) {
                        btn.setEnabled(true);

                        long timeTaken = (System.currentTimeMillis() - start);
                        print("\nGot answer in " + timeTaken + " ms");
                        print("Response code: " + rcode + " " + RCODES.getOrDefault(rcode, "") + "\n");
                        for (InetAddress answer : answers) {
                            print(answer.toString());
                        }
                    }

                    @Override
                    public void onError(@NonNull DnsResolver.DnsException error) {
                        Log.e("DNSToolbox", "on error", error);
                        btn.setEnabled(true);

                        long timeTaken = (System.currentTimeMillis() - start);
                        print("\nGot error in " + timeTaken + "ms");
                        print(error.getClass().getSimpleName() + ": " + error);
                    }
                });
            } catch (RuntimeException error) {
                btn.setEnabled(true);
                Log.e("DNSToolbox", "catch", error);
                print(error.getClass().getSimpleName() + ": " + error);
            }

        });

        btn2.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();

            try {
                ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);

                Network activeNetwork = connectivityManager.getActiveNetwork();
                Network boundNetworkForProcess = connectivityManager.getBoundNetworkForProcess();

                for (Network network : connectivityManager.getAllNetworks()) {
                    LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                    if (linkProperties == null) {
                        continue;
                    }

                    sb.append("Interface ").append(linkProperties.getInterfaceName());
                    if (network.equals(activeNetwork)) {
                        sb.append(" (Active)");
                    }
                    if (network.equals(boundNetworkForProcess)) {
                        sb.append(" (Bound)");
                    }
                    sb.append(":\n");

                    sb.append("  Private Name Server:\n");
                    String privateDnsServerName = linkProperties.getPrivateDnsServerName();
                    sb.append("   - ").append(Objects.requireNonNullElse(privateDnsServerName, "Not set")).append("\n");

                    sb.append("  DNS Servers:\n");
                    List<InetAddress> dnsServers = linkProperties.getDnsServers();
                    for (InetAddress dnsServer : dnsServers) {
                        sb.append("   - ").append(dnsServer.toString()).append("\n");
                    }

                    sb.append("  Link Addresses:\n");
                    List<LinkAddress> linkAddresses = linkProperties.getLinkAddresses();
                    for (LinkAddress linkAddress : linkAddresses) {
                        sb.append("   - ").append(linkAddress.toString()).append("\n");
                    }

                    sb.append("  Routes:\n");
                    List<RouteInfo> routes = linkProperties.getRoutes();
                    for (RouteInfo route : routes) {
                        sb.append("   - ").append(route.toString()).append("\n");
                    }

                    sb.append("\n");
                }
            } catch (Exception e) {
                Log.e("DNSToolbox", "system dns info", e);
                sb.append("\nError:\n");
                sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            }


            new AlertDialog.Builder(this)
                    .setTitle(R.string.system_network_info)
                    .setMessage(sb.toString())
                    .setPositiveButton(R.string.ok, null)
                    .show();
        });
    }

    private void print(String s) {
        TextView textView = findViewById(R.id.text);
        textView.setText(textView.getText().toString() + s + "\n");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        if (item.getItemId() == R.id.help) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.help)
                    .setMessage(R.string.dns_toolbox_help)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dns_toolbox_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }
}