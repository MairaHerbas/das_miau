package com.das.miau;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DestinosAdapter extends RecyclerView.Adapter<DestinosAdapter.DestinoViewHolder> {

    private List<CentroUniversitario> destinos;
    private OnDestinoClickListener listener;

    public interface OnDestinoClickListener {
        void onDestinoClick(CentroUniversitario centro);
    }

    public DestinosAdapter(List<CentroUniversitario> destinos, OnDestinoClickListener listener) {
        this.destinos = destinos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DestinoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_destino, parent, false);
        return new DestinoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DestinoViewHolder holder, int position) {
        CentroUniversitario centro = destinos.get(position);
        holder.tvNombre.setText(centro.getNombre());
        holder.tvUbicacion.setText(centro.getUbicacion());
        holder.itemView.setOnClickListener(v -> listener.onDestinoClick(centro));
    }

    @Override
    public int getItemCount() {
        return destinos.size();
    }

    static class DestinoViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvUbicacion;

        public DestinoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombreDestino);
            tvUbicacion = itemView.findViewById(R.id.tvUbicacionDestino);
        }
    }
}