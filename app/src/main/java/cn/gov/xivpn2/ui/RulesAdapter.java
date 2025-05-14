package cn.gov.xivpn2.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.ViewHolder> {

    private OnClickListener listener;
    private ArrayList<RoutingRule> routingRules;

    public RulesAdapter() {
        routingRules = new ArrayList<>();
    }

    public void setRoutingRules(ArrayList<RoutingRule> routingRules) {
        this.routingRules = routingRules;
    }

    public void setListener(OnClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rule, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RoutingRule rule = routingRules.get(position);

        if (rule.outboundSubscription.equals("none")) {
            holder.proxy.setText(rule.outboundLabel);
        } else {
            holder.proxy.setText(rule.outboundSubscription + " " + rule.outboundLabel);
        }
        holder.label.setText(rule.label);

        holder.up.setOnClickListener(v -> {
            listener.onUp(position);
        });
        holder.down.setOnClickListener(v -> {
            listener.onDown(position);
        });
        holder.delete.setOnClickListener(v -> {
            listener.onDelete(position);
        });
        holder.card.setOnClickListener(v -> {
            listener.onClick(position);
        });
    }

    @Override
    public int getItemCount() {
        return routingRules.size();
    }

    public interface OnClickListener {
        void onClick(int i);

        void onUp(int i);

        void onDown(int i);

        void onDelete(int i);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final TextView proxy;
        private final MaterialButton up;
        private final MaterialButton down;
        private final MaterialButton delete;
        private final MaterialCardView card;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.label);
            proxy = itemView.findViewById(R.id.proxy);
            up = itemView.findViewById(R.id.up);
            down = itemView.findViewById(R.id.down);
            delete = itemView.findViewById(R.id.delete);
            card = itemView.findViewById(R.id.card);
        }

    }
}
