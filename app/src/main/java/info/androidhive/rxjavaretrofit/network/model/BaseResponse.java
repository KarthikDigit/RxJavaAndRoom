package info.androidhive.rxjavaretrofit.network.model;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;

/**
 * Created by ravi on 21/02/18.
 */
@Entity
public class BaseResponse {

    @Ignore
    String error;

    public String getError() {
        return error;
    }
}
