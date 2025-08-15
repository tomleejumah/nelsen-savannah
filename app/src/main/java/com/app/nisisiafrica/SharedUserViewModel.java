package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.Model.UserData;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

//todo fix the loic issue of data accross
public class SharedUserViewModel  extends AndroidViewModel {
    private static final String TAG = "SharedUserViewModel";
    private UserDao userDao;
    private final MutableLiveData<UserData> userData = new MutableLiveData<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    public SharedUserViewModel(@NonNull Application application) {
        super(application);
        userDao = App.getUserDao();
    }

    public void setUserData(UserData data) {
        Log.d(TAG, "setUserData: "+userData.toString());
        userData.setValue(data);
    }

    @SuppressLint("CheckResult")
    public LiveData<UserData> getUserData() {

        userDao.getAllUsersRx().subscribe(
                users -> Log.d(TAG, "Users found: " + users),
                error -> Log.e(TAG, "Error: ", error),
                () -> Log.d(TAG, "No users found") // onComplete
        );

        userDao.getUserByIdRx(Utils.getState("UserID", ""))
                .subscribe(
                        user -> Log.d(TAG, "User found: " + user),
                        error -> Log.e(TAG, "Error: ", error),
                        () -> Log.d(TAG, "No user found") // onComplete
                );
//        disposables.add(
//                userDao.getUserByIdRx(Utils.getState("UserID", ""))
//                        .subscribeOn(Schedulers.io())
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .subscribe(
//                                user -> {
//                                    Log.d(TAG, "getUserData: " + user);
//                                    userData.setValue(user);
//                                },
//                                throwable -> Log.e(TAG, "Error fetching user data", throwable)
//                        )
//        );
        return userData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
