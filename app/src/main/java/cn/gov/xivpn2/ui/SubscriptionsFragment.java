package cn.gov.xivpn2.ui;

import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.database.Subscription;
import cn.gov.xivpn2.service.SubscriptionWork;

public class SubscriptionsFragment extends Fragment {

    private SubscriptionsAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscriptions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.subscriptions);
        }

        // recycler view

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new SubscriptionsAdapter();
        recyclerView.setAdapter(adapter);

        refresh();

        // fab

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.add_subscription, null);
            TextInputEditText labelEditText = dialogView.findViewById(R.id.label);
            TextInputEditText urlEditText = dialogView.findViewById(R.id.url);
            AutoCompleteTextView type = dialogView.findViewById(R.id.type);
            MaterialCheckBox ignoreRoutingDns = dialogView.findViewById(R.id.ignore_routing_dns);

            type.setAdapter(new NonFilterableArrayAdapter(requireContext(), R.layout.list_item, List.of(getResources().getStringArray(R.array.subscription_types))));
            type.setText(getResources().getStringArray(R.array.subscription_types)[0]);
            ignoreRoutingDns.setVisibility(View.GONE);
            type.setOnItemClickListener((parent, itemView, position, id) -> {
                ignoreRoutingDns.setVisibility(position != 0 ? View.VISIBLE : View.GONE);
                if (position == 0) ignoreRoutingDns.setChecked(false);
            });

            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.subscription)
                    .setView(dialogView)
                    .setPositiveButton(getString(R.string.add), (dialog, which) -> {
                        if (Objects.requireNonNull(labelEditText.getText()).toString().isEmpty() || Objects.requireNonNull(urlEditText.getText()).toString().isEmpty()) {
                            Toast.makeText(requireContext(), getString(R.string.empty_label_or_url), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (AppDatabase.getInstance().subscriptionDao().findByLabel(labelEditText.getText().toString()) != null) {
                            Toast.makeText(requireContext(), getString(R.string.subscription_already_exists), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Subscription subscription = new Subscription();
                        subscription.label = labelEditText.getText().toString();
                        subscription.url = urlEditText.getText().toString();
                        subscription.autoUpdate = 180;
                        subscription.type = type.getText().toString().equals(getResources().getStringArray(R.array.subscription_types)[0]) ? "v2rayng" : "xray-json";
                        subscription.ignoreRoutingDns = ignoreRoutingDns.isChecked();
                        AppDatabase.getInstance().subscriptionDao().insert(subscription);

                        refresh();

                        // show xray-json warning
                        SharedPreferences sp = requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
                        if (subscription.type.equals("xray-json") && sp.getBoolean("XRAY_JSON_SUBSCRIPTION_WARNING", true)) {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.warning)
                                    .setMessage(R.string.xray_json_subscription_warning)
                                    .setPositiveButton(R.string.ok, null)
                                    .setNeutralButton(R.string.dont_show_again, (dialog1, which1) -> {
                                        sp.edit().putBoolean("XRAY_JSON_SUBSCRIPTION_WARNING", false).apply();
                                    })
                                    .show();
                        }
                    })
                    .show();
        });

        // list item on click

        adapter.setOnClickListener(subscription -> {
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.edit_subscription, null);
            TextInputEditText urlEditText = dialogView.findViewById(R.id.url);
            MaterialCheckBox ignoreRoutingDns = dialogView.findViewById(R.id.ignore_routing_dns);
            urlEditText.setText(subscription.url);
            ignoreRoutingDns.setChecked(subscription.ignoreRoutingDns);
            ignoreRoutingDns.setVisibility("xray-json".equals(subscription.type) ? View.VISIBLE : View.GONE);

            new AlertDialog.Builder(requireContext())
                    .setTitle(subscription.label)
                    .setView(dialogView)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        AppDatabase.getInstance().subscriptionDao().updateUrl(subscription.label, urlEditText.getText().toString());
                        AppDatabase.getInstance().subscriptionDao().updateIgnoreRoutingDns(subscription.label, ignoreRoutingDns.isChecked());
                        refresh();
                    })
                    .setNegativeButton(R.string.delete, (dialog, which) -> {
                        // delete
                        AppDatabase.getInstance().proxyDao().deleteBySubscription(subscription.label);
                        AppDatabase.getInstance().subscriptionDao().delete(subscription.label);

                        try {
                            Rules.resetDeletedProxies(requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE), requireContext().getFilesDir());
                        } catch (IOException e) {
                            Log.e("SubscriptionsFragment", "reset deleted proxies", e);
                        }

                        refresh();
                    })
                    .show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.subscriptions);
        }
    }

    private void refresh() {
        adapter.clear();
        adapter.addSubscriptions(AppDatabase.getInstance().subscriptionDao().findAll());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.subscription_activity, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            ProgressBar progressBar = getView().findViewById(R.id.progress);

            WorkManager workManager = WorkManager.getInstance(requireContext());

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SubscriptionWork.class)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag("MANUAL_SUBSCRIPTION_REFRESH")
                    .build();
            workManager.enqueue(workRequest);
            workManager.getWorkInfoByIdLiveData(workRequest.getId()).observe(getViewLifecycleOwner(), workInfo -> {
                if (workInfo == null) return;
                if (workInfo.getState().isFinished()) {
                    progressBar.setVisibility(View.GONE);
                } else if (workInfo.getState() == WorkInfo.State.RUNNING) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setIndeterminate(true);
                }
            });


        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private ActionBar getSupportActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        }
        return null;
    }
}
