package com.app.nisisiafrica.ViewModel;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.nisisiafrica.App;
import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.Model.UserData;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

//todo fix the logic issue of data across
public class SharedUserViewModel extends AndroidViewModel {
    private static final String TAG = "SharedUserViewModel";
    private final MutableLiveData<UserData> userData = new MutableLiveData<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private UserDao userDao;

    public SharedUserViewModel(@NonNull Application application) {
        super(application);
        userDao = App.getUserDao();
    }

    public LiveData<UserData> getUserData() {
        return userData;
    }

    public void setUserData(UserData data) {
        Log.d(TAG, "setUserData: " + userData.toString());
        userData.setValue(data);
    }

    @SuppressLint("CheckResult")
    public LiveData<UserData> fetchingCurrentUserDataFromDB(String id) {

        disposables.add(
                userDao.getUserByIdRx(id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                user -> {
                                    userData.setValue(user);
                                },
                                throwable -> Log.e(TAG, "Error fetching user data", throwable),
                                () -> Log.d(TAG, "No user found for id: " + id) // <-- works if Maybe<>
                        )
        );
        return userData;
    }

    @SuppressLint("CheckResult")
    public void saveUserData(UserData userData) {
        userDao.insertUserRx(userData)
                .subscribe(() -> {
                    Log.d("ViewModel", "User inserted successfully");
                }, throwable -> {
                    Log.e("ViewModel", "Error inserting user", throwable);
                });
    }

    public void updateUserData(UserData userData) {
        disposables.add(
                userDao.updateUserRx(userData)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> Log.d(TAG, "User updated successfully"),
                                throwable -> Log.e(TAG, "Error updating user", throwable)
                        )
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
