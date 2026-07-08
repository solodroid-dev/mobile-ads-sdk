package com.solodroid.ads.core;

import android.app.Activity;
import android.view.ViewGroup;
import com.solodroid.ads.core.models.AdModel;

public interface AdProvider {
    void init(Activity activity, AdModel adModel);

    // Banner
    void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener);

    // Interstitial
    void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener);
    void showInterstitial(Activity activity, AdInternalListener listener);

    // Native
    void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener);

    // Rewarded
    void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener);
    void showRewarded(Activity activity, AdInternalListener listener);

    // App Open
    void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener);
    void showAppOpen(Activity activity, AdInternalListener listener);

    //GDPR
    default void showPrivacyOptions(Activity activity) {}
    default boolean isPrivacyOptionsRequired(Activity activity) {
        return false;
    }
}