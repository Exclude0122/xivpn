package cn.gov.xivpn2.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Proxy;

public class ProxiesAdapter extends RecyclerView.Adapter<ProxiesAdapter.ViewHolder> {

    private final ArrayList<Proxy> proxies;
    private Listener onClickListener;
    private int checked = -1;

    public ProxiesAdapter() {
        proxies = new ArrayList<>();
    }

    public void setOnClickListener(Listener listener) {
        this.onClickListener = listener;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_proxy, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Replace proxies with new ones
     */
    public void replaceProxies(List<Proxy> newProxies) {

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return proxies.size();
            }

            @Override
            public int getNewListSize() {
                return newProxies.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Proxy oldProxy = proxies.get(oldItemPosition);
                Proxy newProxy = newProxies.get(newItemPosition);
                return oldProxy.id == newProxy.id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Proxy oldProxy = proxies.get(oldItemPosition);
                Proxy newProxy = newProxies.get(newItemPosition);
                return oldProxy.equals(newProxy);
            }
        });

        this.proxies.clear();
        this.proxies.addAll(newProxies);
        diffResult.dispatchUpdatesTo(this);

    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Proxy proxy = proxies.get(position);
        holder.getLabel().setText(proxy.label);
        holder.getProtocol().setText(proxy.protocol.toUpperCase());

        if (proxy.subscription.equals("none")) {
            holder.getSubscription().setText("");
        } else {
            holder.getSubscription().setText(proxy.subscription);
        }

        if (proxy.protocol.equals("freedom") || proxy.protocol.equals("blackhole") || proxy.protocol.equals("dns")) {
            holder.getEdit().setVisibility(View.GONE);
            holder.getShare().setVisibility(View.GONE);
            holder.getDelete().setVisibility(View.GONE);
        } else if (proxy.protocol.equals("xray-json")) {
            holder.getEdit().setVisibility(View.GONE);
            holder.getShare().setVisibility(View.GONE);
            holder.getDelete().setVisibility(View.VISIBLE);
        } else {
            holder.getEdit().setVisibility(View.VISIBLE);
            holder.getShare().setVisibility(View.VISIBLE);
            holder.getDelete().setVisibility(View.VISIBLE);
        }

        holder.getItemView().setOnClickListener(v -> {
            if (this.onClickListener != null) {
                this.onClickListener.onClick(v, proxy, position);
            }
        });

        holder.getItemView().setOnLongClickListener(v -> {
            if (this.onClickListener != null) {
                this.onClickListener.onLongClick(v, proxy, position);
                return true;
            }
            return false;
        });

        holder.getCard().setCheckable(true);
        holder.getCard().setChecked(checked == position);

        holder.getDelete().setOnClickListener(v -> {
            if (this.onClickListener != null) {
                this.onClickListener.onDelete(v, proxy, position);
            }
        });

        holder.getEdit().setOnClickListener(v -> {
            if (this.onClickListener != null) {
                this.onClickListener.onEdit(v, proxy, position);
            }
        });

        holder.getShare().setOnClickListener(v -> {
            if (this.onClickListener != null) {
                this.onClickListener.onShare(v, proxy, position);
            }
        });
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
        void onLongClick(View v, Proxy proxy, int i);
        void onDelete(View v, Proxy proxy, int i);
        void onShare(View v, Proxy proxy, int i);
        void onEdit(View v, Proxy proxy, int i);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final TextView protocol;
        private final TextView subscription;
        private final View itemView;
        private final MaterialButton edit;
        private final MaterialButton share;
        private final MaterialButton delete;
        private final MaterialCardView card;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            label = itemView.findViewById(R.id.label);
            protocol = itemView.findViewById(R.id.protocol);
            subscription = itemView.findViewById(R.id.subscription);
            card = itemView.findViewById(R.id.card);
            edit = itemView.findViewById(R.id.edit);
            share = itemView.findViewById(R.id.share);
            delete = itemView.findViewById(R.id.delete);

        }

        public TextView getLabel() {
            return label;
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

        public MaterialButton getDelete() {
            return delete;
        }

        public MaterialButton getEdit() {
            return edit;
        }

        public MaterialButton getShare() {
            return share;
        }
    }
}
