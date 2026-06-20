package cn.gov.xivpn2.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.navigation.NavigationView;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.service.XiVPNService;

public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;

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

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.baseline_menu_24);
        }

        drawerLayout = findViewById(R.id.main);

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

        // show home by default
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new HomeFragment())
                    .commit();
            if (actionBar != null) {
                actionBar.setTitle(R.string.app_name);
            }
        }

        // drawer

        NavigationView navigationView = findViewById(R.id.navView);
        navigationView.setNavigationItemSelectedListener(item -> {
            Fragment fragment = null;
            int titleRes = 0;
            if (item.getItemId() == R.id.home) {
                fragment = new HomeFragment();
                titleRes = R.string.app_name;
            } else if (item.getItemId() == R.id.proxies) {
                fragment = new ProxiesFragment();
                titleRes = R.string.proxies;
            } else if (item.getItemId() == R.id.subscriptions) {
                fragment = new SubscriptionsFragment();
                titleRes = R.string.subscriptions;
            } else if (item.getItemId() == R.id.settings) {
                fragment = new SettingsFragment();
                titleRes = R.string.settings;
            } else if (item.getItemId() == R.id.rules) {
                fragment = new RulesFragment();
                titleRes = R.string.rules;
            } else if (item.getItemId() == R.id.dns_toolbox) {
                startActivity(new Intent(this, DNSToolbox.class));
            } else if (item.getItemId() == R.id.dns) {
                startActivity(new Intent(this, DNSActivity.class));
            }

            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, fragment)
                        .commit();
                if (actionBar != null && titleRes != 0) {
                    actionBar.setTitle(titleRes);
                }
            }

            drawerLayout.close();
            return false;
        });
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
