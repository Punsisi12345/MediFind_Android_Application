package lk.punsisi.medifindtest.adapter;


import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.MedicinesListActivity;
import lk.punsisi.medifindtest.databinding.CategoryItemLargeBinding;
import lk.punsisi.medifindtest.model.Category;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    private Context context;
    private List<Category> categoryList;
    private boolean isLargeLayout;

    private CategoryItemLargeBinding itemLargeBinding;

    public CategoryAdapter(Context context, List<Category> categoryList, boolean isLargeLayout) {
        this.context = context;
        this.categoryList = categoryList;
        this.isLargeLayout = isLargeLayout;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;

        if (isLargeLayout){
            view = LayoutInflater.from(context).inflate(R.layout.category_item_large, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.category_item, parent, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Category category = categoryList.get(position);
        holder.categoryName.setText(category.getName());

        Glide.with(context)
                .load(category.getImageUrl())
                .centerCrop()
                .placeholder(R.drawable.baseline_search_24)
                .into(holder.categoryImage);

        if (holder.categoryDescription != null) {
            String descText = category.getDescription();
            holder.categoryDescription.setText(descText);
            Log.d("CategoryAdapter", "Category: " + category.getName() + " | Desc: " + descText);
        }else{
            Log.d("CategoryAdapter", "categoryDescription is null");
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, MedicinesListActivity.class);
            intent.putExtra("CATEGORY_ID", category.getId());
            intent.putExtra("CATEGORY_NAME", category.getName());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView categoryImage;
        TextView categoryName;
        TextView categoryDescription;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryImage = itemView.findViewById(R.id.category_image);
            categoryName = itemView.findViewById(R.id.category_name);
            categoryDescription = itemView.findViewById(R.id.category_desc);
        }

    }
}


