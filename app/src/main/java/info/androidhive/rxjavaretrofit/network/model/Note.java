package info.androidhive.rxjavaretrofit.network.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

/**
 * Created by ravi on 20/02/18.
 */

@Entity(tableName = "note")
public class Note extends BaseResponse {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    int id;

    @ColumnInfo(name = "note_txt")
    String note;

    @ColumnInfo(name = "_time_stamp")
    String timestamp;

    @ColumnInfo(name = "status")
    int status;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Note{" +
                "id=" + id +
                ", note='" + note + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", status=" + status +
                '}';
    }
}
