package lk.punsisi.medifindtest.helper;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.room.AppDatabase;

public class CartHelper {

    public static void addMedicineToCart(Context context, Medicine medicine, int quantityToAdd) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);

            CartItem existingItem = db.cartDao().getCartItemByMedicineId(medicine.getId());
            boolean isOnline = isNetworkAvailable(context);

            int maxStock = 10;
            boolean hitMaxStock = false;

            if (existingItem != null) {

                int newQuantity = existingItem.getQuantity() + quantityToAdd;
                if (newQuantity > maxStock){
                    newQuantity = maxStock;
                    hitMaxStock = true;
                }

                existingItem.setQuantity(newQuantity);
                existingItem.setSynced(isOnline);


                existingItem.setRequiresPrescription(medicine.isRequiresPrescription());

                db.cartDao().update(existingItem);
                scheduleSync(context);
                if (isOnline) pushToFirebase(existingItem);

            } else {

                CartItem newItem = new CartItem(
                        medicine.getId(),
                        medicine.getName(),
                        medicine.getPrice(),
                        quantityToAdd,
                        medicine.getImageUrl(),
                        maxStock,
                        isOnline,
                        false
                );


                newItem.setRequiresPrescription(medicine.isRequiresPrescription());

                db.cartDao().insert(newItem);
                scheduleSync(context);
                if (isOnline) pushToFirebase(newItem);
            }


            final boolean finalHitMaxStock = hitMaxStock;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalHitMaxStock) {
                    Toast.makeText(context, "Max stock reached for single user!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, medicine.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static void pushToFirebase(CartItem item) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("cart")
                .document(item.getMedicineId())
                .set(item)
                .addOnFailureListener(e -> android.util.Log.e("CartSync", "Failed to upload to Firebase", e));
    }

    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
        return netInfo != null && netInfo.isConnected();
    }

    public static void scheduleSync(Context context) {
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        androidx.work.OneTimeWorkRequest syncRequest = new androidx.work.OneTimeWorkRequest.Builder(CartSyncWorker.class)
                .setConstraints(constraints)
                .build();

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("CartSyncJob", androidx.work.ExistingWorkPolicy.REPLACE, syncRequest);
    }
}