package cn.gov.xivpn2.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.service.GeoDownloaderWork;

public class GeoAssetsActivity extends AppCompatActivity {

    private RadioGroup groupGeoip;
    private RadioGroup groupGeosite;
    private AppCompatEditText editGeoip;
    private AppCompatEditText editGeosite;
    private boolean disableListeners = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_geo_assets);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        groupGeoip = findViewById(R.id.group_geoip);
        groupGeosite = findViewById(R.id.group_geosite);
        editGeoip = findViewById(R.id.edit_custom_geoip);
        editGeosite = findViewById(R.id.edit_custom_geosite);

        groupGeosite.setOnCheckedChangeListener((group, checkedId) -> {
            if (disableListeners) return;
            save();
            load();
        });

        groupGeoip.setOnCheckedChangeListener((group, checkedId) -> {
            if (disableListeners) return;
            save();
            load();
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        } else if (item.getItemId() == R.id.update) {
            try {
                WorkManager workManager = WorkManager.getInstance(this);
                List<WorkInfo> works = workManager.getWorkInfosByTag("geoassets").get();

                boolean running = false;
                for (WorkInfo work : works) {
                    if (work.getState() == WorkInfo.State.RUNNING || work.getState() == WorkInfo.State.ENQUEUED) {
                        running = true;
                        break;
                    }
                }

                if (!running) {
                    workManager.enqueue(
                            new OneTimeWorkRequest.Builder(GeoDownloaderWork.class)
                                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                                    .addTag("geoassets")
                                    .build()
                    );
                }


            } catch (ExecutionException | InterruptedException e) {
                Log.e("GeoAssetsActivity", "enqueue work", e);
            }

            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();

        if (groupGeoip.getCheckedRadioButtonId() == R.id.v2fly_geoip) {
            edit.putString("GEOIP", "v2fly");
        } else if (groupGeoip.getCheckedRadioButtonId() == R.id.v2fly_geoip_cn) {
            edit.putString("GEOIP", "v2fly_cn");
        } else if (groupGeoip.getCheckedRadioButtonId() == R.id.ipinfo_geoip) {
            edit.putString("GEOIP", "ipinfo");
        } else if (groupGeoip.getCheckedRadioButtonId() == R.id.ipinfo_geoip_cn) {
            edit.putString("GEOIP", "ipinfo_cn");
        } else if (groupGeoip.getCheckedRadioButtonId() == R.id.ipinfo_geoip_ir) {
            edit.putString("GEOIP", "ipinfo_ir");
        } else if (groupGeoip.getCheckedRadioButtonId() == R.id.ipinfo_geoip_ru) {
            edit.putString("GEOIP", "ipinfo_ru");
        } else if (groupGeoip.getCheckedRadioButtonId() == R.id.geoip_custom) {
            edit.putString("GEOIP", "custom");
        }
        edit.putString("GEOIP_CUSTOM", Objects.requireNonNull(editGeoip.getText()).toString());


        if (groupGeosite.getCheckedRadioButtonId() == R.id.v2lfy_dlc) {
            edit.putString("GEOSITE", "v2fly");
        } else if (groupGeosite.getCheckedRadioButtonId() == R.id.geosite_custom) {
            edit.putString("GEOSITE", "custom");
        }
        edit.putString("GEOSITE_CUSTOM", Objects.requireNonNull(editGeosite.getText()).toString());

        edit.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.geo_assets_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }


    private void load() {
        disableListeners = true;

        SharedPreferences sp = getSharedPreferences("XIVPN", MODE_PRIVATE);
        String geoip = sp.getString("GEOIP", "v2fly");
        String geosite = sp.getString("GEOSITE", "v2fly");

        switch (geoip) {
            case "v2fly":
                groupGeoip.check(R.id.v2fly_geoip);
                break;
            case "v2fly_cn":
                groupGeoip.check(R.id.v2fly_geoip_cn);
                break;
            case "ipinfo":
                groupGeoip.check(R.id.ipinfo_geoip);
                break;
            case "ipinfo_cn":
                groupGeoip.check(R.id.ipinfo_geoip_cn);
                break;
            case "ipinfo_ru":
                groupGeoip.check(R.id.ipinfo_geoip_ru);
                break;
            case "ipinfo_ir":
                groupGeoip.check(R.id.ipinfo_geoip_ir);
                break;
        }

        editGeoip.setText(sp.getString("GEOIP_CUSTOM", ""));
        if (geoip.equals("custom")) {
            groupGeoip.check(R.id.geoip_custom);
            editGeoip.setVisibility(View.VISIBLE);
        } else {
            editGeoip.setVisibility(View.GONE);
        }

        if (geosite.equals("v2fly")) {
            groupGeosite.check(R.id.v2lfy_dlc);
        }

        editGeosite.setText(sp.getString("GEOSITE_CUSTOM", ""));
        if (geosite.equals("custom")) {
            editGeosite.setVisibility(View.VISIBLE);
            groupGeosite.check(R.id.geosite_custom);
        } else {
            editGeosite.setVisibility(View.GONE);
        }

        disableListeners = false;
    }

}