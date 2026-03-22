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
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.Category;

public class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ChipViewHolder> {

    private Context context;
    private List<Category> categoryList;
    private int selectedPosition = 0;
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

        if (position == selectedPosition) {

            holder.tvChipName.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            holder.tvChipName.setTextColor(Color.WHITE);
        } else {

            holder.tvChipName.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5F5F5")));
            holder.tvChipName.setTextColor(Color.BLACK);
        }


        holder.itemView.setOnClickListener(v -> {

            int previousPosition = selectedPosition;
            selectedPosition = position;

            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

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