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
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.IOException;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class MainActivity extends AppCompatActivity {

    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private MaterialSwitch aSwitch;
    private TextView textView;
    private XiVPNService.XiVPNBinder binder;
    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (XiVPNService.XiVPNBinder) service;

            updateSwitch(binder.getState());

            binder.addListener(vpnStatusListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };
    private XiVPNService.VPNStateListener vpnStatusListener;

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
    protected void onResume() {
        super.onResume();
        textView.setText("");
        SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
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


        // bind views
        textView = findViewById(R.id.textview);
        aSwitch = findViewById(R.id.vpn_switch);
        drawerLayout = findViewById(R.id.main);
        navigationView = findViewById(R.id.navView);


        onCheckedChangeListener = (compoundButton, b) -> {
            textView.setText("");

            if (b) {
                // start vpn

                // request vpn permission
                Intent intent = XiVPNService.prepare(this);
                if (intent != null) {
                    aSwitch.setChecked(false);
                    startActivityForResult(intent, 1);
                    return;
                }

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
                    }
                    if ((geoip && !new File(getFilesDir(), "geoip.dat").isFile()) || (geosite && !new File(getFilesDir(), "geosite.dat").isFile())) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.geoip_not_downloaded)
                                .setPositiveButton(R.string.download, (dialog, which) -> {
                                    startActivity(new Intent(this, GeoAssetsActivity.class));
                                })
                                .show();
                        aSwitch.setChecked(false);
                        return;
                    }
                } catch (IOException e) {
                    Log.e("MainActivity", "read rules", e);
                }

                // start service
                Intent intent2 = new Intent(this, XiVPNService.class);
                intent2.setAction("cn.gov.xivpn2.START");
                intent2.putExtra("always-on", false);
                startForegroundService(intent2);

            } else {
                // stop
                Intent intent2 = new Intent(this, XiVPNService.class);
                intent2.setAction("cn.gov.xivpn2.STOP");
                intent2.putExtra("always-on", false);
                startService(intent2);
            }
        };
        aSwitch.setOnCheckedChangeListener(onCheckedChangeListener);

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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.baseline_menu_24);
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
            drawerLayout.close();
            return false;
        });

        // vpn service listener
        vpnStatusListener = new XiVPNService.VPNStateListener() {
            @Override
            public void onStateChanged(XiVPNService.VPNState state) {
                Log.i("MainActivity", "onStatusChanged " + state.name());
                updateSwitch(state);
            }

            @Override
            public void onMessage(String msg) {
                textView.setText(msg);
            }
        };
    }

    /**
     * update switch based on the status of vpn
     */
    private void updateSwitch(XiVPNService.VPNState state) {
        // set listener to null so setChecked will not trigger the listener
        aSwitch.setOnCheckedChangeListener(null);

        if (state == XiVPNService.VPNState.CONNECTED || state == XiVPNService.VPNState.DISCONNECTED) {
            aSwitch.setChecked(state == XiVPNService.VPNState.CONNECTED);
            aSwitch.setEnabled(true);
        } else {
            aSwitch.setChecked(state == XiVPNService.VPNState.ESTABLISHING_VPN || state == XiVPNService.VPNState.STARTING_LIBXI);
            aSwitch.setEnabled(false);
        }

        aSwitch.setOnCheckedChangeListener(onCheckedChangeListener);
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