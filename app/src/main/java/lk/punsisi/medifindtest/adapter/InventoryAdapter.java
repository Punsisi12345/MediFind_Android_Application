package lk.punsisi.medifindtest.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.Medicine;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder> {

    private Context context;
    private List<Medicine> medicineList = new ArrayList<>();
    private OnInventoryItemClickListener listener;

    // 1. The Callback Interface
    public interface OnInventoryItemClickListener {
        void onEditClick(Medicine medicine);
        void onDeleteClick(Medicine medicine);
    }

    public InventoryAdapter(Context context, OnInventoryItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setMedicineList(List<Medicine> medicineList) {
        this.medicineList = medicineList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public InventoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_inventory_medicine, parent, false);
        return new InventoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InventoryViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);

        // Set basic text details
        holder.tvName.setText(medicine.getName());
        holder.tvCategory.setText(medicine.getCategoryName());
        holder.tvPrice.setText(String.format("Rs. %.2f", medicine.getPrice()));

        holder.ivImage.setImageTintList(null);

        // Load Image using Glide
        Glide.with(context)
                .load(medicine.getImageUrl())
                .placeholder(R.drawable.baseline_camera_alt_24)
                .centerCrop()
                .into(holder.ivImage);

        // 2. Dynamic Stock Badge Logic
        int qty = medicine.getQuantity();
        if (qty <= 0) {
            // OUT OF STOCK (Red)
            holder.tvStockStatus.setText("Out of Stock");
            holder.tvStockStatus.setTextColor(Color.parseColor("#93000A")); // md_theme_onErrorContainer
            holder.cardStockBadge.setCardBackgroundColor(Color.parseColor("#FFDAD6")); // md_theme_errorContainer
        } else if (qty <= 10) {
            // LOW STOCK (Orange/Warning)
            holder.tvStockStatus.setText("Low Stock: " + qty);
            holder.tvStockStatus.setTextColor(Color.parseColor("#7D5260"));
            holder.cardStockBadge.setCardBackgroundColor(Color.parseColor("#FFD8E4"));
        } else {
            // IN STOCK (Green - Your Primary Colors)
            holder.tvStockStatus.setText("In Stock: " + qty);
            holder.tvStockStatus.setTextColor(Color.parseColor("#005048")); // md_theme_onPrimaryContainer
            holder.cardStockBadge.setCardBackgroundColor(Color.parseColor("#9EF2E3")); // md_theme_primaryContainer
        }

        // 1. Package the Bottom Sheet logic into a reusable click listener
        View.OnClickListener showOptionsListener = v -> {
            com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(context);

            View sheetView = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_inventory_options, null);
            bottomSheetDialog.setContentView(sheetView);

            TextView tvTitle = sheetView.findViewById(R.id.tv_options_title);
            tvTitle.setText("Options for " + medicine.getName());

            com.google.android.material.card.MaterialCardView cardEdit = sheetView.findViewById(R.id.card_edit_item);
            com.google.android.material.card.MaterialCardView cardDelete = sheetView.findViewById(R.id.card_delete_item);

            cardEdit.setOnClickListener(view -> {
                bottomSheetDialog.dismiss();
                listener.onEditClick(medicine);
            });

            cardDelete.setOnClickListener(view -> {
                bottomSheetDialog.dismiss();
                listener.onDeleteClick(medicine);
            });

            bottomSheetDialog.show();
        };

        // 2. Attach the exact same listener to BOTH the 3-dot button AND the whole card!
        holder.btnOptions.setOnClickListener(showOptionsListener);
        holder.itemView.setOnClickListener(showOptionsListener);
    }

    @Override
    public int getItemCount() {
        return medicineList.size();
    }

    // ViewHolder Class
    public static class InventoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCategory, tvPrice, tvStockStatus;
        ImageView ivImage;
        MaterialCardView cardStockBadge;
        ImageButton btnOptions;

        public InventoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvCategory = itemView.findViewById(R.id.tv_item_category);
            tvPrice = itemView.findViewById(R.id.tv_item_price);
            tvStockStatus = itemView.findViewById(R.id.tv_stock_status);
            ivImage = itemView.findViewById(R.id.iv_item_image);
            cardStockBadge = itemView.findViewById(R.id.card_stock_badge);
            btnOptions = itemView.findViewById(R.id.btn_item_options);
        }
    }
}