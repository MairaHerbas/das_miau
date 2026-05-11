package com.das.miau;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

public abstract class BaseActivity extends AppCompatActivity {
    protected PreferencesManager prefManager;
    protected DrawerLayout drawerLayout;

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

            drawerLayout = findViewById(R.id.drawer_layout);
            NavigationView navigationView = findViewById(R.id.nav_view);

            if (drawerLayout != null) {
                ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                        this, drawerLayout, toolbar,
                        R.string.open_nav, R.string.close_nav);
                drawerLayout.addDrawerListener(toggle);
                toggle.syncState();
            }

            if (navigationView != null) {
                navigationView.setNavigationItemSelectedListener(item -> {
                    int itemId = item.getItemId();
                    
                    if(itemId == R.id.nav_inicio) {
                        if (this instanceof MainActivity) {
                            // Si ya estamos en MainActivity, quitamos fragmentos para volver al "Home"
                            View fragmentContainer = findViewById(R.id.fragment_container);
                            View contenidoPrincipal = findViewById(R.id.contenido_principal);
                            if (fragmentContainer != null && contenidoPrincipal != null) {
                                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                                if (fragment != null) {
                                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                                    contenidoPrincipal.setVisibility(View.VISIBLE);
                                    toolbar.setTitle(R.string.app_name);
                                }
                            }
                        } else {
                            // Ir a Inicio y limpiar el stack de actividades anteriores (Transport, Maps, etc)
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                        }
                    }
                    else if (itemId == R.id.nav_perfil) {
                        if (!isUserLoggedIn()) {
                            startActivity(new Intent(this, LoginRegistroActivity.class));
                        } else {
                            if (this instanceof MainActivity) {
                                // Estamos en Main, solo mostramos el fragmento
                                View fragmentContainer = findViewById(R.id.fragment_container);
                                View contenidoPrincipal = findViewById(R.id.contenido_principal);
                                if (fragmentContainer != null && contenidoPrincipal != null) {
                                    contenidoPrincipal.setVisibility(View.GONE);
                                    getSupportFragmentManager().beginTransaction()
                                            .replace(R.id.fragment_container, new PerfilFragment())
                                            .commit();
                                    toolbar.setTitle(getString(R.string.miperfil));
                                }
                            } else {
                                // En otra actividad: vamos a Main y que él abra el perfil, limpiando el stack
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.putExtra("show_profile", true);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                            }
                        }
                    } else if (itemId == R.id.nav_lineas) {
                        if (!(this instanceof BusesActivity)) {
                            if (this instanceof MainActivity) {
                                // Desde Main simplemente abrimos Buses
                                startActivity(new Intent(this, BusesActivity.class));
                            } else {
                                // Desde otra (ej. Maps), pasamos por Main para limpiar el stack y luego abrir Buses
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                intent.putExtra("open_buses", true);
                                startActivity(intent);
                            }
                        }
                    }

                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                    return true;
                });
            }
        }
    }

    protected boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("MisPreferencias", MODE_PRIVATE);
        return prefs.contains("id_usuario");
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
            if (rgLanguage.getCheckedRadioButtonId() == R.id.rbSpanish) prefManager.setLanguage("es");
            else prefManager.setLanguage("en");

            if (rgThemeMode.getCheckedRadioButtonId() == R.id.rbLight) prefManager.setThemeMode("light");
            else prefManager.setThemeMode("dark");

            int colorId = rgColor.getCheckedRadioButtonId();
            if (colorId == R.id.rbGreen) prefManager.setPrimaryColor("green");
            else if (colorId == R.id.rbRed) prefManager.setPrimaryColor("red");
            else if (colorId == R.id.rbPink) prefManager.setPrimaryColor("pink");
            else if (colorId == R.id.rbPurple) prefManager.setPrimaryColor("purple");
            else prefManager.setPrimaryColor("blue");

            prefManager.applySettings(this);
            recreate();
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
}
