package cn.gov.xivpn2.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import cn.gov.xivpn2.BuildConfig;
import cn.gov.xivpn2.LibXivpn;
import cn.gov.xivpn2.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        findPreference("feedback").setOnPreferenceClickListener(preference -> {
            openUrl("https://github.com/Exclude0122/xivpn/issues/new");
            return true;
        });

        findPreference("privacy_policy").setOnPreferenceClickListener(preference -> {
            openUrl("https://exclude0122.github.io/docs/privacy-policy.html");
            return true;
        });

        findPreference("source_code").setOnPreferenceClickListener(preference -> {
            openUrl("https://github.com/Exclude0122/xivpn");
            return true;
        });

        findPreference("open_source_licenses").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), LicensesActivity.class));
            return true;
        });

        findPreference("xray_version").setSummary(LibXivpn.xivpn_version());

        findPreference("black_background").setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getContext(), R.string.restart_to_apply, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference("app_version").setSummary(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
    }

    private void openUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            Log.e("SettingsFragment", "open browser", e);
        }
    }
}
