package lk.punsisi.medifindtest.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    private String orderId;
    private String status;
    private double grandTotal;
    private String prescriptionUrl;
    private String userId;
    private String pharmacyId;
    private String pharmacyName;
    private String deliveryMethod;

    private List<Map<String, Object>> items;
    private DeliveryAddress deliveryAddress;

    @Builder.Default
    private boolean isPaid = false;

    @Builder.Default
    private boolean isReviewed = false;

    @ServerTimestamp
    private Date timestamp;
}
