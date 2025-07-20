package cn.gov.xivpn2.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Index;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.DNS;
import cn.gov.xivpn2.xrayconfig.DNSServer;
import cn.gov.xivpn2.xrayconfig.XrayDNS;

public class DNSServerActivity extends AppCompatActivity {
    private final static String TAG = "DNSServerActivity";
    private int index = 0;
    private TextInputEditText address;
    private TextInputEditText port;
    private TextInputEditText tag;
    private TextInputEditText domains;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlackBackground.apply(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dnsserver);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        index = getIntent().getIntExtra("INDEX", -1);

        address = findViewById(R.id.edit_address);
        port = findViewById(R.id.edit_port);
        tag = findViewById(R.id.edit_tag);
        domains = findViewById(R.id.edit_domains);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (index == -1) {
            // new
            getSupportActionBar().setTitle(R.string.new_dns_server);
        } else {
            getSupportActionBar().setTitle(R.string.edit_dns_server);

            try {
                XrayDNS xrayDNS = DNS.readDNSSettings(getFilesDir());
                DNSServer dnsServer = xrayDNS.servers.get(index);
                address.setText(dnsServer.address);
                port.setText(String.valueOf(dnsServer.port));
                tag.setText(dnsServer.tag);
                domains.setText(String.join("\n", dnsServer.domains));
            } catch (IOException | IndexOutOfBoundsException e) {
                Log.e(TAG, "read dns settings", e);
                Toast.makeText(this, R.string.could_not_read_dns, Toast.LENGTH_SHORT).show();
                finish();
            }
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dns_server_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.save) {
            try {
                XrayDNS xrayDNS = DNS.readDNSSettings(getFilesDir());

                if (index == -1) {
                    DNSServer e = new DNSServer();
                    e.address = address.getText().toString();
                    e.tag = tag.getText().toString();
                    if (!domains.getText().toString().isBlank()) {
                        e.domains = Arrays.asList(domains.getText().toString().split("\n"));
                    }
                    else {
                        e.domains = List.of();
                    }
                    e.port = Integer.parseInt(port.getText().toString());
                    xrayDNS.servers.add(e);
                } else {
                    DNSServer e = xrayDNS.servers.get(index);
                    e.address = address.getText().toString();
                    e.tag = tag.getText().toString();
                    if (!domains.getText().toString().isBlank()) {
                        e.domains = Arrays.asList(domains.getText().toString().split("\n"));
                    }
                    else {
                        e.domains = List.of();
                    }
                    e.port = Integer.parseInt(port.getText().toString());
                }

                DNS.writeDNSSettings(getFilesDir(), xrayDNS);
            } catch (IOException | IndexOutOfBoundsException e) {
                Log.e(TAG, "write dns settings", e);
                Toast.makeText(this, R.string.could_not_save_dns, Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                return true;
            }
            finish();
            return true;
        }
        if (item.getItemId() == R.id.help) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.help)
                    .setMessage(R.string.dns_server_help)
                    .setNeutralButton(R.string.open_xray_docs_dns, (dialog, which) -> {
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://xtls.github.io/en/config/dns.html#dnsserverobject"));
                            startActivity(browserIntent);
                        } catch (ActivityNotFoundException e) {
                            Log.e(TAG, "open browser", e);
                        }
                    })
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}