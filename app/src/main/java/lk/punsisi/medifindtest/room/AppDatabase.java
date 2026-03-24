package lk.punsisi.medifindtest.room;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.model.Category;
import lk.punsisi.medifindtest.model.Medicine;

@Database(entities = {Category.class, Medicine.class, CartItem.class}, version = 9, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract CategoryDao categoryDao();

    public abstract MedicineDao medicineDao();

    public abstract CartDao cartDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "medifind_offline_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}