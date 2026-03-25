package lk.punsisi.medifindtest.api;

import java.util.List;

import lk.punsisi.medifindtest.model.Notice;
import retrofit2.Call;
import retrofit2.http.GET;

public interface NoticeApiService {

    @GET("api/notice")
    Call<List<Notice>> getActiveNotices();
}
