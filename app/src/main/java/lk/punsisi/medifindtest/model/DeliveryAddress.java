package lk.punsisi.medifindtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress {

    private String phoneNumber;
    private String addressLine1;
    private String addressLine2;
    private String homeTown;
    private String postalCode;

}
