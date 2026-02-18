package cn.gov.xivpn2.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import cn.gov.xivpn2.R;

public class ProxyEditTextAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements IProxyEditor {

    private final static String TAG = "ProxyEditTextAdapter";

    private final ArrayList<Input> inputs;
    private BiConsumer<String, String> onInputChanged;

    public ProxyEditTextAdapter() {
        inputs = new ArrayList<>();
    }

    @Override
    public void setOnInputChangedListener(BiConsumer<String, String> onInputChanged) {
        this.onInputChanged = onInputChanged;
    }

    @Override
    public int getItemViewType(int position) {
        if (inputs.get(position) instanceof ButtonInput) return 2;
        if (inputs.get(position) instanceof SelectInput) return 0;
        if (inputs.get(position) instanceof TextInputGen) return 4;
        if (inputs.get(position) instanceof TextInput) return 1;
        if (inputs.get(position) instanceof TitleInput) return 3;
        if (inputs.get(position) instanceof TextAreaInput) return 5;
        throw new IllegalArgumentException("unexpected view type at position " + position);
    }

    /**
     * Add the input if no input with the same key exists.
     */
    @Override
    public void addInput(Input input) {
        boolean found = false;
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(input.key)) {
                found = true;
                break;
            }
        }
        if (!found) {
            int size = this.inputs.size();
            inputs.add(input);
            this.notifyItemInserted(size);
        }
        if (onInputChanged != null) {
            if (input instanceof SelectInput) {
                onInputChanged.accept(input.key, ((SelectInput) input).value);
            }
        }
    }

    @Override
    public void addInputAfter(String key, Input input) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(key)) {

                inputs.add(i + 1, input);
                this.notifyItemInserted(i + 1);

                if (onInputChanged != null) {
                    if (input instanceof SelectInput) {
                        onInputChanged.accept(input.key, ((SelectInput) input).value);
                    }
                }

                break;
            }
            if (inputs.get(i).key.equals(input.key)) {
                // skip if input with same key exists
                break;
            }
        }
    }

    @Override
    public void addInput(String key, String label) {
        this.addInput(new TextInput(key, label, ""));
    }

    public void addTextAreaInput(String key, String label, String helperText) {
        this.addInput(new TextAreaInput(key, label, helperText));
    }

    @Override
    public void addGroupTitle(String key, String label) {
        this.addInput(new TitleInput(key, label));
    }

    @Override
    public void addInput(String key, String label, String defaultValue) {
        this.addInput(new TextInput(key, label, "", defaultValue));
    }

    @Override
    public void addInput(String key, String label, String defaultValue, String helperText) {
        this.addInput(new TextInput(key, label, helperText, defaultValue));
    }

    @Override
    public void addInput(String key, String label, List<String> selections) {
        this.addInput(new SelectInput(key, label, "", selections));
    }

    @Override
    public void addInputAfter(String after, String key, String label) {
        this.addInputAfter(after, new TextInput(key, label, ""));
    }

    @Override
    public void addInputAfter(String after, String key, String label, Runnable onClick) {
        this.addInputAfter(after, new ButtonInput(key, label, "", onClick));
    }

    @Override
    public void addInputAfter(String after, String key, String label, String defaultValue) {
        this.addInputAfter(after, new TextInput(key, label, "", defaultValue));
    }

    @Override
    public void addInputAfter(String after, String key, String label, String defaultValue, String helperText) {
        this.addInputAfter(after, new TextInput(key, label, helperText, defaultValue));
    }

    @Override
    public void addInputAfter(String after, String key, String label, List<String> selections) {
        this.addInputAfter(after, new SelectInput(key, label, "", selections));
    }

    /**
     * Remove input with the key. Does nothing if the key does not exist.
     */
    @Override
    public void removeInput(String key) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(key)) {
                inputs.remove(i);
                this.notifyItemRemoved(i);
                break;
            }
        }
    }

    @Override
    public void removeInputByPrefix(String prefix) {
        Iterator<Input> iterator = inputs.iterator();
        int i = 0;
        int removed = 0;
        while (iterator.hasNext()) {
            Input next = iterator.next();
            if (next.key.startsWith(prefix)) {
                iterator.remove();
                Log.d(TAG, "notifyItemRemoved " + (i - removed));
                this.notifyItemRemoved(i - removed);
                removed++;
            }
            i++;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dropdown, parent, false);
            return new DropdownViewHolder(view);
        } else if (viewType == 1) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edittext, parent, false);
            return new EditTextViewHolder(view);
        } else if (viewType == 2) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_button, parent, false);
            return new ButtonViewHolder(view);
        } else if (viewType == 3) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title, parent, false);
            return new TitleViewHolder(view);
        } else if (viewType == 4) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edittext_gen, parent, false);
            return new EditTextViewHolder(view);
        } else if (viewType == 5) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_edittext_multiline, parent, false);
            return new EditTextMultilineViewHolder(view);
        } else {
            throw new IllegalArgumentException("view type " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {

        if (h instanceof EditTextViewHolder) {
            TextInput input = ((TextInput) inputs.get(position));

            EditTextViewHolder holder = (EditTextViewHolder) h;

            holder.onTextChanged = null;

            holder.editText.setText(input.value);
            holder.layout.setHint(input.title);
            holder.layout.setHelperText(input.helperText);
            if (input.validated) {
                holder.layout.setError(null);
            } else {
                holder.layout.setError("Invalid value");
            }

            if (input.readonly) {
                holder.editText.setInputType(InputType.TYPE_NULL);
                holder.editText.setFocusable(false);
                holder.editText.setOnClickListener((view) -> {
                    ClipboardManager clipman = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("", input.value);
                    clipman.setPrimaryClip(clip);
                });
            }

            holder.onTextChanged = () -> {
                input.value = (Objects.requireNonNull(holder.editText.getText()).toString());
                if (onInputChanged != null) {
                    onInputChanged.accept(input.key, input.value);
                }

                if (input instanceof TextInputGen) {
                    ((TextInputGen) input).onTextChanged.run();
                }
            };

            if (input instanceof TextInputGen) {
                holder.layout.setEndIconOnClickListener(((TextInputGen) input).onClickListener);
            }
        }

        if (h instanceof EditTextMultilineViewHolder) {
            TextAreaInput input = ((TextAreaInput) inputs.get(position));

            EditTextMultilineViewHolder holder = (EditTextMultilineViewHolder) h;

            holder.onTextChanged = null;

            holder.editText.setText(input.value);
            holder.layout.setHint(input.title);
            holder.layout.setHelperText(input.helperText);
            if (input.validated) {
                holder.layout.setError(null);
            } else {
                holder.layout.setError("Invalid value");
            }

            holder.onTextChanged = () -> {
                input.value = (Objects.requireNonNull(holder.editText.getText()).toString());
                if (onInputChanged != null) {
                    onInputChanged.accept(input.key, input.value);
                }

            };

        }

        if (h instanceof DropdownViewHolder) {
            SelectInput input = ((SelectInput) inputs.get(position));

            DropdownViewHolder holder = (DropdownViewHolder) h;

            holder.onTextChanged = null;

            holder.editText.setAdapter(new NonFilterableArrayAdapter(holder.editText.getContext(), R.layout.list_item, input.selections));
            holder.editText.setText(input.value);
            holder.layout.setHint(input.title);
            holder.layout.setHelperText(input.helperText);
            holder.layout.setContentDescription(input.title);

            holder.onTextChanged = () -> {
                input.value = (holder.editText.getText().toString());
                if (onInputChanged != null) {
                    onInputChanged.accept(input.key, input.value);
                }
            };
        }

        if (h instanceof ButtonViewHolder) {
            ButtonInput input = ((ButtonInput) inputs.get(position));
            ButtonViewHolder holder = (ButtonViewHolder) h;
            holder.btn.setOnClickListener(v -> {
                input.runnable.run();
            });
            holder.btn.setText(input.title);
            holder.btn.setError(null);
            if (!input.validated) {
                holder.btn.setError("Invalid input");
            }
        }

        if (h instanceof TitleViewHolder) {
            ((TitleViewHolder) h).textView.setText(inputs.get(position).title);
        }

    }

    @Override
    public String getValue(String key) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(key)) {
                if (inputs.get(i) instanceof TextInput) {
                    return ((TextInput) inputs.get(i)).value;
                } else if (inputs.get(i) instanceof SelectInput) {
                    return ((SelectInput) inputs.get(i)).value;
                } else if (inputs.get(i) instanceof TextAreaInput) {
                    return ((TextAreaInput) inputs.get(i)).value;
                }
                return "";
            }
        }
        return "";
    }

    @Override
    public boolean exists(String key) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setValue(String key, String value) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(key)) {
                if (inputs.get(i) instanceof TextInput) {
                    ((TextInput) inputs.get(i)).value = value;
                    if (inputs.get(i) instanceof TextInputGen) {
                        ((TextInputGen) inputs.get(i)).onTextChanged.run();
                    }
                } else if (inputs.get(i) instanceof SelectInput) {
                    ((SelectInput) inputs.get(i)).value = value;
                } else if (inputs.get(i) instanceof TextAreaInput) {
                    ((TextAreaInput) inputs.get(i)).value = value;
                }
                if (onInputChanged != null) onInputChanged.accept(key, value);
            }
        }
    }

    @Override
    public void notifyValueChanged(String key) {
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).key.equals(key)) {
                this.notifyItemChanged(i);
            }
        }
    }

    @Override
    public int getItemCount() {
        return inputs.size();
    }


    @Override
    public ArrayList<Input> getInputs() {
        return inputs;
    }

    public static class DropdownViewHolder extends RecyclerView.ViewHolder implements TextWatcher {
        private final TextInputLayout layout;
        private final AutoCompleteTextView editText;
        private Runnable onTextChanged;

        public DropdownViewHolder(@NonNull View itemView) {
            super(itemView);

            layout = itemView.findViewById(R.id.layout);
            editText = itemView.findViewById(R.id.edittext);
            editText.addTextChangedListener(this);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (onTextChanged != null) {
                onTextChanged.run();
            }
        }
    }

    public static class ButtonViewHolder extends RecyclerView.ViewHolder {

        private final MaterialButton btn;

        public ButtonViewHolder(@NonNull View itemView) {
            super(itemView);
            btn = itemView.findViewById(R.id.btn);
        }

    }

    public static class TitleViewHolder extends RecyclerView.ViewHolder {

        private final AppCompatTextView textView;

        public TitleViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.textview);
        }

    }

    public static class EditTextViewHolder extends RecyclerView.ViewHolder implements TextWatcher {

        private final TextInputLayout layout;
        private final TextInputEditText editText;
        private Runnable onTextChanged;

        public EditTextViewHolder(@NonNull View itemView) {
            super(itemView);

            layout = itemView.findViewById(R.id.layout);
            editText = itemView.findViewById(R.id.edittext);

            editText.addTextChangedListener(this);
        }


        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (onTextChanged != null) {
                onTextChanged.run();
            }
        }

    }

    public static class EditTextMultilineViewHolder extends RecyclerView.ViewHolder implements TextWatcher {

        private final TextInputLayout layout;
        private final TextInputEditText editText;
        private Runnable onTextChanged;

        public EditTextMultilineViewHolder(@NonNull View itemView) {
            super(itemView);

            layout = itemView.findViewById(R.id.layout);
            editText = itemView.findViewById(R.id.edittext);

            editText.addTextChangedListener(this);
        }


        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (onTextChanged != null) {
                onTextChanged.run();
            }
        }

    }

    public static class Input {
        protected final String key;
        protected final String title;
        protected final String helperText;
        protected boolean validated = true;

        public Input(String key, String title, String helperText) {
            this.key = key;
            this.title = title;
            this.helperText = helperText;
        }
    }

    public static class TitleInput extends Input {
        public TitleInput(String key, String title) {
            super(key, title, "");
        }
    }

    public static class TextInput extends Input {

        protected String value = "";
        protected boolean readonly = false;

        public TextInput(String key, String title, String helperText) {
            super(key, title, helperText);
        }

        public TextInput(String key, String title, String helperText, String defaultValue) {
            super(key, title, helperText);
            this.value = defaultValue;
        }

        public TextInput(String key, String title, String helperText, boolean readonly) {
            super(key, title, helperText);
            this.readonly = readonly;
        }
    }

    public static class TextAreaInput extends Input {

        protected String value = "";

        public TextAreaInput(String key, String title, String helperText) {
            super(key, title, helperText);
        }

    }

    public static class TextInputGen extends TextInput {
        protected View.OnClickListener onClickListener;
        protected Runnable onTextChanged;

        public TextInputGen(String key, String title, String helperText, View.OnClickListener onClickListener, Runnable onTextChanged) {
            super(key, title, helperText);
            this.onClickListener = onClickListener;
            this.onTextChanged = onTextChanged;
        }
    }

    public static class ButtonInput extends Input {
        private final Runnable runnable;

        public ButtonInput(String key, String title, String helperText, Runnable runnable) {
            super(key, title, helperText);
            if (runnable == null) {
                throw new NullPointerException("ButtonInput null runnable");
            }
            this.runnable = runnable;
        }

    }

    public static class SelectInput extends Input {

        protected final List<String> selections;
        protected String value;

        public SelectInput(String key, String title, String helperText, List<String> selections) {
            super(key, title, helperText);
            this.selections = selections;
            if (!selections.isEmpty()) value = selections.get(0);
        }

    }
}
