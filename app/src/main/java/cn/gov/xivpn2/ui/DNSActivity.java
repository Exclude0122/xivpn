package cn.gov.xivpn2.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.DNS;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.XrayDNS;

public class DNSActivity extends AppCompatActivity {

    private TextInputEditText editHosts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlackBackground.apply(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dnsactivity);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.dns);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editHosts = findViewById(R.id.edit_hosts);

        MaterialButton btn = findViewById(R.id.btn);
        btn.setOnClickListener(v -> {
            startActivity(new Intent(this, DNSServersActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dns_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.help) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.help)
                    .setMessage(R.string.dns_help)
                    .setNeutralButton(R.string.open_xray_docs_dns, (dialog, which) -> {
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://xtls.github.io/en/config/dns.html"));
                            startActivity(browserIntent);
                        } catch (ActivityNotFoundException e) {
                            Log.e("DNSActivity", "open browser", e);
                        }
                    })
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        }
        if (item.getItemId() == R.id.save) {

            // save

            try {
                XrayDNS xrayDNS = DNS.readDNSSettings(getFilesDir());
                xrayDNS.hosts = new HashMap<>();

                String hosts = Objects.requireNonNull(editHosts.getText()).toString();
                String[] lines = hosts.split("\n");
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    String trimmed = line.trim();
                    String[] s = trimmed.split(" ", 2);
                    if (s.length != 2) {
                        Toast.makeText(this, getText(R.string.hosts_format_error) + ": " + trimmed, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    xrayDNS.hosts.put(s[0], s[1]);
                }

                DNS.writeDNSSettings(getFilesDir(), xrayDNS);
                XiVPNService.markConfigStale(this);

                finish();
            } catch (IOException e) {
                Log.e("DNSActivity", "save dns settings", e);
                Toast.makeText(this, R.string.could_not_save_dns, Toast.LENGTH_SHORT).show();
            }

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        try {
            XrayDNS xrayDNS = DNS.readDNSSettings(getFilesDir());
            if (xrayDNS.hosts == null) {
                editHosts.setText("");
            } else {
                StringBuilder sb = new StringBuilder();
                xrayDNS.hosts.forEach((domain, ip) -> {
                    sb.append(domain).append(" ").append(ip).append("\n");
                });
                editHosts.setText(sb.toString());
            }
        } catch (IOException e) {
            Log.e("DNSActivity", "load dns settings", e);
        }
    }
}