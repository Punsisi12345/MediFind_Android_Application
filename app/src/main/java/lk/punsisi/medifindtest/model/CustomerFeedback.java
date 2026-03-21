package lk.punsisi.medifindtest.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerFeedback {

    private String customerId;
    private String orderId;
    private String pharmacyId;
    private float rating;
    private String comment;

    // Firebase will automatically fill this with the exact server time when saved!
    @ServerTimestamp
    private Date timestamp;


}
