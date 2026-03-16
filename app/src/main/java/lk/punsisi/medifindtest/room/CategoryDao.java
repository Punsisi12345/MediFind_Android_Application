package lk.punsisi.medifindtest.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import lk.punsisi.medifindtest.model.Category;


@Dao
public interface CategoryDao {

    // 1. Insert new categories. If the ID already exists, REPLACE it (this handles Updates!)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCategories(List<Category> categories);


    // 2. Get all active categories for the RecyclerView. (SQLite stores true/false as 1/0)
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name ASC")
    List<Category> getAllCategories();

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name ASC LIMIT 4")
    List<Category> getActiveCategories();

    // 3. THE MAGIC QUERY: Find the highest timestamp we currently have saved
    @Query("SELECT MAX(lastUpdated) FROM categories")
    long getLatestTimestamp();


}
