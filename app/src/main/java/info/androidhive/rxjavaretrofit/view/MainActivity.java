package info.androidhive.rxjavaretrofit.view;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;

import org.json.JSONException;
import org.json.JSONObject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import info.androidhive.rxjavaretrofit.R;
import info.androidhive.rxjavaretrofit.db.AppDatabase;
import info.androidhive.rxjavaretrofit.network.ApiClient;
import info.androidhive.rxjavaretrofit.network.ApiService;
import info.androidhive.rxjavaretrofit.network.model.Note;
import info.androidhive.rxjavaretrofit.network.model.User;
import info.androidhive.rxjavaretrofit.utils.MyDividerItemDecoration;
import info.androidhive.rxjavaretrofit.utils.PrefUtils;
import info.androidhive.rxjavaretrofit.utils.RecyclerTouchListener;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.observers.FutureSingleObserver;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DefaultSubscriber;
import io.reactivex.subscribers.DisposableSubscriber;
import io.reactivex.subscribers.SafeSubscriber;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private ApiService apiService;
    private CompositeDisposable disposable = new CompositeDisposable();
    private NotesAdapter mAdapter;
    private List<Note> notesList = new ArrayList<>();

    @BindView(R.id.coordinator_layout)
    CoordinatorLayout coordinatorLayout;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.txt_empty_notes_view)
    TextView noNotesView;

    private AppDatabase appDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        appDatabase = AppDatabase.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.activity_title_home));
        setSupportActionBar(toolbar);


        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNoteDialog(false, null, -1);
            }
        });

        // white background notification bar
        whiteNotificationBar(fab);

        apiService = ApiClient.getClient(getApplicationContext()).create(ApiService.class);

        mAdapter = new NotesAdapter(this, notesList);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new MyDividerItemDecoration(this, LinearLayoutManager.VERTICAL, 16));
        recyclerView.setAdapter(mAdapter);

        /**
         * On long press on RecyclerView item, open alert dialog
         * with options to choose
         * Edit and Delete
         * */
        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this,
                recyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, final int position) {
            }

            @Override
            public void onLongClick(View view, int position) {
                showActionsDialog(position);
            }
        }));

        /**
         * Check for stored Api Key in shared preferences
         * If not present, make api call to register the user
         * This will be executed when app is installed for the first time
         * or data is cleared from settings
         * */
        if (TextUtils.isEmpty(PrefUtils.getApiKey(this))) {
            registerUser();
        } else {
            // user is already registered, fetch all notes
//            fetchAllNotes();
//            fetecWithMerge();
//            fetechWithConcat();

            fetchLocalDataToUpdateServer();

            fetchWithMergeDelayError();
        }

    }

    /**
     * Registering new user
     * sending unique id as device identification
     * https://developer.android.com/training/articles/user-data-ids.html
     */
    private void registerUser() {
        // unique id to identify the device
        String uniqueId = UUID.randomUUID().toString();

        disposable.add(
                apiService
                        .register(uniqueId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSingleObserver<User>() {
                            @Override
                            public void onSuccess(User user) {
                                // Storing user API Key in preferences
                                PrefUtils.storeApiKey(getApplicationContext(), user.getApiKey());

                                Toast.makeText(getApplicationContext(),
                                        "Device is registered successfully! ApiKey: " + PrefUtils.getApiKey(getApplicationContext()),
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        }));
    }


    private void fetchLocalDataToUpdateServer() {


        appDatabase.getUpdateNotes(appDatabase).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<List<Note>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(List<Note> notes) {


                        Log.e(TAG, "onSuccess: " + notes.size() + "  " + notes.toString());


                        if (notes.size() > 0) {

                            Observable.fromIterable(notes).switchMapSingle(new Function<Note, SingleSource<Note>>() {
                                @Override
                                public SingleSource<Note> apply(Note note) throws Exception {
                                    return apiService.createNote(note.getNote());
                                }
                            })
                                    .doOnNext(new Consumer<Note>() {
                                        @Override
                                        public void accept(Note note) throws Exception {
                                            note.setStatus(0);
                                            appDatabase.updateNote(appDatabase, note).subscribe(new SingleObserver<Boolean>() {
                                                @Override
                                                public void onSubscribe(Disposable d) {

                                                }

                                                @Override
                                                public void onSuccess(Boolean aBoolean) {

                                                }

                                                @Override
                                                public void onError(Throwable e) {

                                                }
                                            });
                                        }
                                    })
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new DisposableObserver<Note>() {
                                        @Override
                                        public void onNext(Note note) {

                                            Log.e(TAG, "onNext:  Updated note " + note.toString());

                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            Log.e(TAG, "onError: " + e.getMessage());
                                        }

                                        @Override
                                        public void onComplete() {

                                        }
                                    });
                        }


                    }

                    @Override
                    public void onError(Throwable e) {

                        Log.e(TAG, "onError: " + e.getMessage());
                    }
                });


    }


    private void fetechWithConcat() {


        Single<List<Note>> localDB = appDatabase.getNotes(appDatabase);

        Single<List<Note>> remoteDB = apiService.fetchAllNotes();


        Single.concat(localDB, remoteDB)

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSubscriber<List<Note>>() {
                    @Override
                    public void onNext(final List<Note> notes) {

                        recyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                notesList.clear();
                                notesList.addAll(notes);
                                mAdapter.notifyDataSetChanged();
                                toggleEmptyNotes();
                            }
                        });


                        Log.e(TAG, "onNext: " + notes.toString());
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "onError: " + t.getMessage());
                    }

                    @Override
                    public void onComplete() {

                    }
                });

    }


    private void fetchWithMergeDelayError() {

//        Single<List<Note>> localDB = appDatabase.getNotes(appDatabase).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
//
//        Single<List<Note>> remoteDB = apiService.fetchAllNotes().doOnSuccess(new Consumer<List<Note>>() {
//            @Override
//            public void accept(List<Note> notes) throws Exception {
//                appDatabase.insertNotes(appDatabase, notes)
//                        .subscribeOn(Schedulers.io())
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .subscribe(new SingleObserver<List<Long>>() {
//                            @Override
//                            public void onSubscribe(Disposable d) {
//
//                            }
//
//                            @Override
//                            public void onSuccess(List<Long> longs) {
//
//                                Log.e(TAG, "onSuccess: ids " + longs.toString());
//                            }
//
//                            @Override
//                            public void onError(Throwable e) {
//
//                            }
//                        });
//
//                Log.e(TAG, "accept: ");
//            }
//        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());


        Single.mergeDelayError(apiService.fetchAllNotes().doOnSuccess(new Consumer<List<Note>>() {
            @Override
            public void accept(List<Note> notes) throws Exception {
                appDatabase.insertNotes(appDatabase, notes)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new SingleObserver<List<Long>>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onSuccess(List<Long> longs) {

                                Log.e(TAG, "onSuccess: ids " + longs.toString());
                            }

                            @Override
                            public void onError(Throwable e) {

                            }
                        });

                Log.e(TAG, "accept: ");
            }
        }).subscribeOn(Schedulers.io()), appDatabase.getNotes(appDatabase).subscribeOn(Schedulers.io()))
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSubscriber<List<Note>>() {

                    @Override
                    public void onNext(final List<Note> notes) {

                        recyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                notesList.clear();
                                notesList.addAll(notes);
                                mAdapter.notifyDataSetChanged();
                                toggleEmptyNotes();
                            }
                        });


                        Log.e(TAG, "onNext: " + notes.toString());
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "onError: " + t.getMessage());
                    }

                    @Override
                    public void onComplete() {

                    }
                });


    }


    private void fetecWithMerge() {


//        Single.mergeDelayError(
//                Single.error(new RuntimeException())
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .subscribeOn(Schedulers.io()),
//                Single.just("Hello")
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .subscribeOn(Schedulers.io())
//        )
//
//                .subscribe(new DisposableSubscriber<Object>() {
//                    @Override
//                    public void onNext(Object o) {
//
//                        Log.e(TAG, "onNext: " + o.toString());
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//
//                        Log.e(TAG, "onError: " + e.getMessage());
//                    }
//
//                    @Override
//                    public void onComplete() {
//
//                    }
//                });


//        Single.mergeDelayError(apiService.fetchAllNotes().doOnSuccess(new Consumer<List<Note>>() {
//            @Override
//            public void accept(List<Note> notes) throws Exception {
//                appDatabase.insertNotes(appDatabase, notes).subscribeOn(Schedulers.io());
//            }
//        }).subscribeOn(Schedulers.io()), appDatabase.getNotes(appDatabase).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()))
//
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread()).subscribe(new DisposableSubscriber<List<Note>>() {
//            @Override
//            public void onNext(List<Note> notes) {
//                notesList.clear();
//                notesList.addAll(notes);
//                mAdapter.notifyDataSetChanged();
//                toggleEmptyNotes();
//            }
//
//            @Override
//            public void onError(Throwable t) {
//
//
//                Log.e(TAG, "onError: " + t.getMessage());
//
//            }
//
//            @Override
//            public void onComplete() {
//
//            }
//        });


        Single.mergeDelayError(apiService.fetchAllNotes().doOnSuccess(new Consumer<List<Note>>() {
                    @Override
                    public void accept(final List<Note> users) throws Exception {

                        appDatabase.insertNotes(appDatabase, users)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new SingleObserver<List<Long>>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onSuccess(List<Long> longs) {

                                        Log.e(TAG, "onSuccess: ids " + longs.toString());
                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }
                                });

                        Log.e(TAG, "accept: ");
//                        appDatabase.insertNotes(appDatabase, users).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
                    }
                }).subscribeOn(Schedulers.io()), appDatabase.getNotes(appDatabase).subscribeOn(Schedulers.io())
        )

                .subscribe(new DisposableSubscriber<List<Note>>() {

                    @Override
                    public void onNext(final List<Note> notes) {

                        recyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                notesList.clear();
                                notesList.addAll(notes);
                                mAdapter.notifyDataSetChanged();
                                toggleEmptyNotes();
                            }
                        });


                        Log.e(TAG, "onNext: " + notes.toString());
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "onError: " + t.getMessage());
                    }

                    @Override
                    public void onComplete() {

                    }
                });


//                .subscribe(new DisposableSubscriber<List<Note>>() {
//                    @Override
//                    public void onNext(List<Note> notes) {
//                        notesList.clear();
//                        notesList.addAll(notes);
//                        mAdapter.notifyDataSetChanged();
//                        toggleEmptyNotes();
//
//                        Log.e(TAG, "onNext: " + notes.toString());
//                    }
//
//                    @Override
//                    public void onError(Throwable t) {
//
//                        Log.e(TAG, "onError: " + t.getMessage());
//                    }
//
//                    @Override
//                    public void onComplete() {
//
//                    }
//                });

    }


    /**
     * Fetching all notes from api
     * The received items will be in random order
     * map() operator is used to sort the items in descending order by Id
     */
    private void fetchAllNotes() {


//        disposable.add(
//                apiService.fetchAllNotes()
//                        .subscribeOn(Schedulers.io())
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .map(new Function<List<Note>, List<Note>>() {
//                            @Override
//                            public List<Note> apply(List<Note> notes) throws Exception {
//                                // TODO - note about sort
//                                Collections.sort(notes, new Comparator<Note>() {
//                                    @Override
//                                    public int compare(Note n1, Note n2) {
//                                        return n2.getId() - n1.getId();
//                                    }
//                                });
//                                return notes;
//                            }
//                        })
//                        .flatMap(new Function<List<Note>, SingleSource<List<Long>>>() {
//                            @Override
//                            public SingleSource<List<Long>> apply(List<Note> notes) throws Exception {
//                                return appDatabase.insertNotes(appDatabase, notes).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
//                            }
//                        })
//                        .flatMap(new Function<List<Long>, SingleSource<List<Note>>>() {
//                            @Override
//                            public SingleSource<List<Note>> apply(List<Long> notes) throws Exception {
//                                return appDatabase.getNotes(appDatabase).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
//                            }
//                        })
//
//                        .subscribeWith(new DisposableSingleObserver<List<Note>>() {
//                            @Override
//                            public void onSuccess(List<Note> notes) {
//                                notesList.clear();
//                                notesList.addAll(notes);
//                                mAdapter.notifyDataSetChanged();
//                                toggleEmptyNotes();
//                            }
//
//                            @Override
//                            public void onError(Throwable e) {
//                                Log.e(TAG, "onError: " + e.getMessage());
//                                showError(e);
//                            }
//                        })
//        );
    }


    /**
     * Creating new note
     */
    private void createNote(final String note) {
        disposable.add(
                apiService.createNote(note)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSuccess(new Consumer<Note>() {
                            @Override
                            public void accept(Note note) throws Exception {
                                note.setStatus(0);
                                appDatabase.insertNotes(appDatabase, note).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new SingleObserver<Long>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onSuccess(Long aLong) {

                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }
                                });
                            }
                        })
                        .doOnError(new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {

                                Note note1 = new Note();
                                note1.setStatus(1);
                                note1.setNote(note);
                                note1.setTimestamp(new Date().toString());

                                appDatabase.insertNotes(appDatabase, note1).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new SingleObserver<Long>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onSuccess(Long aLong) {

                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }
                                });
                            }
                        })
//                        .flatMap(new Function<Note, SingleSource<Long>>() {
//                            @Override
//                            public SingleSource<Long> apply(Note note) throws Exception {
//                                return appDatabase.insertNotes(appDatabase, note).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
//                            }
//                        }).flatMap(new Function<Long, SingleSource<Note>>() {
//                    @Override
//                    public SingleSource<Note> apply(Long aLong) throws Exception {
//                        return appDatabase.getNote(appDatabase, aLong).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
//                    }
//                })
                        .subscribeWith(new DisposableSingleObserver<Note>() {

                            @Override
                            public void onSuccess(Note note) {
                                if (!TextUtils.isEmpty(note.getError())) {
                                    Toast.makeText(getApplicationContext(), note.getError(), Toast.LENGTH_LONG).show();
                                    return;
                                }

                                Log.d(TAG, "new note created: " + note.getId() + ", " + note.getNote() + ", " + note.getTimestamp());

                                // Add new item and notify adapter
                                notesList.add(0, note);
                                mAdapter.notifyItemInserted(0);

                                toggleEmptyNotes();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        }));
    }

    /**
     * Updating a note
     */
    private void updateNote(int noteId, final String note, final int position) {
        disposable.add(
                apiService.updateNote(noteId, note)
                        .doOnComplete(new Action() {
                            @Override
                            public void run() throws Exception {
                                Note n = notesList.get(position);
                                n.setNote(note);

                                appDatabase.updateNote(appDatabase, n).subscribe(new SingleObserver<Boolean>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onSuccess(Boolean aBoolean) {

                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }
                                });
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())

                        .subscribeWith(new DisposableCompletableObserver() {
                            @Override
                            public void onComplete() {
                                Log.d(TAG, "Note updated!");

                                Note n = notesList.get(position);
                                n.setNote(note);


                                // Update item and notify adapter
                                notesList.set(position, n);
                                mAdapter.notifyItemChanged(position);

                                appDatabase.updateNote(appDatabase, n);


                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        }));
    }

    /**
     * Deleting a note
     */
    private void deleteNote(final int noteId, final int position) {
        Log.e(TAG, "deleteNote: " + noteId + ", " + position);
        disposable.add(
                apiService.deleteNote(noteId)
                        .doOnComplete(new Action() {
                            @Override
                            public void run() throws Exception {

                                appDatabase.deleteNote(appDatabase, notesList.get(position)).subscribe(new SingleObserver<Boolean>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onSuccess(Boolean aBoolean) {

                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }
                                });
                            }
                        })
                        .doOnError(new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                appDatabase.deleteNote(appDatabase, notesList.get(position)).subscribe(new SingleObserver<Boolean>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onSuccess(Boolean aBoolean) {

                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }
                                });
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableCompletableObserver() {
                            @Override
                            public void onComplete() {
                                Log.d(TAG, "Note deleted! " + noteId);

                                // Remove and notify adapter about item deletion
                                notesList.remove(position);
                                mAdapter.notifyItemRemoved(position);

                                Toast.makeText(MainActivity.this, "Note deleted!", Toast.LENGTH_SHORT).show();


                                toggleEmptyNotes();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        })
        );
    }

    /**
     * Shows alert dialog with EditText options to enter / edit
     * a note.
     * when shouldUpdate=true, it automatically displays old note and changes the
     * button text to UPDATE
     */
    private void showNoteDialog(final boolean shouldUpdate, final Note note, final int position) {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View view = layoutInflaterAndroid.inflate(R.layout.note_dialog, null);

        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilderUserInput.setView(view);

        final EditText inputNote = view.findViewById(R.id.note);
        TextView dialogTitle = view.findViewById(R.id.dialog_title);
        dialogTitle.setText(!shouldUpdate ? getString(R.string.lbl_new_note_title) : getString(R.string.lbl_edit_note_title));

        if (shouldUpdate && note != null) {
            inputNote.setText(note.getNote());
        }
        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton(shouldUpdate ? "update" : "save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {

                    }
                })
                .setNegativeButton("cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogBox, int id) {
                                dialogBox.cancel();
                            }
                        });

        final AlertDialog alertDialog = alertDialogBuilderUserInput.create();
        alertDialog.show();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show toast message when no text is entered
                if (TextUtils.isEmpty(inputNote.getText().toString())) {
                    Toast.makeText(MainActivity.this, "Enter note!", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    alertDialog.dismiss();
                }

                // check if user updating note
                if (shouldUpdate && note != null) {
                    // update note by it's id
                    updateNote(note.getId(), inputNote.getText().toString(), position);
                } else {
                    // create new note
                    createNote(inputNote.getText().toString());
                }
            }
        });
    }

    /**
     * Opens dialog with Edit - Delete options
     * Edit - 0
     * Delete - 0
     */
    private void showActionsDialog(final int position) {
        CharSequence colors[] = new CharSequence[]{"Edit", "Delete"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose option");
        builder.setItems(colors, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showNoteDialog(true, notesList.get(position), position);
                } else {
                    deleteNote(notesList.get(position).getId(), position);
                }
            }
        });
        builder.show();
    }

    private void toggleEmptyNotes() {
        if (notesList.size() > 0) {
            noNotesView.setVisibility(View.GONE);
        } else {
            noNotesView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Showing a Snackbar with error message
     * The error body will be in json format
     * {"error": "Error message!"}
     */
    private void showError(Throwable e) {
        String message = "";
        try {
            if (e instanceof IOException) {
                message = "No internet connection!";
            } else if (e instanceof HttpException) {
                HttpException error = (HttpException) e;
                String errorBody = error.response().errorBody().string();
                JSONObject jObj = new JSONObject(errorBody);

                message = jObj.getString("error");
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (JSONException e1) {
            e1.printStackTrace();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        if (TextUtils.isEmpty(message)) {
            message = "Unknown error occurred! Check LogCat.";
        }

        Snackbar snackbar = Snackbar
                .make(coordinatorLayout, message, Snackbar.LENGTH_LONG);

        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.YELLOW);
        snackbar.show();
    }

    private void whiteNotificationBar(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = view.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            view.setSystemUiVisibility(flags);
            getWindow().setStatusBarColor(Color.WHITE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposable.dispose();
    }
}
