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
@Entity(tableName = "categories")
public class Category {

    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private String imageUrl;
    private String description;
    private long lastUpdated;
    private boolean deleted;

}
