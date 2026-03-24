package lk.punsisi.medifindtest.model;

import androidx.annotation.NonNull;
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
@Entity(tableName = "medicines")
public class Medicine {

    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private String description;
    private String categoryId;
    private String categoryName;
    private String dosage;
    private double price;
    private int quantity;
    private String status;
    private String imageUrl;

    private int salesCount;
    private long expiryDate;

    private boolean requiresPrescription;

    private String pharmacistId;
    private String pharmacyName;
    private String source = "pharmacist";

    private long lastUpdated;
    private boolean deleted;
}
