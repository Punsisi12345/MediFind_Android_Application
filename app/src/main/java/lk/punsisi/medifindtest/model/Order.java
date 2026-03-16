package lk.punsisi.medifindtest.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

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

    @ServerTimestamp
    private Date timestamp;

    private String prescriptionUrl;
}
