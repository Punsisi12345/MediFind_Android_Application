package lk.punsisi.medifindtest.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import lk.punsisi.medifindtest.model.Notice;

@Dao
public interface NoticeDao {
    //get all
    @Query("SELECT * FROM notices")
    List<Notice> getAllNoticesLocally();

    //save new notices
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Notice> notices);

    //delete
    @Query("DELETE FROM notices")
    void deleteAllNotices();
}
