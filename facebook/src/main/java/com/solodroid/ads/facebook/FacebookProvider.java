package com.solodroid.ads.facebook;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.AdOptionsView;
import com.facebook.ads.AdSettings;
import com.facebook.ads.AdSize;
import com.facebook.ads.AdView;
import com.facebook.ads.AudienceNetworkAds;
import com.facebook.ads.InterstitialAd;
import com.facebook.ads.InterstitialAdListener;
import com.facebook.ads.MediaView;
import com.facebook.ads.NativeAd;
import com.facebook.ads.NativeAdLayout;
import com.facebook.ads.NativeAdListener;
import com.facebook.ads.RewardedVideoAd;
import com.facebook.ads.RewardedVideoAdListener;
import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.models.AdModel;

import java.util.ArrayList;
import java.util.List;

public class FacebookProvider implements AdProvider {

    private static final String TAG = "FacebookProvider";

    private InterstitialAd interstitialAd;
    private RewardedVideoAd rewardedAd;
    private InterstitialAd appOpenAd;

    private boolean isInitialized = false;

    // PERBAIKAN: Variabel penampung listener saat metode show() dipanggil
    private AdInternalListener interstitialShowListener;
    private AdInternalListener rewardedShowListener;
    private AdInternalListener appOpenShowListener;

    @Override
    public void init(Activity activity, AdModel adModel) {
        if (isInitialized || AudienceNetworkAds.isInitialized(activity)) {
            return;
        }

        boolean isDebuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        if (isDebuggable) {
            AdSettings.turnOnSDKDebugger(activity);
            AdSettings.setTestMode(true);
            AdSettings.setIntegrationErrorMode(AdSettings.IntegrationErrorMode.INTEGRATION_ERROR_CRASH_DEBUG_MODE);
            Log.d(TAG, "Facebook Test Mode & SDK Debugger ENABLED");
        }

        AudienceNetworkAds
                .buildInitSettings(activity)
                .withInitListener(initResult -> {
                    Log.d(TAG, "Facebook Audience Network Initialized: " + initResult.getMessage());
                    isInitialized = true;
                })
                .initialize();
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdView adView = new AdView(activity, adUnitId, AdSize.BANNER_HEIGHT_50);

        com.facebook.ads.AdListener adListener = new com.facebook.ads.AdListener() {
            @Override
            public void onError(Ad ad, AdError adError) {
                Log.e(TAG, "Banner Failed: " + adError.getErrorMessage());
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdLoaded(Ad ad) {
                Log.d(TAG, "Banner Loaded");
                container.removeAllViews();
                container.addView(adView);
                if (listener != null) listener.onAdLoaded();
            }

            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        };

        adView.loadAd(adView.buildLoadAdConfig().withAdListener(adListener).build());
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        interstitialAd = new InterstitialAd(activity, adUnitId);

        InterstitialAdListener adListener = new InterstitialAdListener() {
            @Override
            public void onInterstitialDisplayed(Ad ad) {}

            @Override
            public void onInterstitialDismissed(Ad ad) {
                interstitialAd = null;
                // PERBAIKAN: Gunakan listener dari metode show()
                if (interstitialShowListener != null) {
                    interstitialShowListener.onAdDismissed();
                    interstitialShowListener = null;
                }
            }

            @Override
            public void onError(Ad ad, AdError adError) {
                Log.e(TAG, "Interstitial Failed: " + adError.getErrorMessage());
                interstitialAd = null;
                if (listener != null) listener.onAdFailed(); // Gagal load, panggil listener load
            }

            @Override
            public void onAdLoaded(Ad ad) {
                Log.d(TAG, "Interstitial Loaded");
                if (listener != null) listener.onAdLoaded(); // Sukses load, panggil listener load
            }

            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        };

        interstitialAd.loadAd(interstitialAd.buildLoadAdConfig().withAdListener(adListener).build());
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        if (interstitialAd != null && interstitialAd.isAdLoaded() && !interstitialAd.isAdInvalidated()) {
            // PERBAIKAN: Simpan listener dari AdsManager
            this.interstitialShowListener = listener;
            interstitialAd.show();
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.w(TAG, "Native Ad Canceled: ID empty or 0");
            if (listener != null) listener.onAdFailed();
            return;
        }

        NativeAd nativeAd = new NativeAd(activity, adUnitId);

        NativeAdListener adListener = new NativeAdListener() {
            @Override public void onMediaDownloaded(Ad ad) {}

            @Override
            public void onError(Ad ad, AdError adError) {
                Log.e(TAG, "Native Ad Failed: " + adError.getErrorMessage());
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdLoaded(Ad ad) {
                Log.d(TAG, "Native Ad Loaded");
                if (nativeAd == null || nativeAd != ad) {
                    return;
                }

                View adView = activity.getLayoutInflater().inflate(R.layout.facebook_native_ads, null);

                int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
                int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
                int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);

                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
                adView.setLayoutParams(params);

                populateNativeAdView(activity, nativeAd, adView);

                container.removeAllViews();
                container.addView(adView);

                if (listener != null) listener.onAdLoaded();
            }

            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        };

        nativeAd.loadAd(nativeAd.buildLoadAdConfig().withAdListener(adListener).build());
    }

    private void populateNativeAdView(Activity activity, NativeAd nativeAd, View adView) {
        nativeAd.unregisterView();

        NativeAdLayout nativeAdLayout = adView.findViewById(R.id.facebook_native_ad_layout);

        MediaView mvAdMedia = adView.findViewById(R.id.ad_media);
        MediaView mvAdIcon = adView.findViewById(R.id.ad_app_icon);
        TextView tvAdTitle = adView.findViewById(R.id.ad_headline);
        TextView tvAdBody = adView.findViewById(R.id.ad_body);
        TextView tvAdSocialContext = adView.findViewById(R.id.ad_social_context);
        Button btnAdCallToAction = adView.findViewById(R.id.ad_call_to_action);
        LinearLayout adChoicesContainer = adView.findViewById(R.id.ad_choices_container);

        if (tvAdTitle != null) tvAdTitle.setText(nativeAd.getAdvertiserName());
        if (tvAdBody != null) tvAdBody.setText(nativeAd.getAdBodyText());
        if (tvAdSocialContext != null) tvAdSocialContext.setText(nativeAd.getAdSocialContext());
        if (btnAdCallToAction != null) {
            btnAdCallToAction.setText(nativeAd.getAdCallToAction());
            btnAdCallToAction.setVisibility(nativeAd.hasCallToAction() ? View.VISIBLE : View.INVISIBLE);
        }

        if (adChoicesContainer != null) {
            adChoicesContainer.removeAllViews();
            AdOptionsView adOptionsView = new AdOptionsView(activity, nativeAd, nativeAdLayout);
            adChoicesContainer.addView(adOptionsView, 0);
        }

        List<View> clickableViews = new ArrayList<>();
        if (tvAdTitle != null) clickableViews.add(tvAdTitle);
        if (btnAdCallToAction != null) clickableViews.add(btnAdCallToAction);
        if (mvAdIcon != null) clickableViews.add(mvAdIcon);

        nativeAd.registerViewForInteraction(nativeAdLayout, mvAdMedia, mvAdIcon, clickableViews);
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        rewardedAd = new RewardedVideoAd(activity, adUnitId);

        RewardedVideoAdListener adListener = new RewardedVideoAdListener() {
            @Override
            public void onRewardedVideoCompleted() {
                Log.d(TAG, "Rewarded Video Completed - Reward Earned");
                // PERBAIKAN: Gunakan listener dari metode show()
                if (rewardedShowListener != null) {
                    rewardedShowListener.onRewardEarned();
                }
            }

            @Override
            public void onRewardedVideoClosed() {
                rewardedAd = null;
                // PERBAIKAN: Gunakan listener dari metode show()
                if (rewardedShowListener != null) {
                    rewardedShowListener.onAdDismissed();
                    rewardedShowListener = null;
                }
            }

            @Override
            public void onError(Ad ad, AdError adError) {
                Log.e(TAG, "Rewarded Failed: " + adError.getErrorMessage());
                rewardedAd = null;
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdLoaded(Ad ad) {
                Log.d(TAG, "Rewarded Loaded");
                if (listener != null) listener.onAdLoaded();
            }

            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        };

        rewardedAd.loadAd(rewardedAd.buildLoadAdConfig().withAdListener(adListener).build());
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        if (rewardedAd != null && rewardedAd.isAdLoaded() && !rewardedAd.isAdInvalidated()) {
            // PERBAIKAN: Simpan listener dari AdsManager agar bisa mengeksekusi unlock layar
            this.rewardedShowListener = listener;
            rewardedAd.show();
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        appOpenAd = new InterstitialAd(activity, adUnitId);

        InterstitialAdListener adListener = new InterstitialAdListener() {
            @Override
            public void onInterstitialDisplayed(Ad ad) {}

            @Override
            public void onInterstitialDismissed(Ad ad) {
                appOpenAd = null;
                // PERBAIKAN: Gunakan listener dari metode show()
                if (appOpenShowListener != null) {
                    appOpenShowListener.onAdDismissed();
                    appOpenShowListener = null;
                }
            }

            @Override
            public void onError(Ad ad, AdError adError) {
                Log.e(TAG, "App Open (Interstitial Fallback) Failed: " + adError.getErrorMessage());
                appOpenAd = null;
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdLoaded(Ad ad) {
                Log.d(TAG, "App Open (Interstitial Fallback) Loaded");
                if (listener != null) listener.onAdLoaded();
            }

            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        };

        appOpenAd.loadAd(appOpenAd.buildLoadAdConfig().withAdListener(adListener).build());
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        if (appOpenAd != null && appOpenAd.isAdLoaded() && !appOpenAd.isAdInvalidated()) {
            // PERBAIKAN: Simpan listener dari AdsManager
            this.appOpenShowListener = listener;
            appOpenAd.show();
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        return false;
    }
}