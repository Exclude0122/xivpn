package cn.gov.xivpn2.ui;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.gov.xivpn2.R;

public class SplitTunnelActivity extends AppCompatActivity {

    private static final String TAG = "SplitTunnelActivity";
    private final List<InstalledAppsAdapter.App> allApps = new ArrayList<>();
    private Thread thread = null;
    private InstalledAppsAdapter adapter;
    private String searchKeyword = "";
    private boolean unsavedChanges = false;

    private List<InstalledAppsAdapter.App> filter() {
        ArrayList<InstalledAppsAdapter.App> newList = new ArrayList<>();
        for (InstalledAppsAdapter.App app : allApps) {
            if (app.packageName.toLowerCase().contains(searchKeyword) || app.appName.toLowerCase().contains(searchKeyword)) {
                newList.add(app);
            }
        }
        return newList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_split_tunnel);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.split_tunnel);
        }

        // recycler view
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new InstalledAppsAdapter();

        adapter.onCheckListener = (packageName, isChecked) -> {
            unsavedChanges = true;
            for (int i = 0; i < allApps.size(); i++) {
                InstalledAppsAdapter.App oldApp = allApps.get(i);
                if (oldApp.packageName.equals(packageName)) {
                    allApps.set(i, new InstalledAppsAdapter.App(oldApp.appName, oldApp.icon, oldApp.packageName, isChecked));
                }
            }
            adapter.replaceAll(filter());
        };

        recyclerView.setAdapter(adapter);

        // selected apps
        SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
        Set<String> selectedPackageNames = sp.getStringSet("APP_LIST", new HashSet<>());
        unsavedChanges = false;

        // get app list
        thread = new Thread(() -> {

            Log.d(TAG, "loading apps...");

            for (PackageInfo installedPackage : getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                ApplicationInfo applicationInfo = installedPackage.applicationInfo;

                if (applicationInfo == null) {
                    continue;
                }
                if (applicationInfo.packageName.equals(getApplication().getPackageName())) {
                    continue;
                }

                Drawable drawable = applicationInfo.loadIcon(getPackageManager());
                String label = applicationInfo.loadLabel(getPackageManager()).toString();

                InstalledAppsAdapter.App app = new InstalledAppsAdapter.App(label, drawable, applicationInfo.packageName, selectedPackageNames.contains(applicationInfo.packageName));
                allApps.add(app);
            }
            Log.d(TAG, "loaded " + allApps.size() + " apps");

            if (Thread.interrupted()) {
                return;
            }

            runOnUiThread(() -> {
                Log.d(TAG, "run on ui thread");

                // add apps to adapter
                adapter.replaceAll(filter());

                // hide spinner
                CircularProgressIndicator progress = findViewById(R.id.progress);
                progress.hide();

            });
        });
        thread.start();

        // prevent back button

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (unsavedChanges) {
                    showUnsavedChangesWarning();
                } else {
                    finish();
                }
            }
        });


    }

    @Override
    protected void onDestroy() {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.split_tunnel_activity, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchKeyword = newText.toLowerCase();
                    adapter.replaceAll(filter());
                    return true;
                }
            });

            searchView.setOnCloseListener(() -> {
                searchKeyword = "";
                adapter.replaceAll(filter());
                return false;
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    private void saveAndExit() {
        unsavedChanges = false;

        SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        HashSet<String> apps = new HashSet<>();
        for (int i = 0; i < allApps.size(); i++) {
            if (!allApps.get(i).checked) continue;
            apps.add(allApps.get(i).packageName);
        }
        editor.putStringSet("APP_LIST", apps);

        editor.apply();

        finish();
    }

    private void showUnsavedChangesWarning() {
        new AlertDialog.Builder(SplitTunnelActivity.this)
            .setTitle(R.string.warning)
            .setMessage(R.string.unsaved_changes)
            .setPositiveButton(R.string.save, (dialog, which) -> {
                saveAndExit();
            })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.discard_changes, (dialog, which) -> {
                finish();
            })
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (unsavedChanges) {
                showUnsavedChangesWarning();
            } else {
                finish();
            }
            return true;
        }
        if (item.getItemId() == R.id.save) {
            saveAndExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}