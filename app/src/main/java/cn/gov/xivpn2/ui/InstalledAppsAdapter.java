package cn.gov.xivpn2.ui;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import java.util.List;
import java.util.function.BiConsumer;

import cn.gov.xivpn2.R;

public class InstalledAppsAdapter extends RecyclerView.Adapter<InstalledAppsAdapter.ViewHolder> {

    public BiConsumer<String, Boolean> onCheckListener = null;

    public final SortedList<App> apps = new SortedList<>(App.class, new SortedList.Callback<App>() {
        @Override
        public int compare(App o1, App o2) {
            if (o1.checked && !o2.checked) return -1;
            if (!o1.checked && o2.checked) return 1;
            return o1.appName.compareToIgnoreCase(o2.appName);
        }

        @Override
        public void onChanged(int position, int count) {
            InstalledAppsAdapter.this.notifyItemRangeChanged(position, count);
        }

        @Override
        public boolean areContentsTheSame(App oldItem, App newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areItemsTheSame(App item1, App item2) {
            return item1.equals(item2);
        }

        @Override
        public void onInserted(int position, int count) {
            InstalledAppsAdapter.this.notifyItemRangeInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            InstalledAppsAdapter.this.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            InstalledAppsAdapter.this.notifyItemMoved(fromPosition, toPosition);
        }
    });

    public InstalledAppsAdapter() {
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.checkBox.setOnCheckedChangeListener(null);

        App app = apps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.appName.setText(app.appName);
        holder.packageName.setText(app.packageName);
        holder.checkBox.setChecked(app.checked);

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            apps.updateItemAt(holder.getBindingAdapterPosition(), new App(app.appName, app.icon, app.packageName, isChecked));
            onCheckListener.accept(app.packageName, isChecked);
        });
    }

    public void replaceAll(List<App> models) {
        apps.beginBatchedUpdates();
        for (int i = apps.size() - 1; i >= 0; i--) {
            final App model = apps.get(i);
            if (!models.contains(model)) {
                apps.remove(model);
            }
        }
        apps.addAll(models);
        apps.endBatchedUpdates();
    }


    @Override
    public int getItemCount() {
        return apps.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView icon;
        private final TextView appName;
        private final TextView packageName;
        private final CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            appName = itemView.findViewById(R.id.app_name);
            packageName = itemView.findViewById(R.id.package_name);
            checkBox = itemView.findViewById(R.id.checkbox);
        }

    }

    public static class App {
        public final String appName;
        public final Drawable icon;
        public final String packageName;
        public final boolean checked;

        public App(String appName, Drawable icon, String packageName, boolean checked) {
            this.appName = appName;
            this.icon = icon;
            this.packageName = packageName;
            this.checked = checked;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof App)) return false;
            return ((App) obj).packageName.equals(this.packageName) && ((App) obj).appName.equals(this.appName) && ((App) obj).checked == this.checked;
        }
    }
}
