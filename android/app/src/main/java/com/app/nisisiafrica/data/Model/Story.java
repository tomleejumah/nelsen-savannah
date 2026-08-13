package com.app.nisisiafrica.data.Model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Corporate advertisement "story". Authored in the Firebase console under the
 * realtime-database node "stories/{id}".
 */
public class Story implements Parcelable {
    public String storyId;
    public String companyName;
    public String logoUrl;
    public String mediaUrl;
    public String caption;
    public String ctaUrl;
    public long timestamp;
    public boolean active = true;
    public String ownerId;
    public long expiresAt;
    public long views;

    public Story() {
        // Required for Firebase deserialization
    }

    protected Story(Parcel in) {
        storyId = in.readString();
        companyName = in.readString();
        logoUrl = in.readString();
        mediaUrl = in.readString();
        caption = in.readString();
        ctaUrl = in.readString();
        timestamp = in.readLong();
        active = in.readByte() != 0;
        ownerId = in.readString();
        expiresAt = in.readLong();
        views = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(storyId);
        dest.writeString(companyName);
        dest.writeString(logoUrl);
        dest.writeString(mediaUrl);
        dest.writeString(caption);
        dest.writeString(ctaUrl);
        dest.writeLong(timestamp);
        dest.writeByte((byte) (active ? 1 : 0));
        dest.writeString(ownerId);
        dest.writeLong(expiresAt);
        dest.writeLong(views);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Story> CREATOR = new Creator<Story>() {
        @Override
        public Story createFromParcel(Parcel in) {
            return new Story(in);
        }

        @Override
        public Story[] newArray(int size) {
            return new Story[size];
        }
    };
}
