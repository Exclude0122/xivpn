package cn.gov.xivpn2.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Proxy;

public class ProxiesAdapter extends RecyclerView.Adapter<ProxiesAdapter.ViewHolder> {

    private final ArrayList<Proxy> proxies;
    private Listener onClickListener;
    private Listener onLongClickListener;
    private int checked = -1;

    public ProxiesAdapter() {
        proxies = new ArrayList<>();
    }

    public void setOnClickListener(Listener listener) {
        this.onClickListener = listener;
    }

    public void setOnLongClickListener(Listener listener) {
        this.onLongClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_proxy, parent, false);
        return new ViewHolder(view);
    }

    public void addProxies(List<Proxy> proxyList) {
        int length = this.proxies.size();
        proxies.addAll(proxyList);
        this.notifyItemRangeInserted(length, proxyList.size());
    }

    public void clear() {
        int length = this.proxies.size();
        proxies.clear();
        this.notifyItemRangeRemoved(0, length);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Proxy proxy = proxies.get(position);
        holder.getLabel().setText(proxy.label);
        holder.getProtocol().setText(proxy.protocol.toUpperCase());
        holder.getPing().setText("");
        if (proxy.subscription.equals("none")) {
            holder.getSubscription().setText("");
        } else {
            holder.getSubscription().setText(proxy.subscription);
        }
        holder.getItemView().setOnClickListener(v -> {
            if (this.onClickListener != null) {
                this.onClickListener.onClick(v, proxy, position);
            }
        });
        holder.getItemView().setOnLongClickListener(v -> {
            if (this.onLongClickListener != null) {
                this.onLongClickListener.onClick(v, proxy, position);
                return true;
            }
            return false;
        });
        holder.getCard().setCheckable(true);
        holder.getCard().setChecked(checked == position);
    }

    public void setChecked(String label, String subscription) {
        if (checked != -1) notifyItemChanged(checked);
        for (int i = 0; i < proxies.size(); i++) {
            if (proxies.get(i).label.equals(label) && proxies.get(i).subscription.equals(subscription)) {
                checked = i;
                notifyItemChanged(checked);
                return;
            }
        }
    }

    @Override
    public int getItemCount() {
        return proxies.size();
    }

    public interface Listener {
        void onClick(View v, Proxy proxy, int i);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final TextView protocol;
        private final TextView subscription;
        private final TextView ping;
        private final View itemView;
        private final MaterialCardView card;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            label = itemView.findViewById(R.id.label);
            protocol = itemView.findViewById(R.id.protocol);
            ping = itemView.findViewById(R.id.ping);
            subscription = itemView.findViewById(R.id.subscription);
            card = itemView.findViewById(R.id.card);
        }

        public TextView getLabel() {
            return label;
        }

        public TextView getPing() {
            return ping;
        }

        public TextView getProtocol() {
            return protocol;
        }

        public TextView getSubscription() {
            return subscription;
        }

        public View getItemView() {
            return itemView;
        }

        public MaterialCardView getCard() {
            return card;
        }
    }
}
