package cn.gov.xivpn2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class RulesActivity extends AppCompatActivity {

    private final ArrayList<RoutingRule> rules = new ArrayList<>();
    private final String TAG = "RulesActivity";
    private RulesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rules);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.rules);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // recycler view
        adapter = new RulesAdapter();
        adapter.setRoutingRules(rules);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setListener(new RulesAdapter.OnClickListener() {
            @Override
            public void onClick(int i) {
                Intent intent = new Intent(RulesActivity.this, RuleActivity.class);
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
        FloatingActionButton fab = findViewById(R.id.add);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(RulesActivity.this, RuleActivity.class);
            intent.putExtra("INDEX", -1);
            startActivity(intent);
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
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

            rules.addAll(Rules.readRules(getFilesDir()));
            adapter.notifyItemRangeInserted(0, rules.size());

        } catch (IOException e) {
            Log.e(TAG, "load rules", e);
            Toast.makeText(this, e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Write routing rules to the file
     */
    private void saveRules() {
        try {
            Rules.writeRules(getFilesDir(), rules);
        } catch (IOException e) {
            Log.e(TAG, "save rules", e);
            Toast.makeText(this, e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();

        }

        XiVPNService.markConfigStale(this);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}