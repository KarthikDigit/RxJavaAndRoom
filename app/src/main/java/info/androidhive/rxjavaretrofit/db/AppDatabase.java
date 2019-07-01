package info.androidhive.rxjavaretrofit.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.widget.FrameLayout;

import java.util.List;
import java.util.concurrent.Callable;

import info.androidhive.rxjavaretrofit.network.model.Note;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

@Database(entities = {Note.class}, version =3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "userDatabase.db";
    private static AppDatabase INSTANCE;

    public static synchronized AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = create(context);
        }
        return INSTANCE;
    }

    private static AppDatabase create(final Context context) {
        return Room.databaseBuilder(
                context,
                AppDatabase.class,
                DB_NAME).fallbackToDestructiveMigration().build();
    }

    public abstract NoteDao getNoteDao();


    public Single<List<Note>> getUpdateNotes(final AppDatabase appDatabase) {

        return Single.fromCallable(new Callable<List<Note>>() {
            @Override
            public List<Note> call() throws Exception {
                return appDatabase.getNoteDao().getNotUpdatedListNotes(1);
            }
        });

    }

    public Single<List<Note>> getNotes(final AppDatabase appDatabase) {

        return Single.fromCallable(new Callable<List<Note>>() {
            @Override
            public List<Note> call() throws Exception {
                return appDatabase.getNoteDao().getAllNotes();
            }
        });

    }

    public Single<List<Long>> insertNotes(final AppDatabase appDatabase, final List<Note> notes) {

        return Single.fromCallable(new Callable<List<Long>>() {
            @Override
            public List<Long> call() throws Exception {
                return appDatabase.getNoteDao().insert(notes);
            }
        });
    }

    public Single<Long> insertNotes(final AppDatabase appDatabase, final Note notes) {

        return Single.fromCallable(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return appDatabase.getNoteDao().insert(notes);
            }
        });
    }

    public Single<Note> getNote(final AppDatabase appDatabase, final long id) {

        return Single.fromCallable(new Callable<Note>() {
            @Override
            public Note call() throws Exception {
                return appDatabase.getNoteDao().getNote(id);
            }
        });
    }

    public Single<Boolean> updateNote(final AppDatabase appDatabase, final Note note) {

        return Single.fromCallable(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                appDatabase.getNoteDao().update(note);
                return true;
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Single<Boolean> deleteNote(final AppDatabase appDatabase, final Note note) {

        return Single.fromCallable(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                appDatabase.getNoteDao().delete(note);
                return true;
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }
}
