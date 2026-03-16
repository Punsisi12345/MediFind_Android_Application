package lk.punsisi.medifindtest.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity(tableName = "cart_items")
public class CartItem {

    @PrimaryKey(autoGenerate = true)
    private int localId; // Room needs a local ID

    private String medicineId;
    private String name;
    private double price;
    private int quantity;
    private String imageUrl;
    private int maxStock;

    private boolean requiresPrescription;

    // The magic flag for Offline-First!
    private boolean isSynced;
    private boolean isDeleted;

    public CartItem(String medicineId, String name, double price, int quantity, String imageUrl, int maxStock, boolean isSynced, boolean isDeleted) {
        this.medicineId = medicineId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.maxStock = maxStock;
        this.isSynced = isSynced;
        this.isDeleted = isDeleted;
    }

}
