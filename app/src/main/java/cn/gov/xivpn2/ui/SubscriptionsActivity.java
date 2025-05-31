package cn.gov.xivpn2.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.database.Subscription;
import cn.gov.xivpn2.service.SubscriptionWork;

public class SubscriptionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SubscriptionsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subscriptions);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.subscriptions);

        // recycler view

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SubscriptionsAdapter();
        recyclerView.setAdapter(adapter);

        refresh();

        // fab

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            View view = LayoutInflater.from(this).inflate(R.layout.add_subscription, null);
            TextInputEditText labelEditText = view.findViewById(R.id.label);
            TextInputEditText urlEditText = view.findViewById(R.id.url);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.subscription)
                    .setView(view)
                    .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                        if (labelEditText.getText().toString().isEmpty() || urlEditText.getText().toString().isEmpty()) {
                            Toast.makeText(this, getString(R.string.empty_label_or_url), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (AppDatabase.getInstance().subscriptionDao().findByLabel(labelEditText.getText().toString()) != null) {
                            Toast.makeText(this, getString(R.string.subscription_already_exists), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Subscription subscription = new Subscription();
                        subscription.label = labelEditText.getText().toString();
                        subscription.url = urlEditText.getText().toString();
                        subscription.autoUpdate = 180;
                        AppDatabase.getInstance().subscriptionDao().insert(subscription);

                        refresh();
                    })
                    .show();
        });

        // list item on click

        adapter.setOnClickListener(subscription -> {
            View view = LayoutInflater.from(this).inflate(R.layout.edit_subscription, null);
            TextInputEditText urlEditText = view.findViewById(R.id.url);
            urlEditText.setText(subscription.url);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(subscription.label)
                    .setView(view)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        // edit
                        AppDatabase.getInstance().subscriptionDao().updateUrl(subscription.label, urlEditText.getText().toString());
                        refresh();
                    })
                    .setNegativeButton(R.string.delete, (dialog, which) -> {
                        // delete
                        AppDatabase.getInstance().proxyDao().deleteBySubscription(subscription.label);
                        AppDatabase.getInstance().subscriptionDao().delete(subscription.label);

                        try {
                            Rules.resetDeletedProxies(getSharedPreferences("XIVPN", MODE_PRIVATE), getApplicationContext().getFilesDir());
                        } catch (IOException e) {
                            Log.e("SubscriptionsActivity", "reset deleted proxies", e);
                        }

                        refresh();
                    })
                    .show();
        });
    }

    private void refresh() {
        adapter.clear();
        adapter.addSubscriptions(AppDatabase.getInstance().subscriptionDao().findAll());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.subscription_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        if (item.getItemId() == R.id.refresh) {
            WorkManager workManager = WorkManager.getInstance(this);
            workManager.enqueue(
                    new OneTimeWorkRequest.Builder(SubscriptionWork.class)
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build()
            );
        }
        return super.onOptionsItemSelected(item);
    }
}