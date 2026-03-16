package lk.punsisi.medifindtest.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import lk.punsisi.medifindtest.model.CartItem;

@Dao
public interface CartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CartItem cartItem);

    @Update
    void update(CartItem cartItem);

    @Delete
    void delete(CartItem cartItem);

    @Query("SELECT * FROM cart_items WHERE isDeleted = 0")
    List<CartItem> getActiveCartItems();

    // Check if item is already in cart so we just increase quantity instead of duplicating!
    @Query("SELECT * FROM cart_items WHERE medicineId = :medId LIMIT 1")
    CartItem getCartItemByMedicineId(String medId);

    // Get ONLY the items that failed to upload to Firebase
    @Query("SELECT * FROM cart_items WHERE isSynced = 0")
    List<CartItem> getUnsyncedItems();

    // Empty the cart after successful checkout
    @Query("DELETE FROM cart_items")
    void clearCart();

    @Query("SELECT COUNT(*) FROM cart_items WHERE requiresPrescription = 1")
    int checkPrescriptionRequirement();

    @Query("SELECT name FROM cart_items WHERE requiresPrescription = 1")
    List<String> getRestrictedItemNames();


}