package com.das.miau;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class PreferencesManager {
    private static final String PREF_NAME = "MiauPrefs";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_PRIMARY_COLOR = "primary_color";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public PreferencesManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void setLanguage(String lang) {
        editor.putString(KEY_LANGUAGE, lang);
        editor.apply();
    }

    public String getLanguage() {
        return pref.getString(KEY_LANGUAGE, "es");
    }

    public void setThemeMode(String mode) {
        editor.putString(KEY_THEME_MODE, mode);
        editor.apply();
    }

    public String getThemeMode() {
        return pref.getString(KEY_THEME_MODE, "light");
    }

    public void setPrimaryColor(String color) {
        editor.putString(KEY_PRIMARY_COLOR, color);
        editor.apply();
    }

    public String getPrimaryColor() {
        return pref.getString(KEY_PRIMARY_COLOR, "blue");
    }

    public void applySettings(Context context) {
        // Apply Language
        String lang = getLanguage();
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        context.createConfigurationContext(config);
        res.updateConfiguration(config, res.getDisplayMetrics());

        // Theme is applied via setTheme() in Activity
    }

    public int getThemeResource() {
        String mode = getThemeMode();
        String color = getPrimaryColor();

        if (mode.equals("light")) {
            switch (color) {
                case "green": return R.style.Theme_Miau_Light_Green;
                case "red": return R.style.Theme_Miau_Light_Red;
                case "pink": return R.style.Theme_Miau_Light_Pink;
                case "purple": return R.style.Theme_Miau_Light_Purple;
                default: return R.style.Theme_Miau_Light_Blue;
            }
        } else {
            switch (color) {
                case "green": return R.style.Theme_Miau_Dark_Green;
                case "red": return R.style.Theme_Miau_Dark_Red;
                case "pink": return R.style.Theme_Miau_Dark_Pink;
                case "purple": return R.style.Theme_Miau_Dark_Purple;
                default: return R.style.Theme_Miau_Dark_Blue;
            }
        }
    }
}