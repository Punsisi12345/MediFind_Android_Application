package lk.punsisi.medifindtest.helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.room.AppDatabase;

public class CartSyncWorker extends Worker {

    public CartSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return Result.failure(); // Stop if not logged in
        }

        String userId = currentUser.getUid();
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // 1. Get all items that failed to upload earlier
        List<CartItem> unsyncedItems = db.cartDao().getUnsyncedItems();

        if (unsyncedItems.isEmpty()) {
            return Result.success(); // Nothing to sync!
        }

        try {
            for (CartItem item : unsyncedItems) {

                if (item.isDeleted()) {
                    // It's a pending DELETION!
                    Tasks.await(firestore.collection("users")
                            .document(userId)
                            .collection("cart")
                            .document(item.getMedicineId())
                            .delete());

                    // Now that Firebase knows it's deleted, we can permanently remove it from the phone
                    db.cartDao().delete(item);
                    Log.d("CartSyncWorker", "Successfully synced DELETION: " + item.getName());

                } else {
                    // It's a pending UPDATE or INSERT!
                    Tasks.await(firestore.collection("users")
                            .document(userId)
                            .collection("cart")
                            .document(item.getMedicineId())
                            .set(item));

                    item.setSynced(true);
                    db.cartDao().update(item);
                    Log.d("CartSyncWorker", "Successfully synced UPDATE: " + item.getName());
                }
            }
            return Result.success();

        } catch (Exception e) {
            Log.e("CartSyncWorker", "Sync failed, will retry later", e);
            return Result.retry();
        }
    }
}