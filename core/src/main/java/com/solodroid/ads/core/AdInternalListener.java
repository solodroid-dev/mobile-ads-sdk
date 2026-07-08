package com.solodroid.ads.core;

public interface AdInternalListener {
    void onAdLoaded();
    void onAdFailed();
    default void onAdDismissed() {}
    default void onRewardEarned() {}
}