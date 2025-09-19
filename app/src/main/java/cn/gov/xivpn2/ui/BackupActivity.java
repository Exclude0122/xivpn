package cn.gov.xivpn2.ui;

import android.app.ComponentCaller;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import cn.gov.xivpn2.BuildConfig;
import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Subscription;

public class BackupActivity extends AppCompatActivity {

    private static final String TAG = "BackupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlackBackground.apply(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_backup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.backup);
        }


        MaterialButton backupButton = findViewById(R.id.backup);
        MaterialButton restoreButton = findViewById(R.id.restore);

        backupButton.setOnClickListener(view -> {
            java.text.SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault());
            String datetime = sdf.format(new Date());

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "xivpn_backup_" + datetime + ".zip");

            startActivityForResult(intent, 1);
        });

        restoreButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");

            startActivityForResult(intent, 2);
        });


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data, @NonNull ComponentCaller caller) {
        super.onActivityResult(requestCode, resultCode, data, caller);

        if (data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;


        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();


        if (requestCode == 1 && resultCode == RESULT_OK) {
            // save file

            Log.d(TAG, "on activity result: save " + uri);

            try {
                ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "w");
                if (parcelFileDescriptor == null)
                    throw new IOException("null parcel file descriptor");

                FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());

                // zip content
                ZipOutputStream zip = new ZipOutputStream(fileOutputStream);
                zip.setComment("XiVPN - https://github.com/Exclude0122/xivpn");
                zip.setLevel(9);


                // app version
                zip.putNextEntry(new ZipEntry("version.txt"));
                zip.write(String.valueOf(BuildConfig.VERSION_CODE).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();

                // proxies
                zip.putNextEntry(new ZipEntry("proxies.json"));
                List<Proxy> proxies = AppDatabase.getInstance().proxyDao().findAll();
                zip.write(gson.toJson(proxies).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();

                // subscriptions
                zip.putNextEntry(new ZipEntry("subscriptions.json"));
                List<Subscription> subscriptions = AppDatabase.getInstance().subscriptionDao().findAll();
                zip.write(gson.toJson(subscriptions).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();

                // rules
                zip.putNextEntry(new ZipEntry("rules.json"));
                FileUtils.copyFile(new File(getFilesDir(), "rules.json"), zip);
                zip.closeEntry();

                // dns
                zip.putNextEntry(new ZipEntry("dns.json"));
                FileUtils.copyFile(new File(getFilesDir(), "dns.json"), zip);
                zip.closeEntry();

                Toast.makeText(this, R.string.backup_finished, Toast.LENGTH_SHORT).show();

                zip.close();
                fileOutputStream.close();
                parcelFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "backup error", e);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.backup_error) + e.getClass().getName() + ": " + e.getMessage())
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }

        if (requestCode == 2 && resultCode == RESULT_OK) {

            // restore

            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) throw new IOException("null input stream");

                AppDatabase.getInstance().runInTransaction(() -> {
                    try {

                        // read zip

                        ZipInputStream zipInputStream = new ZipInputStream(inputStream);

                        ZipEntry nextEntry = zipInputStream.getNextEntry();
                        while (nextEntry != null) {
                            nextEntry = zipInputStream.getNextEntry();

                            byte[] bytes = new byte[Math.toIntExact(nextEntry.getSize())]; // TODO: getSize returns -1
                            IOUtils.readFully(zipInputStream, bytes);
                            String content = new String(bytes, StandardCharsets.UTF_8);

                            // read files

                            String fileName = nextEntry.getName();

                            switch (fileName) {
                                case "proxies.json": {

                                    List<Proxy> proxies = gson.fromJson(content, new TypeToken<>() {
                                    });
                                    AppDatabase.getInstance().proxyDao().deleteAll();
                                    for (Proxy proxy : proxies) {
                                        AppDatabase.getInstance().proxyDao().add(proxy);
                                    }

                                    break;
                                }
                                case "subscriptions.json": {

                                    List<Subscription> proxies = gson.fromJson(content, new TypeToken<>() {
                                    });
                                    AppDatabase.getInstance().proxyDao().deleteAll();
                                    for (Subscription sub : proxies) {
                                        AppDatabase.getInstance().subscriptionDao().insert(sub);
                                    }

                                    break;
                                }
                                case "rules.json":

                                    FileUtils.writeStringToFile(new File(getFilesDir(), "rules.json"), content, StandardCharsets.UTF_8);

                                    break;
                                case "dns.json":

                                    FileUtils.writeStringToFile(new File(getFilesDir(), "dns.json"), content, StandardCharsets.UTF_8);

                                    break;
                            }
                        }

                    } catch (Exception e) {
                        Log.d(TAG, "restore in transaction", e);
                        throw new RuntimeException(e);
                    }
                });

                inputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "restore error", e);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.restore_error) + e.getClass().getName() + ": " + e.getMessage())
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}