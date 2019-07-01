package info.androidhive.rxjavaretrofit.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

import info.androidhive.rxjavaretrofit.network.model.Note;
import io.reactivex.Flowable;
import io.reactivex.Single;


@Dao
public interface NoteDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Long insert(Note note);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insert(List<Note> notes);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

//    @Query("SELECT * FROM note")
//    Flowable<List<Note>> getAllNotes();

    @Query("SELECT * FROM note")
    List<Note> getAllNotes();

    @Query("SELECT * FROM note WHERE status=:status")
    List<Note> getNotUpdatedListNotes(int status);

    @Query("SELECT * FROM note WHERE _id=:id")
    Note getNote(long id);

}
