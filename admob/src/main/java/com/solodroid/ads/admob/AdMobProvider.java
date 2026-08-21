package com.solodroid.ads.admob;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

// NEXT-GEN SDK IMPORTS
import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

// BANNER
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;

// INTERSTITIAL
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;

// REWARDED
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;

// APP OPEN
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd;

// NATIVE
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.AdsManager;
import com.solodroid.ads.core.models.AdModel;

import java.util.Arrays;
import java.util.List;

public class AdMobProvider implements AdProvider {

    private static final String TAG = "AdMobProvider";

    private InterstitialAd mInterstitial;
    private RewardedAd mRewarded;
    private AppOpenAd mAppOpen;
    private boolean isMobileAdsInitializeCalled = false;

    // --- SISTEM KUNCI ANTI DUPLIKAT (RACE CONDITION) ---
    private boolean isInterstitialLoading = false;
    private boolean isRewardedLoading = false;
    private boolean isAppOpenLoading = false;

    @Override
    public void init(Activity activity, AdModel adModel, AdsManager.InitializationListener listener) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        adMobGdpr.gatherConsent(() -> {
            if (isMobileAdsInitializeCalled) {
                return;
            }
            isMobileAdsInitializeCalled = true;

            String admobAppId = adModel.getMainAdmobAppId();
            if (admobAppId == null || admobAppId.isEmpty()) {
                admobAppId = adModel.getBackupAdmobAppId();
            }

            if (admobAppId != null && !admobAppId.isEmpty()) {
                InitializationConfig config = new InitializationConfig.Builder(admobAppId).build();
                MobileAds.initialize(activity, config, initializationStatus -> {
                    Log.d(TAG, "AdMob SDK Initialized successfully");
                    activity.runOnUiThread(() -> {
                        if (listener != null) listener.onInitComplete();
                    });
                });
            } else {
                Log.e(TAG, "AdMob SDK Init Failed: App ID is empty or null!");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onInitComplete();
                });
            }
        });
    }

    // =========================================================================================
    // BANNER AD (View-based, no global lock needed)
    // =========================================================================================
    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.e(TAG, "Banner Ad - Failed to load: Ad Unit ID is empty");
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdView adView = new AdView(activity);
        BannerAdRequest adRequest = new BannerAdRequest.Builder(adUnitId, getAdSize(activity)).build();

        container.removeAllViews();
        container.addView(adView);

        adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd ad) {
                Log.d(TAG, "Banner Ad - Loaded successfully");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                Log.e(TAG, "Banner Ad - Failed to load: " + e.getMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    private AdSize getAdSize(Activity activity) {
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        float adWidthPixels = displayMetrics.widthPixels;
        float density = displayMetrics.density;
        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }

    // =========================================================================================
    // INTERSTITIAL AD (Global, protected with loading lock)
    // =========================================================================================
    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.e(TAG, "Interstitial Ad - Failed to load: Ad Unit ID is empty");
            if (listener != null) listener.onAdFailed();
            return;
        }

        // Cek 1: Jika iklan sudah siap tayang
        if (mInterstitial != null) {
            Log.d(TAG, "Interstitial Ad - Already loaded, skipping request");
            if (listener != null) listener.onAdLoaded();
            return;
        }

        // Cek 2: Jika iklan SEDANG di-request
        if (isInterstitialLoading) {
            Log.d(TAG, "Interstitial Ad - Currently loading, skipping duplicate request");
            return;
        }

        isInterstitialLoading = true; // Kunci aktif
        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();

        InterstitialAd.load(adRequest, new AdLoadCallback<InterstitialAd>() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                Log.d(TAG, "Interstitial Ad - Loaded successfully");
                mInterstitial = interstitialAd;
                isInterstitialLoading = false; // Buka kunci
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Interstitial Ad - Failed to load: " + loadAdError.getMessage());
                mInterstitial = null;
                isInterstitialLoading = false; // Buka kunci
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (mInterstitial != null) {
                mInterstitial.setAdEventCallback(
                        new InterstitialAdEventCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                Log.d(TAG, "Interstitial Ad - Dismissed by user");
                                mInterstitial = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                                Log.e(TAG, "Interstitial Ad - Failed to show: " + fullScreenContentError.getMessage());
                                mInterstitial = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }
                        }
                );
                Log.d(TAG, "Interstitial Ad - Showing to user");
                mInterstitial.show(activity);
            } else {
                Log.e(TAG, "Interstitial Ad - Cannot show, ad is null (Not loaded yet)");
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    // =========================================================================================
    // NATIVE AD (View-based, no global lock needed)
    // =========================================================================================
    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, String style, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.e(TAG, "Native Ad - Failed to load: Ad Unit ID is empty");
            if (listener != null) listener.onAdFailed();
            return;
        }

        List<NativeAd.NativeAdType> adTypes = Arrays.asList(NativeAd.NativeAdType.NATIVE);
        NativeAdRequest adRequest = new NativeAdRequest.Builder(adUnitId, adTypes).build();

        NativeAdLoader.load(adRequest, new NativeAdLoaderCallback() {
            @Override
            public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                Log.d(TAG, "Native Ad - Loaded successfully");
                activity.runOnUiThread(() -> {
                    int layoutResId;
                    String safeStyle = (style != null) ? style.toLowerCase() : "medium";

                    switch (safeStyle) {
                        case "small":
                            layoutResId = R.layout.admob_native_small;
                            break;
                        case "large":
                            layoutResId = R.layout.admob_native_large;
                            break;
                        case "medium":
                        default:
                            layoutResId = R.layout.admob_native_medium;
                            break;
                    }

                    View adView = activity.getLayoutInflater().inflate(layoutResId, null);

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

                    populateNativeAdView(nativeAd, (NativeAdView) adView);

                    container.removeAllViews();
                    container.addView(adView);

                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                Log.e(TAG, "Native Ad - Failed to load: " + e.getMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override
            public void onAdLoadingCompleted() {}
        });
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));

        if (adView.getHeadlineView() != null) {
            ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        }

        if (adView.getBodyView() != null) {
            if (nativeAd.getBody() == null) {
                adView.getBodyView().setVisibility(View.INVISIBLE);
            } else {
                adView.getBodyView().setVisibility(View.VISIBLE);
                ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
            }
        }

        if (adView.getCallToActionView() != null) {
            if (nativeAd.getCallToAction() == null) {
                adView.getCallToActionView().setVisibility(View.INVISIBLE);
            } else {
                adView.getCallToActionView().setVisibility(View.VISIBLE);
                ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            }
        }

        if (adView.getIconView() != null) {
            if (nativeAd.getIcon() == null) {
                adView.getIconView().setVisibility(View.GONE);
            } else {
                ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
                adView.getIconView().setVisibility(View.VISIBLE);
            }
        }

        MediaView mediaView = adView.findViewById(R.id.ad_media);
        adView.registerNativeAd(nativeAd, mediaView);
    }

    // =========================================================================================
    // REWARDED AD (Global, protected with loading lock)
    // =========================================================================================
    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.e(TAG, "Rewarded Ad - Failed to load: Ad Unit ID is empty");
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (mRewarded != null) {
            Log.d(TAG, "Rewarded Ad - Already loaded, skipping request");
            if (listener != null) listener.onAdLoaded();
            return;
        }

        if (isRewardedLoading) {
            Log.d(TAG, "Rewarded Ad - Currently loading, skipping duplicate request");
            return;
        }

        isRewardedLoading = true; // Kunci aktif
        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();

        RewardedAd.load(adRequest, new AdLoadCallback<RewardedAd>() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                Log.d(TAG, "Rewarded Ad - Loaded successfully");
                mRewarded = rewardedAd;
                isRewardedLoading = false; // Buka kunci
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                Log.e(TAG, "Rewarded Ad - Failed to load: " + e.getMessage());
                mRewarded = null;
                isRewardedLoading = false; // Buka kunci
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (mRewarded != null) {
                mRewarded.setAdEventCallback(
                        new RewardedAdEventCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                Log.d(TAG, "Rewarded Ad - Dismissed by user");
                                mRewarded = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                                Log.e(TAG, "Rewarded Ad - Failed to show: " + fullScreenContentError.getMessage());
                                mRewarded = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }
                        }
                );

                Log.d(TAG, "Rewarded Ad - Showing to user");
                mRewarded.show(activity, rewardItem -> {
                    activity.runOnUiThread(() -> {
                        if (listener != null) {
                            listener.onRewardEarned();
                            Log.d(TAG, "Rewarded Ad - Reward earned! Amount: " + rewardItem.getAmount());
                        }
                    });
                });
            } else {
                Log.e(TAG, "Rewarded Ad - Cannot show, ad is null (Not loaded yet)");
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    // =========================================================================================
    // APP OPEN AD (Global, protected with loading lock)
    // =========================================================================================
    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.e(TAG, "App Open Ad - Failed to load: Ad Unit ID is empty");
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (mAppOpen != null) {
            Log.d(TAG, "App Open Ad - Already loaded, skipping request");
            if (listener != null) listener.onAdLoaded();
            return;
        }

        if (isAppOpenLoading) {
            Log.d(TAG, "App Open Ad - Currently loading, skipping duplicate request");
            return;
        }

        isAppOpenLoading = true; // Kunci aktif
        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();

        AppOpenAd.load(adRequest, new AdLoadCallback<AppOpenAd>() {
            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                Log.d(TAG, "App Open Ad - Loaded successfully");
                mAppOpen = ad;
                isAppOpenLoading = false; // Buka kunci
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                Log.e(TAG, "App Open Ad - Failed to load: " + e.getMessage());
                mAppOpen = null;
                isAppOpenLoading = false; // Buka kunci
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            // PROTEKSI SANGAT PENTING:
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.e(TAG, "App Open Ad - Activity is dying, aborting ad show!");
                if (listener != null) listener.onAdDismissed();
                return;
            }

            if (mAppOpen != null) {
                mAppOpen.setAdEventCallback(
                        new AppOpenAdEventCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                Log.d(TAG, "App Open Ad - Dismissed by user");
                                mAppOpen = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                                Log.e(TAG, "App Open Ad - Failed to show: " + fullScreenContentError.getMessage());
                                mAppOpen = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }
                        });
                Log.d(TAG, "App Open Ad - Showing to user");
                mAppOpen.show(activity);
            } else {
                Log.e(TAG, "App Open Ad - Cannot show, ad is null (Not loaded yet)");
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    // =========================================================================================
    // PRIVACY / GDPR
    // =========================================================================================
    @Override
    public void showPrivacyOptions(Activity activity) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        if (adMobGdpr.isPrivacyOptionsRequired()) {
            adMobGdpr.showPrivacyOptionsForm(activity, formError -> {
                if (formError != null) {
                    Log.w(TAG, "Error showing privacy options form: " + formError.getMessage());
                }
            });
        }
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        return adMobGdpr.isPrivacyOptionsRequired();
    }
}