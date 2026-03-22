package lk.punsisi.medifindtest.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.model.Category;
import lk.punsisi.medifindtest.model.Medicine; // Import Medicine!

// 1 & 2. Add Medicine.class and change version to 2
@Database(entities = {Category.class, Medicine.class, CartItem.class}, version = 8, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract CategoryDao categoryDao();

    // Add the new Medicine DAO
    public abstract MedicineDao medicineDao();

    public abstract CartDao cartDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "medifind_offline_database")
                            // 3. Add this line so the app doesn't crash when you run it!
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}