package cn.gov.xivpn2.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import cn.gov.xivpn2.BuildConfig;
import cn.gov.xivpn2.R;

public class CrashActivity extends AppCompatActivity {

    private static @NonNull String getMessage(String exception) {


        return "Please report this issue to the developers.\n" +
                "Android Release: " + Build.VERSION.RELEASE + "\n" +
                "Android SDK: " + Build.VERSION.SDK_INT + "\n" +
                "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Supported ABIs: " + String.join(", ", Build.SUPPORTED_ABIS) + "\n" +
                "App Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n" +
                "\n" +
                "Exception:\n" +
                exception;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_crash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("App Crashed");
        }

        String exception = getIntent().getStringExtra("EXCEPTION");
        if (exception == null) {
            exception = "NULL EXCEPTION";
        }

        String crashReport = getMessage(exception);

        TextView textView = findViewById(R.id.textview);
        textView.setText(crashReport);

        Button btn = findViewById(R.id.btn_report);
        btn.setOnClickListener(v -> {
            try {
                String url = "https://github.com/Exclude0122/xivpn/issues/new?title=Crash&body=" + URLEncoder.encode(crashReport, "UTF-8");
                Log.d("CrashActivity", "open " + url);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            } catch (ActivityNotFoundException | UnsupportedEncodingException e) {
                Log.e("CrashActivity", "open browser", e);
            }
        });
    }
}