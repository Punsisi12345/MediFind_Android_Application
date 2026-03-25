package lk.punsisi.medifindtest.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(tableName = "notices")
public class Notice {

    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String description;
    private String imageUrl;
}
