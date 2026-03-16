package lk.punsisi.medifindtest.model;

import com.google.firebase.firestore.Exclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String uid;
    private String name;
    private String email;
    private String profileImage;
    private DeliveryAddress deliveryAddress;

    @Builder.Default
    private String role = "user";

    @Builder.Default
    private String pharmacistRequestStatus = "none";


    @Exclude
    public String getSafeRole() {
        return (role != null) ? role : "user";
    }

    @Exclude
    public String getSafePharmacistRequestStatus() {
        return (pharmacistRequestStatus != null) ? pharmacistRequestStatus : "none";
    }
}


