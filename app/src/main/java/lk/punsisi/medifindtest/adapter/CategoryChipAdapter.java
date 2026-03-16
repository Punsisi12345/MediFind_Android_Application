package lk.punsisi.medifindtest.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.Category;

public class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ChipViewHolder> {

    private Context context;
    private List<Category> categoryList;
    private int selectedPosition = 0; // "All" will be index 0, so it starts selected!
    private OnChipClickListener listener;

    public interface OnChipClickListener {
        void onChipClick(Category category);
    }

    public CategoryChipAdapter(Context context, List<Category> categoryList, OnChipClickListener listener) {
        this.context = context;
        this.categoryList = categoryList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        return new ChipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChipViewHolder holder, @SuppressLint("RecyclerView") int position) {
        Category category = categoryList.get(position);
        holder.tvChipName.setText(category.getName());

        // ==========================================
        // UI MAGIC: Recolor based on selection!
        // ==========================================
        if (position == selectedPosition) {
            // SELECTED: Primary Green Background, White Text
            holder.tvChipName.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Replace with your primary color hex if needed
            holder.tvChipName.setTextColor(Color.WHITE);
        } else {
            // UNSELECTED: Light Grey Background, Black Text
            holder.tvChipName.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5F5F5")));
            holder.tvChipName.setTextColor(Color.BLACK);
        }

        // Handle Click
        holder.itemView.setOnClickListener(v -> {
            // Update the selected position
            int previousPosition = selectedPosition;
            selectedPosition = position; // Needs to be exact position from adapter

            // Tell the RecyclerView to redraw ONLY the two chips that changed colors (saves battery!)
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

            // Pass the category back to HomeFragment
            listener.onChipClick(category);
        });
    }

    @Override
    public int getItemCount() {
        return categoryList != null ? categoryList.size() : 0;
    }

    public static class ChipViewHolder extends RecyclerView.ViewHolder {
        TextView tvChipName;

        public ChipViewHolder(@NonNull View itemView) {
            super(itemView);
            tvChipName = itemView.findViewById(R.id.tv_chip_name);
        }
    }
}