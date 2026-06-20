package cn.gov.xivpn2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class RulesFragment extends Fragment {

    private final ArrayList<RoutingRule> rules = new ArrayList<>();
    private final String TAG = "RulesFragment";
    private RulesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rules, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.rules);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // recycler view
        adapter = new RulesAdapter();
        adapter.setRoutingRules(rules);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setListener(new RulesAdapter.OnClickListener() {
            @Override
            public void onClick(int i) {
                Intent intent = new Intent(requireContext(), RuleActivity.class);
                intent.putExtra("INDEX", i);
                startActivity(intent);
            }

            @Override
            public void onUp(int i) {
                if (i == 0) return;
                RoutingRule tmp = rules.get(i);
                rules.set(i, rules.get(i - 1));
                rules.set(i - 1, tmp);
                adapter.notifyItemRangeChanged(i - 1, 2);
                saveRules();
            }

            @Override
            public void onDown(int i) {
                if (i == rules.size() - 1) return;
                RoutingRule tmp = rules.get(i);
                rules.set(i, rules.get(i + 1));
                rules.set(i + 1, tmp);
                adapter.notifyItemRangeChanged(i, 2);
                saveRules();
            }

            @Override
            public void onDelete(int i) {
                rules.remove(i);
                saveRules();
                adapter.notifyItemRemoved(i);
                adapter.notifyItemRangeChanged(i, rules.size() - i);
            }
        });

        // fab
        FloatingActionButton fab = view.findViewById(R.id.add);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RuleActivity.class);
            intent.putExtra("INDEX", -1);
            startActivity(intent);
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.rules);
        }
        loadRules();
    }

    /**
     * Load routing rules from file
     */
    private void loadRules() {
        try {
            int size = rules.size();
            rules.clear();
            adapter.notifyItemRangeRemoved(0, size);

            rules.addAll(Rules.readRules(requireContext().getFilesDir()));
            adapter.notifyItemRangeInserted(0, rules.size());

        } catch (IOException e) {
            Log.e(TAG, "load rules", e);
            Toast.makeText(requireContext(), e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Write routing rules to the file
     */
    private void saveRules() {
        try {
            Rules.writeRules(requireContext().getFilesDir(), rules);
        } catch (IOException e) {
            Log.e(TAG, "save rules", e);
            Toast.makeText(requireContext(), e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();

        }

        XiVPNService.markConfigStale(requireContext());
    }


    @Nullable
    private ActionBar getSupportActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        }
        return null;
    }
}
