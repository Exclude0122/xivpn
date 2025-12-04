package cn.gov.xivpn2.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import cn.gov.xivpn2.R;

public class CrashLogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_crash_log);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setTitle(R.string.error);

        String fileName = getIntent().getStringExtra("FILE");
        if (fileName != null) {
            File file = new File(getCacheDir(), fileName);

            Log.i("CrashLogActivity", "read file " + file.getAbsolutePath());


            try {
                String text = FileUtils.readFileToString(file, "utf-8");
                TextView textView = findViewById(R.id.text);
                textView.setText(text);
            } catch (IOException e) {
                Log.e("CrashLogActivity", "read crash log", e);
            }
        }
    }
}