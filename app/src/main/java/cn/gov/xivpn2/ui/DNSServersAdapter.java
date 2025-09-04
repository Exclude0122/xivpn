package cn.gov.xivpn2.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.xrayconfig.DNSServer;

public class DNSServersAdapter extends RecyclerView.Adapter<DNSServersAdapter.ViewHolder> {


    private final List<DNSServer> servers;
    private final OnClickListener listener;

    public DNSServersAdapter (List<DNSServer> servers, OnClickListener listener) {
        this.servers = servers;
        this.listener = listener;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dns_server, parent, false);
        return new DNSServersAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DNSServer dnsServer = servers.get(position);

        holder.label.setText(dnsServer.address);

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
        return servers.size();
    }

    public interface OnClickListener {
        void onClick(int i);

        void onUp(int i);

        void onDown(int i);

        void onDelete(int i);
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final MaterialButton up;
        private final MaterialButton down;
        private final MaterialButton delete;
        private final MaterialCardView card;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            label = itemView.findViewById(R.id.label);
            up = itemView.findViewById(R.id.up);
            down = itemView.findViewById(R.id.down);
            delete = itemView.findViewById(R.id.delete);
            card = itemView.findViewById(R.id.card);
        }
    }
}
