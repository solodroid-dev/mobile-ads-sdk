package com.solodroid.ads.admob;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.*;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.models.AdModel;

public class AdMobProvider implements AdProvider {

    private InterstitialAd mInterstitial;
    private RewardedAd mRewarded;
    private AppOpenAd mAppOpen;
    private boolean isMobileAdsInitializeCalled = false;

    @Override
    public void init(Activity activity, AdModel adModel) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        adMobGdpr.gatherConsent(() -> {
            if (isMobileAdsInitializeCalled) {
                return;
            }
            isMobileAdsInitializeCalled = true;

            MobileAds.initialize(activity, initializationStatus -> {
                Log.d("AdMobProvider", "AdMob Initialized successfully after GDPR check");
            });
        });
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdView adView = new AdView(activity);
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
        adView.loadAd(new AdRequest.Builder().build());
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
        if (adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(activity, adUnitId, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                mInterstitial = interstitialAd;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mInterstitial = null;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        if (mInterstitial != null) {
            mInterstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mInterstitial = null;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mInterstitial = null;
                    if (listener != null) listener.onAdDismissed();
                }
            });
            mInterstitial.show(activity);
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        // 1. Log saat request dimulai
        Log.d("AdMobProvider", "Native Ad: Memulai request iklan. Ad Unit ID: " + adUnitId);

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            Log.w("AdMobProvider", "Native Ad: Dibatalkan karena Ad Unit ID kosong atau '0'.");
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdLoader adLoader = new AdLoader.Builder(activity, adUnitId)
                .forNativeAd(nativeAd -> {
                    // 2. Log saat iklan sukses didapatkan dari server
                    Log.d("AdMobProvider", "Native Ad: Sukses didapatkan dari server!");

                    View adView = activity.getLayoutInflater().inflate(R.layout.admob_native_ads, null);

                    int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                    int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
                    int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
                    int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);

                    // Tambahan pengecekan konteks untuk layout params
                    ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
                    adView.setLayoutParams(params);

                    populateNativeAdView(nativeAd, (NativeAdView) adView);

                    container.removeAllViews();
                    container.addView(adView);

                    // 3. Log saat iklan selesai dirender ke layar
                    Log.d("AdMobProvider", "Native Ad: Selesai dirender ke container (layar).");

                    if (listener != null) listener.onAdLoaded();
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError e) {
                        // 4. Log saat gagal, menampilkan kode ERROR ASLI DARI ADMOB
                        Log.e("AdMobProvider", "Native Ad: GAGAL! Error Code: " + e.getCode() + " | Pesan: " + e.getMessage());

                        if (listener != null) listener.onAdFailed();
                    }
                }).build();

        adLoader.loadAd(new AdRequest.Builder().build());
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
        if (adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        RewardedAd.load(activity, adUnitId, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                mRewarded = rewardedAd;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                mRewarded = null;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        if (mRewarded != null) {
            mRewarded.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mRewarded = null;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mRewarded = null;
                    if (listener != null) listener.onAdDismissed();
                }
            });

            // TAMPILKAN IKLAN
            mRewarded.show(activity, rewardItem -> {
                // Callback ini HANYA jalan jika user menonton sampai durasi yang ditentukan
                if (listener != null) {
                    listener.onRewardEarned();
                    Log.d("AdMob", "Reward earned: " + rewardItem.getAmount());
                }
            });
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId.equals("0")) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AppOpenAd.load(activity, adUnitId, new AdRequest.Builder().build(), new AppOpenAd.AppOpenAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                mAppOpen = ad;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                mAppOpen = null;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        if (mAppOpen != null) {
            mAppOpen.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mAppOpen = null;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mAppOpen = null;
                    if (listener != null) listener.onAdDismissed();
                }
            });
            mAppOpen.show(activity);
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        if (adMobGdpr.isPrivacyOptionsRequired()) {
            adMobGdpr.showPrivacyOptionsForm(activity, formError -> {
                if (formError != null) {
                    Log.w("AdMobProvider", "Error showing privacy options form: " + formError.getMessage());
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