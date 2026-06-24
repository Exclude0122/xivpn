package cn.gov.xivpn2.ui.home;

import android.Manifest;
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
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.ui.BlackBackground;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();

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

        BottomNavigationView navigationView = findViewById(R.id.navView);
        navigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int titleRes = 0;
            if (item.getItemId() == R.id.home) {
                fragment = new HomeFragment();
                titleRes = R.string.app_name;
            } else if (item.getItemId() == R.id.proxies) {
                fragment = new ProxiesFragment();
                titleRes = R.string.proxies;
            } else if (item.getItemId() == R.id.settings) {
                fragment = new AllSettingsFragment();
                titleRes = R.string.settings;
            }

            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, fragment)
                        .commit();
                if (actionBar != null && titleRes != 0) {
                    actionBar.setTitle(titleRes);
                }
                return true;
            }

            return false;
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        return super.onOptionsItemSelected(item);
    }


}
