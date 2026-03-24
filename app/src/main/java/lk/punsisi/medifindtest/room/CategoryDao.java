package lk.punsisi.medifindtest.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import lk.punsisi.medifindtest.model.Category;


@Dao
public interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCategories(List<Category> categories);


    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name ASC")
    List<Category> getAllCategories();

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name ASC LIMIT 4")
    List<Category> getActiveCategories();

    @Query("SELECT MAX(lastUpdated) FROM categories")
    long getLatestTimestamp();


}
