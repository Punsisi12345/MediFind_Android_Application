package lk.punsisi.medifindtest.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import lk.punsisi.medifindtest.R;

public class OrderSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_success);

        // 1. Grab the Order ID passed from Checkout
        String orderId = getIntent().getStringExtra("FINAL_ORDER_ID");

        // 2. Display it
        TextView tvOrderId = findViewById(R.id.tv_order_id_display);
        if (orderId != null) {
            tvOrderId.setText("Order ID: #" + orderId);
        }

        // 3. Back to Home Button
        MaterialButton btnHome = findViewById(R.id.btn_back_to_home);
        btnHome.setOnClickListener(v -> {
            // This special Intent clears the entire backstack so they start fresh at Home!
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // 4. View Orders Button (We will link this up in the next phase!)
        MaterialButton btnOrders = findViewById(R.id.btn_view_orders);
        btnOrders.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderHistoryActivity.class);
            startActivity(intent);
            finish(); // Close the success screen behind it
        });


        // MODERN SECURITY: Disable the physical back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Leave this completely empty!
                // When the user swipes back or presses the back button, it does absolutely nothing.
            }
        });
    }


}