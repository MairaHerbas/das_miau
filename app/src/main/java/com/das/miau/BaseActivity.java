package com.das.miau;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public abstract class BaseActivity extends AppCompatActivity {

    protected PreferencesManager prefManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        prefManager = new PreferencesManager(this);
        prefManager.applySettings(this);
        setTheme(prefManager.getThemeResource());
        super.onCreate(savedInstanceState);
    }

    protected void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.main_menu);

            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_settings) {
                    showSettingsDialog();
                    return true;
                }
                return false;
            });

            if (toolbar.getTitle() == null || toolbar.getTitle().toString().isEmpty()) {
                toolbar.setTitle(R.string.app_name);
            }
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(view);

        RadioGroup rgLanguage = view.findViewById(R.id.rgLanguage);
        RadioGroup rgThemeMode = view.findViewById(R.id.rgThemeMode);
        RadioGroup rgColor = view.findViewById(R.id.rgColor);

        if (prefManager.getLanguage().equals("es")) {
            ((RadioButton) view.findViewById(R.id.rbSpanish)).setChecked(true);
        } else {
            ((RadioButton) view.findViewById(R.id.rbEnglish)).setChecked(true);
        }

        if (prefManager.getThemeMode().equals("light")) {
            ((RadioButton) view.findViewById(R.id.rbLight)).setChecked(true);
        } else {
            ((RadioButton) view.findViewById(R.id.rbDark)).setChecked(true);
        }

        String currentColor = prefManager.getPrimaryColor();
        switch (currentColor) {
            case "green": ((RadioButton) view.findViewById(R.id.rbGreen)).setChecked(true); break;
            case "red": ((RadioButton) view.findViewById(R.id.rbRed)).setChecked(true); break;
            case "pink": ((RadioButton) view.findViewById(R.id.rbPink)).setChecked(true); break;
            case "purple": ((RadioButton) view.findViewById(R.id.rbPurple)).setChecked(true); break;
            default: ((RadioButton) view.findViewById(R.id.rbBlue)).setChecked(true); break;
        }

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            // Guardar idioma
            if (rgLanguage.getCheckedRadioButtonId() == R.id.rbSpanish) {
                prefManager.setLanguage("es");
            } else {
                prefManager.setLanguage("en");
            }

            // Guardar modo tema
            if (rgThemeMode.getCheckedRadioButtonId() == R.id.rbLight) {
                prefManager.setThemeMode("light");
            } else {
                prefManager.setThemeMode("dark");
            }

            // Guardar color
            int colorId = rgColor.getCheckedRadioButtonId();
            if (colorId == R.id.rbGreen) prefManager.setPrimaryColor("green");
            else if (colorId == R.id.rbRed) prefManager.setPrimaryColor("red");
            else if (colorId == R.id.rbPink) prefManager.setPrimaryColor("pink");
            else if (colorId == R.id.rbPurple) prefManager.setPrimaryColor("purple");
            else prefManager.setPrimaryColor("blue");

            // Aplicar configuración globalmente antes de recrear
            prefManager.applySettings(this);
            
            // Reiniciar actividad para aplicar cambios visuales
            recreate();
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
}