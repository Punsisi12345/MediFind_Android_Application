package lk.punsisi.medifindtest.api;

import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface NotificationApi {
    // This matches the @RequestParam in your Spring Boot controller
    @POST("api/notifications/send")
    Call<String> sendNotification(
            @Query("targetToken") String targetToken,
            @Query("title") String title,
            @Query("body") String body
    );
}
