package lk.punsisi.medifindtest.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import lk.punsisi.medifindtest.model.Medicine;

@Dao
public interface MedicineDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMedicines(List<Medicine> medicines);


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMedicine(Medicine medicine);

    @Update
    void updateMedicine(Medicine medicine);


    @Query("SELECT * FROM medicines WHERE deleted = 0 ORDER BY salesCount DESC LIMIT 10")
    List<Medicine> getTopSellingMedicines();

    @Query("SELECT MAX(lastUpdated) FROM medicines")
    long getLatestTimestamp();

    @Query("SELECT * FROM medicines WHERE categoryId = :categoryId AND deleted = 0")
    List<Medicine> getMedicinesByCategory(String categoryId);

    @Query("SELECT * FROM medicines WHERE id = :medicineId LIMIT 1")
    Medicine getMedicineById(String medicineId);

    @Query("SELECT * FROM medicines")
    List<Medicine> getAllMedicines();

    @Query("SELECT * FROM medicines WHERE deleted = 0")
    List<Medicine> getAllActiveMedicines();

    @Query("SELECT * FROM medicines WHERE pharmacistId = :uid AND deleted = 0 ORDER BY name ASC")
    List<Medicine> getMyInventory(String uid);



}