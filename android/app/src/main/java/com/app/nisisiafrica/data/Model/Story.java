package com.app.nisisiafrica.data.Model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * Home story. {@code storyType}:
 * <ul>
 *   <li>{@code personal} — mentee/mentor posts</li>
 *   <li>{@code corporate} — school/super-admin marketplace / brand ads</li>
 * </ul>
 * Media is hosted on our API under {@code /uploads/app/stories/...}.
 */
public class Story implements Parcelable {
    public static final String TYPE_PERSONAL = "personal";
    public static final String TYPE_CORPORATE = "corporate";

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
    /** personal | corporate — defaults to corporate for legacy rows. */
    public String storyType = TYPE_CORPORATE;

    public Story() {
        // Required for Firebase deserialization
    }

    public boolean isPersonal() {
        return TYPE_PERSONAL.equalsIgnoreCase(storyType);
    }

    public String displayLabel() {
        if (!TextUtils.isEmpty(companyName)) return companyName;
        return isPersonal() ? "Story" : "Brand";
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
        storyType = in.readString();
        if (storyType == null || storyType.isEmpty()) storyType = TYPE_CORPORATE;
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
        dest.writeString(storyType != null ? storyType : TYPE_CORPORATE);
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
