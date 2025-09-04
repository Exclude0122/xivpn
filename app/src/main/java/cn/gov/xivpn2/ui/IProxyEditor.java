package cn.gov.xivpn2.ui;

import java.util.List;
import java.util.function.BiConsumer;

public interface IProxyEditor {
    void setOnInputChangedListener(BiConsumer<String, String> onInputChanged);

    void addInput(ProxyEditTextAdapter.Input input);

    void addInputAfter(String key, ProxyEditTextAdapter.Input input);

    /**
     * Add text input
     */
    void addInput(String key, String label);

    void addGroupTitle(String key, String label);

    /**
     * Add text input with default value
     */
    void addInput(String key, String label, String defaultValue);

    /**
     * Add text input with default value and helper text
     */
    void addInput(String key, String label, String defaultValue, String helperText);

    /**
     * Add dropdown
     */
    void addInput(String key, String label, List<String> selections);

    /**
     * Add text input
     */
    void addInputAfter(String after, String key, String label);

    /**
     * Add button
     */
    void addInputAfter(String after, String key, String label, Runnable onClick);

    /**
     * Add text input with default value
     */
    void addInputAfter(String after, String key, String label, String defaultValue);

    /**
     * Add text input with default value and helper text
     */
    void addInputAfter(String after, String key, String label, String defaultValue, String helperText);

    /**
     * Add dropdown
     */
    void addInputAfter(String after, String key, String label, List<String> selections);

    /**
     * Remove input by name
     */
    void removeInput(String key);

    /**
     * Remove inputs by prefix
     */
    void removeInputByPrefix(String prefix);

    /**
     * Return value for given input.
     * Return empty string if the input does not exist.
     */
    String getValue(String key);

    /**
     * Return whether the input exists
     */
    boolean exists(String key);

    void setValue(String key, String value);

    void notifyValueChanged(String key);

    List<ProxyEditTextAdapter.Input> getInputs();
}
