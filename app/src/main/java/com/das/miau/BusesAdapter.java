package com.das.miau;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class BusesAdapter extends RecyclerView.Adapter<BusesAdapter.BusViewHolder> {

    private List<RutaBus> listaRutas;

    public BusesAdapter(List<RutaBus> listaRutas) {
        this.listaRutas = listaRutas;
    }

    public void setRutas(List<RutaBus> nuevasRutas) {
        this.listaRutas = nuevasRutas;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //usamos un layout de Android por defecto para ir más rápido
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new BusViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BusViewHolder holder, int position) {
        RutaBus ruta = listaRutas.get(position);
        holder.tvLinea.setText("Línea " + ruta.getNombreCorto());
        holder.tvDestino.setText(ruta.getNombreLargo());
    }

    @Override
    public int getItemCount() {
        return listaRutas.size();
    }

    static class BusViewHolder extends RecyclerView.ViewHolder {
        TextView tvLinea, tvDestino;

        public BusViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLinea = itemView.findViewById(android.R.id.text1);
            tvDestino = itemView.findViewById(android.R.id.text2);
            tvLinea.setTextColor(0xFF000000); //negro
            tvLinea.setTextSize(18f);
            tvLinea.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDestino.setTextColor(0xFF666666); //gris oscuro
        }
    }
}