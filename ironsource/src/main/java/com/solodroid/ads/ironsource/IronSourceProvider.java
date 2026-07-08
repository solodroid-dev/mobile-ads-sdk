package com.solodroid.ads.ironsource;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayMediaView;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAd;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAdListener;
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout;
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo;
import com.ironsource.mediationsdk.logger.IronSourceError;

import com.unity3d.mediation.LevelPlay;
import com.unity3d.mediation.LevelPlayAdError;
import com.unity3d.mediation.LevelPlayAdInfo;
import com.unity3d.mediation.LevelPlayAdSize;
import com.unity3d.mediation.LevelPlayConfiguration;
import com.unity3d.mediation.LevelPlayInitError;
import com.unity3d.mediation.LevelPlayInitListener;
import com.unity3d.mediation.LevelPlayInitRequest;
import com.unity3d.mediation.banner.LevelPlayBannerAdView;
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener;
import com.unity3d.mediation.rewarded.LevelPlayReward;
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd;
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.models.AdModel;

import java.util.ArrayList;
import java.util.List;

public class IronSourceProvider implements AdProvider {

    private static final String TAG = "IronSourceProvider";

    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private boolean isInitFailed = false;

    private final List<Runnable> pendingTasks = new ArrayList<>();

    // Instance Object LevelPlay
    private LevelPlayBannerAdView levelPlayBannerAdView;
    private LevelPlayInterstitialAd levelPlayInterstitialAd;
    private LevelPlayRewardedAd levelPlayRewardedAd;
    private LevelPlayNativeAd currentNativeAd;
    private LevelPlayInterstitialAd levelPlayAppOpenAd;

    // PERBAIKAN: Jembatan Listener untuk menangkap aksi saat iklan ditonton/ditutup
    private AdInternalListener interstitialShowListener;
    private AdInternalListener rewardedShowListener;
    private AdInternalListener appOpenShowListener;

    @Override
    public void init(Activity activity, AdModel adModel) {
        if (isInitialized || isInitializing) return;

        String appKey = adModel.getMainAds().equals("ironsource")
                ? adModel.getMainIronsourceAppKey()
                : adModel.getBackupIronsourceAppKey();

        if (appKey != null && !appKey.isEmpty() && !appKey.equals("0")) {

            isInitializing = true;
            LevelPlayInitRequest initRequest = new LevelPlayInitRequest.Builder(appKey).build();

            LevelPlay.init(activity, initRequest, new LevelPlayInitListener() {
                @Override
                public void onInitSuccess(@NonNull LevelPlayConfiguration configuration) {
                    isInitialized = true;
                    isInitializing = false;
                    isInitFailed = false;
                    Log.d(TAG, "LevelPlay (ironSource) initialize complete with appKey: " + appKey);

                    // Salin antrean untuk menghindari ConcurrentModificationException
                    List<Runnable> tasksCopy = new ArrayList<>(pendingTasks);
                    pendingTasks.clear();

                    activity.runOnUiThread(() -> {
                        for (Runnable task : tasksCopy) {
                            task.run();
                        }
                    });
                }

                @Override
                public void onInitFailed(@NonNull LevelPlayInitError error) {
                    isInitializing = false;
                    isInitFailed = true;
                    Log.e(TAG, "LevelPlay initialize failed: " + error.getErrorMessage());

                    // Salin antrean dan eksekusi agar memicu listener.onAdFailed()
                    List<Runnable> tasksCopy = new ArrayList<>(pendingTasks);
                    pendingTasks.clear();

                    activity.runOnUiThread(() -> {
                        for (Runnable task : tasksCopy) {
                            task.run();
                        }
                    });
                }
            });
        }

        new Thread(() -> {
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(activity);
                String adId = adInfo.getId();
                Log.d("IronSourceProvider", "Advertising ID: " + adId); // Tampilkan di Logcat
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Banner load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadBanner(activity, container, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        container.removeAllViews();

        LevelPlayBannerAdView.Config adConfig = new LevelPlayBannerAdView.Config.Builder()
                .setAdSize(LevelPlayAdSize.BANNER)
                .setPlacementName(adUnitId)
                .build();

        levelPlayBannerAdView = new LevelPlayBannerAdView(activity, adUnitId, adConfig);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.addView(levelPlayBannerAdView, 0, layoutParams);

        levelPlayBannerAdView.setBannerListener(new LevelPlayBannerAdViewListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onBannerAdLoaded");
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onBannerAdLoadFailed: " + error.getErrorMessage() + " " + adUnitId);
                container.removeAllViews();
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdInfo adInfo, @NonNull LevelPlayAdError error) {
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdExpanded(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdCollapsed(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdLeftApplication(@NonNull LevelPlayAdInfo adInfo) {
            }
        });

        levelPlayBannerAdView.loadAd();
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Interstitial load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadInterstitial(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        levelPlayInterstitialAd = new LevelPlayInterstitialAd(adUnitId);
        levelPlayInterstitialAd.setListener(new LevelPlayInterstitialAdListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onInterstitialAdLoaded");
                if (listener != null) listener.onAdLoaded(); // Gunakan load listener
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onInterstitialAdLoadFailed: " + error.getErrorMessage() + " " + adUnitId);
                levelPlayInterstitialAd = null;
                if (listener != null) listener.onAdFailed(); // Gunakan load listener
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdError error, @NonNull LevelPlayAdInfo adInfo) {
                Log.e(TAG, "onInterstitialAdDisplayFailed: " + error.getErrorMessage());
                levelPlayInterstitialAd = null;
                // PERBAIKAN: Gunakan show listener
                if (interstitialShowListener != null) {
                    interstitialShowListener.onAdDismissed();
                    interstitialShowListener = null;
                }
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdClosed(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onInterstitialAdClosed");
                levelPlayInterstitialAd = null;
                // PERBAIKAN: Gunakan show listener
                if (interstitialShowListener != null) {
                    interstitialShowListener.onAdDismissed();
                    interstitialShowListener = null;
                }
            }

            @Override
            public void onAdInfoChanged(@NonNull LevelPlayAdInfo adInfo) {
            }
        });

        levelPlayInterstitialAd.loadAd();
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        if (levelPlayInterstitialAd != null && levelPlayInterstitialAd.isAdReady()) {
            this.interstitialShowListener = listener; // PERBAIKAN: Simpan listener saat show
            levelPlayInterstitialAd.showAd(activity);
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Native Ad load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadNative(activity, container, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (currentNativeAd != null) {
            currentNativeAd.destroyAd();
        }

        LevelPlayNativeAdListener nativeAdListener = new LevelPlayNativeAdListener() {
            @Override
            public void onAdLoaded(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
                Log.d(TAG, "onNativeAdLoaded");

                LayoutInflater inflater = LayoutInflater.from(activity);
                NativeAdLayout nativeAdLayout = (NativeAdLayout) inflater.inflate(R.layout.ironsource_native_ads, null);

                TextView titleView = nativeAdLayout.findViewById(R.id.ad_headline);
                TextView bodyView = nativeAdLayout.findViewById(R.id.ad_body);
                ImageView iconView = nativeAdLayout.findViewById(R.id.ad_app_icon);
                Button ctaView = nativeAdLayout.findViewById(R.id.ad_call_to_action);
                LevelPlayMediaView mediaView = nativeAdLayout.findViewById(R.id.ad_media);

                if (nativeAd.getTitle() != null && titleView != null) {
                    titleView.setText(nativeAd.getTitle());
                    nativeAdLayout.setTitleView(titleView);
                }

                if (nativeAd.getBody() != null && bodyView != null) {
                    bodyView.setText(nativeAd.getBody());
                    nativeAdLayout.setBodyView(bodyView);
                }

                if (nativeAd.getIcon() != null && nativeAd.getIcon().getDrawable() != null && iconView != null) {
                    iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
                    nativeAdLayout.setIconView(iconView);
                }

                if (nativeAd.getCallToAction() != null && ctaView != null) {
                    ctaView.setText(nativeAd.getCallToAction());
                    nativeAdLayout.setCallToActionView(ctaView);
                }

                if (mediaView != null) {
                    nativeAdLayout.setMediaView(mediaView);
                }

                nativeAdLayout.registerNativeAdViews(nativeAd);

                // --- PERBAIKAN: SETTING MARGIN NATIVE AD ---
                int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
                int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
                int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);

                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
                nativeAdLayout.setLayoutParams(params);
                // ------------------------------------------

                container.removeAllViews();
                container.addView(nativeAdLayout);

                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdLoadFailed(LevelPlayNativeAd nativeAd, IronSourceError error) {
                Log.e(TAG, "onNativeAdLoadFailed: " + error.getErrorMessage() + " " + adUnitId);
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onAdImpression(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
            }

            @Override
            public void onAdClicked(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
            }
        };

        currentNativeAd = new LevelPlayNativeAd.Builder()
                .withPlacementName(adUnitId)
                .withListener(nativeAdListener)
                .build();

        currentNativeAd.loadAd();
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Rewarded load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadRewarded(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        levelPlayRewardedAd = new LevelPlayRewardedAd(adUnitId);
        levelPlayRewardedAd.setListener(new LevelPlayRewardedAdListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onRewardedAdLoaded");
                if (listener != null) listener.onAdLoaded(); // Gunakan load listener
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onRewardedAdLoadFailed: " + error.getErrorMessage());
                levelPlayRewardedAd = null;
                if (listener != null) listener.onAdFailed(); // Gunakan load listener
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdError error, @NonNull LevelPlayAdInfo adInfo) {
                Log.e(TAG, "onRewardedAdDisplayFailed: " + error.getErrorMessage());
                levelPlayRewardedAd = null;
                // PERBAIKAN: Gunakan show listener
                if (rewardedShowListener != null) {
                    rewardedShowListener.onAdDismissed();
                    rewardedShowListener = null;
                }
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdRewarded(@NonNull LevelPlayReward reward, @NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onRewardedAdRewarded: " + reward.getName());
                // PERBAIKAN: Gunakan show listener
                if (rewardedShowListener != null) {
                    rewardedShowListener.onRewardEarned();
                }
            }

            @Override
            public void onAdClosed(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onRewardedAdClosed");
                levelPlayRewardedAd = null;
                // PERBAIKAN: Gunakan show listener
                if (rewardedShowListener != null) {
                    rewardedShowListener.onAdDismissed();
                    rewardedShowListener = null;
                }
            }

            @Override
            public void onAdInfoChanged(@NonNull LevelPlayAdInfo adInfo) {
            }
        });

        levelPlayRewardedAd.loadAd();
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        if (levelPlayRewardedAd != null && levelPlayRewardedAd.isAdReady()) {
            this.rewardedShowListener = listener; // PERBAIKAN: Simpan listener saat show
            levelPlayRewardedAd.showAd(activity);
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "App Open load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadAppOpen(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        levelPlayAppOpenAd = new LevelPlayInterstitialAd(adUnitId);
        levelPlayAppOpenAd.setListener(new LevelPlayInterstitialAdListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onAppOpenAdLoaded (Fallback)");
                if (listener != null) listener.onAdLoaded(); // Gunakan load listener
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onAppOpenAdLoadFailed: " + error.getErrorMessage() + " " + adUnitId);
                levelPlayAppOpenAd = null;
                if (listener != null) listener.onAdFailed(); // Gunakan load listener
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdError error, @NonNull LevelPlayAdInfo adInfo) {
                Log.e(TAG, "onAppOpenAdDisplayFailed: " + error.getErrorMessage());
                levelPlayAppOpenAd = null;
                // PERBAIKAN: Gunakan show listener
                if (appOpenShowListener != null) {
                    appOpenShowListener.onAdDismissed();
                    appOpenShowListener = null;
                }
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {
            }

            @Override
            public void onAdClosed(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onAppOpenAdClosed");
                levelPlayAppOpenAd = null;
                // PERBAIKAN: Gunakan show listener
                if (appOpenShowListener != null) {
                    appOpenShowListener.onAdDismissed();
                    appOpenShowListener = null;
                }
            }

            @Override
            public void onAdInfoChanged(@NonNull LevelPlayAdInfo adInfo) {
            }
        });

        levelPlayAppOpenAd.loadAd();
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        if (levelPlayAppOpenAd != null && levelPlayAppOpenAd.isAdReady()) {
            this.appOpenShowListener = listener; // PERBAIKAN: Simpan listener saat show
            levelPlayAppOpenAd.showAd(activity);
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