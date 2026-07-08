package com.solodroid.ads.applovin;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxAdView;
import com.applovin.mediation.ads.MaxAppOpenAd;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;
import com.applovin.sdk.AppLovinCmpService;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.models.AdModel;

public class AppLovinMaxProvider implements AdProvider {

    private static final String TAG = "AppLovinMaxProvider";

    private MaxInterstitialAd interstitialAd;
    private MaxRewardedAd rewardedAd;
    private MaxAppOpenAd appOpenAd;

    private MaxNativeAdLoader nativeAdLoader;
    private MaxAd loadedNativeAd;

    private boolean isInitialized = false;

    // JEMBATAN LISTENER: Untuk menangkap listener dari metode show()
    private AdInternalListener interstitialShowListener;
    private AdInternalListener rewardedShowListener;
    private AdInternalListener appOpenShowListener;

    @Override
    public void init(Activity activity, AdModel adModel) {
        if (isInitialized) return;

        boolean isDebuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String sdkKey = AppLovinSdk.getInstance(activity).getSdkKey();

        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(sdkKey, activity)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .build();

        AppLovinSdk.getInstance(activity).initialize(initConfig, configuration -> {
            isInitialized = true;
            Log.d(TAG, "AppLovin MAX Initialized. Country: " + configuration.getCountryCode());

            if (isDebuggable) {
                // AppLovinSdk.getInstance(activity).showMediationDebugger();
            }
        });
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        MaxAdView adView = new MaxAdView(adUnitId, activity);

        int heightPx = activity.getResources().getDimensionPixelSize(R.dimen.ads_banner_height_50);
        adView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

        adView.setListener(new MaxAdViewAdListener() {
            @Override
            public void onAdLoaded(@NonNull MaxAd ad) {
                Log.d(TAG, "Banner Loaded");
                container.removeAllViews();
                container.addView(adView);
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) {
                Log.e(TAG, "Banner Failed: " + error.getMessage());
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError error) {
            }

            @Override
            public void onAdExpanded(MaxAd ad) {
            }

            @Override
            public void onAdCollapsed(MaxAd ad) {
            }

            @Override
            public void onAdDisplayed(MaxAd ad) {
            }

            @Override
            public void onAdHidden(MaxAd ad) {
            }

            @Override
            public void onAdClicked(MaxAd ad) {
            }
        });

        adView.loadAd();
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        interstitialAd = new MaxInterstitialAd(adUnitId, activity);
        interstitialAd.setListener(new MaxAdListener() {
            @Override
            public void onAdLoaded(MaxAd ad) {
                Log.d(TAG, "Interstitial Loaded");
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdLoadFailed(String adUnitId, MaxError error) {
                Log.e(TAG, "Interstitial Failed: " + error.getMessage());
                interstitialAd = null;
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdHidden(MaxAd ad) {
                interstitialAd = null;
                if (interstitialShowListener != null) {
                    interstitialShowListener.onAdDismissed();
                    interstitialShowListener = null;
                }
            }

            @Override
            public void onAdDisplayFailed(MaxAd ad, MaxError error) {
                Log.e(TAG, "Interstitial Display Failed: " + error.getMessage());
                interstitialAd = null;
                if (interstitialShowListener != null) {
                    interstitialShowListener.onAdDismissed();
                    interstitialShowListener = null;
                }
            }

            @Override
            public void onAdDisplayed(MaxAd ad) {
            }

            @Override
            public void onAdClicked(MaxAd ad) {
            }
        });

        interstitialAd.loadAd();
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        if (interstitialAd != null && interstitialAd.isReady()) {
            this.interstitialShowListener = listener; // Simpan listener show
            interstitialAd.showAd();
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(R.layout.applovin_native_ads)
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                .setCallToActionButtonId(R.id.ad_call_to_action)
                .setIconImageViewId(R.id.ad_app_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setOptionsContentViewGroupId(R.id.ad_options_view)
                .build();

        MaxNativeAdView maxNativeAdView = new MaxNativeAdView(binder, activity);

        nativeAdLoader = new MaxNativeAdLoader(adUnitId, activity);
        nativeAdLoader.setNativeAdListener(new MaxNativeAdListener() {
            @Override
            public void onNativeAdLoaded(MaxNativeAdView nativeAdView, MaxAd ad) {
                Log.d(TAG, "Native Ad Loaded");

                if (loadedNativeAd != null) {
                    nativeAdLoader.destroy(loadedNativeAd);
                }
                loadedNativeAd = ad;

                int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
                int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
                int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);

                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
                nativeAdView.setLayoutParams(params);

                container.removeAllViews();
                container.addView(nativeAdView);

                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onNativeAdLoadFailed(String adUnitId, MaxError error) {
                Log.e(TAG, "Native Ad Failed: " + error.getMessage());
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onNativeAdClicked(MaxAd ad) {
            }
        });

        nativeAdLoader.loadAd(maxNativeAdView);
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        rewardedAd = MaxRewardedAd.getInstance(adUnitId, activity);
        rewardedAd.setListener(new MaxRewardedAdListener() {
            @Override
            public void onAdLoaded(MaxAd ad) {
                Log.d(TAG, "Rewarded Loaded");
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdLoadFailed(String adUnitId, MaxError error) {
                Log.e(TAG, "Rewarded Failed: " + error.getMessage());
                rewardedAd = null;
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onUserRewarded(MaxAd ad, MaxReward reward) {
                Log.d(TAG, "Reward Earned (AppLovin MAX)");
                if (rewardedShowListener != null) {
                    rewardedShowListener.onRewardEarned();
                }
            }

            @Override
            public void onAdHidden(MaxAd ad) {
                rewardedAd = null;
                if (rewardedShowListener != null) {
                    rewardedShowListener.onAdDismissed();
                    rewardedShowListener = null;
                }
            }

            @Override
            public void onAdDisplayFailed(MaxAd ad, MaxError error) {
                Log.e(TAG, "Rewarded Display Failed: " + error.getMessage());
                rewardedAd = null;
                if (rewardedShowListener != null) {
                    rewardedShowListener.onAdDismissed();
                    rewardedShowListener = null;
                }
            }

            @Override
            public void onAdDisplayed(MaxAd ad) {
            }

            @Override
            public void onAdClicked(MaxAd ad) {
            }
        });

        rewardedAd.loadAd();
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        if (rewardedAd != null && rewardedAd.isReady()) {
            this.rewardedShowListener = listener; // Simpan listener show
            rewardedAd.showAd();
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

        appOpenAd = new MaxAppOpenAd(adUnitId, activity);
        appOpenAd.setListener(new MaxAdListener() {
            @Override
            public void onAdLoaded(MaxAd ad) {
                Log.d(TAG, "App Open Loaded");
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdLoadFailed(String adUnitId, MaxError error) {
                Log.e(TAG, "App Open Failed: " + error.getMessage());
                appOpenAd = null;
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdHidden(MaxAd ad) {
                appOpenAd = null;
                if (appOpenShowListener != null) {
                    appOpenShowListener.onAdDismissed();
                    appOpenShowListener = null;
                }
            }

            @Override
            public void onAdDisplayFailed(MaxAd ad, MaxError error) {
                Log.e(TAG, "App Open Display Failed: " + error.getMessage());
                appOpenAd = null;
                if (appOpenShowListener != null) {
                    appOpenShowListener.onAdDismissed();
                    appOpenShowListener = null;
                }
            }

            @Override
            public void onAdDisplayed(MaxAd ad) {
            }

            @Override
            public void onAdClicked(MaxAd ad) {
            }
        });

        appOpenAd.loadAd();
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        if (appOpenAd != null && appOpenAd.isReady()) {
            this.appOpenShowListener = listener; // Simpan listener show
            appOpenAd.showAd();
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
        AppLovinCmpService cmpService = AppLovinSdk.getInstance(activity).getCmpService();
        if (cmpService.hasSupportedCmp()) {
            cmpService.showCmpForExistingUser(activity, error -> {
                if (error != null) {
                    Log.w(TAG, "CMP Error: " + error.getMessage());
                }
            });
        }
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        return AppLovinSdk.getInstance(activity).getCmpService().hasSupportedCmp();
    }
}