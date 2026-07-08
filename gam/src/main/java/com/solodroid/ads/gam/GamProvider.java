package com.solodroid.ads.gam;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.models.AdModel;

public class GamProvider implements AdProvider {

    private AdManagerInterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private AppOpenAd appOpenAd;
    private boolean isMobileAdsInitializeCalled = false;

    @Override
    public void init(Activity activity, AdModel adModel) {
        GamGdpr gamGdpr = new GamGdpr(activity);
        gamGdpr.gatherConsent(() -> {
            if (isMobileAdsInitializeCalled) {
                return;
            }
            isMobileAdsInitializeCalled = true;

            MobileAds.initialize(activity, initializationStatus -> {
                Log.d("GamProvider", "GAM Initialized successfully after GDPR check");
            });
        });
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdManagerAdView adView = new AdManagerAdView(activity);
        adView.setAdUnitId(adUnitId);
        adView.setAdSize(getAdSize(activity));
        container.removeAllViews();
        container.addView(adView);

        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                if (listener != null) listener.onAdFailed();
            }
        });

        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private AdSize getAdSize(Activity activity) {
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        float adWidthPixels = displayMetrics.widthPixels;
        float density = displayMetrics.density;
        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        AdManagerInterstitialAd.load(activity, adUnitId, adRequest, new AdManagerInterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull AdManagerInterstitialAd ad) {
                interstitialAd = ad;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                interstitialAd = null;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        if (interstitialAd != null) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    interstitialAd = null;
                    if (listener != null) listener.onAdDismissed();
                }
            });
            interstitialAd.show(activity);
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        Log.d("GamProvider", "Native Ad: Memulai request iklan. Ad Unit ID: " + adUnitId);

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.w("GamProvider", "Native Ad: Dibatalkan karena Ad Unit ID kosong atau '0'.");
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdLoader adLoader = new AdLoader.Builder(activity, adUnitId)
                .forNativeAd(nativeAd -> {
                    Log.d("GamProvider", "Native Ad: Sukses didapatkan dari server!");

                    // Menggunakan layout native yang sama dengan AdMob
                    View adView = activity.getLayoutInflater().inflate(com.solodroid.ads.gam.R.layout.gam_native_ads, null);

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

                    Log.d("GamProvider", "Native Ad: Selesai dirender ke container (layar).");

                    if (listener != null) listener.onAdLoaded();
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError e) {
                        Log.e("GamProvider", "Native Ad: GAGAL! Error Code: " + e.getCode() + " | Pesan: " + e.getMessage());
                        if (listener != null) listener.onAdFailed();
                    }
                }).build();

        // Native Ad menggunakan AdManagerAdRequest
        adLoader.loadAd(new AdManagerAdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setMediaView(adView.findViewById(R.id.ad_media));

        ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());

        if (nativeAd.getBody() == null) {
            adView.getBodyView().setVisibility(View.INVISIBLE);
        } else {
            adView.getBodyView().setVisibility(View.VISIBLE);
            ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
        }

        if (nativeAd.getCallToAction() == null) {
            adView.getCallToActionView().setVisibility(View.INVISIBLE);
        } else {
            adView.getCallToActionView().setVisibility(View.VISIBLE);
            ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
        }

        if (nativeAd.getIcon() == null) {
            adView.getIconView().setVisibility(View.GONE);
        } else {
            ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
            adView.getIconView().setVisibility(View.VISIBLE);
        }

        adView.setNativeAd(nativeAd);
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        RewardedAd.load(activity, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                rewardedAd = null;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        if (rewardedAd != null) {
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    rewardedAd = null;
                    if (listener != null) listener.onAdDismissed();
                }
            });

            rewardedAd.show(activity, rewardItem -> {
                if (listener != null) {
                    listener.onRewardEarned();
                    Log.d("GamProvider", "Reward earned: " + rewardItem.getAmount());
                }
            });
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();
        AppOpenAd.load(activity, adUnitId, adRequest, new AppOpenAd.AppOpenAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                appOpenAd = ad;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                appOpenAd = null;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        if (appOpenAd != null) {
            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    appOpenAd = null;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    appOpenAd = null;
                    if (listener != null) listener.onAdDismissed();
                }
            });
            appOpenAd.show(activity);
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
        GamGdpr gamGdpr = new GamGdpr(activity);
        if (gamGdpr.isPrivacyOptionsRequired()) {
            gamGdpr.showPrivacyOptionsForm(activity, formError -> {
                if (formError != null) {
                    Log.w("GamProvider", "Error showing privacy options form: " + formError.getMessage());
                }
            });
        }
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        GamGdpr gamGdpr = new GamGdpr(activity);
        return gamGdpr.isPrivacyOptionsRequired();
    }

}