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
import cn.gov.xivpn2.xrayconfig.ProxyChain;

public class ProxyChainAdapter extends RecyclerView.Adapter<ProxyChainAdapter.ViewHolder> {

    private OnClickListener listener;
    private ArrayList<ProxyChain> proxies;

    public ProxyChainAdapter() {
        proxies = new ArrayList<>();
    }

    public void setProxies(ArrayList<ProxyChain> proxies) {
        this.proxies = proxies;
    }

    public void setListener(OnClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_proxy_chain, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProxyChain p = proxies.get(position);

        holder.subcription.setText(p.subscription);
        holder.label.setText(p.label);

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
        return proxies.size();
    }

    public interface OnClickListener {
        void onClick(int i);

        void onUp(int i);

        void onDown(int i);

        void onDelete(int i);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final TextView subcription;
        private final MaterialButton up;
        private final MaterialButton down;
        private final MaterialButton delete;
        private final MaterialCardView card;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.label);
            subcription = itemView.findViewById(R.id.proxy);
            up = itemView.findViewById(R.id.up);
            down = itemView.findViewById(R.id.down);
            delete = itemView.findViewById(R.id.delete);
            card = itemView.findViewById(R.id.card);
        }

    }
}
