package com.app.nisisiafrica;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.nisisiafrica.Model.UserData;
//todo fix the loic issue of data accross
public class SharedUserViewModel  extends AndroidViewModel {
    private static final String TAG = "SharedUserViewModel";
    private final MutableLiveData<UserData> userData = new MutableLiveData<>();

    public SharedUserViewModel(@NonNull Application application) {
        super(application);
    }

    public void setUserData(UserData data) {
        Log.d(TAG, "setUserData: "+userData.toString());
        userData.setValue(data);
    }

    public LiveData<UserData> getUserData()
    {
        Log.d(TAG, "getUserData: ");
        return userData;
    }
}
