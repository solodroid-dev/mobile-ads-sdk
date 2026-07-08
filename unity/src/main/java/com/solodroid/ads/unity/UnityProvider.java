package com.solodroid.ads.unity;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsLoadOptions;
import com.unity3d.ads.UnityAdsShowOptions;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.models.AdModel;

import java.util.ArrayList;
import java.util.List;

public class UnityProvider implements AdProvider {

    private static final String TAG = "UnityProvider";

    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private boolean isInitFailed = false;

    // Sistem antrean untuk mencegah error "Load before init"
    private final List<Runnable> pendingTasks = new ArrayList<>();

    // Menyimpan state ketersediaan iklan
    private boolean isInterstitialReady = false;
    private boolean isRewardedReady = false;

    // Menyimpan ID iklan secara internal untuk digunakan saat show()
    private String loadedInterstitialId = "";
    private String loadedRewardedId = "";

    @Override
    public void init(Activity activity, AdModel adModel) {
        if (isInitialized || isInitializing) return;

        String unityGameId = adModel.getMainAds().equals("unity")
                ? adModel.getMainUnityGameId()
                : adModel.getBackupUnityGameId();

        if (unityGameId != null && !unityGameId.isEmpty() && !unityGameId.equals("0")) {
            isInitializing = true;

            boolean isDebuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

            UnityAds.initialize(activity, unityGameId, isDebuggable, new IUnityAdsInitializationListener() {
                @Override
                public void onInitializationComplete() {
                    isInitialized = true;
                    isInitializing = false;
                    isInitFailed = false;
                    Log.d(TAG, "Unity Ads is successfully initialized with ID: " + unityGameId);

                    // Eksekusi antrean iklan yang tertunda
                    List<Runnable> tasksCopy = new ArrayList<>(pendingTasks);
                    pendingTasks.clear();
                    activity.runOnUiThread(() -> {
                        for (Runnable task : tasksCopy) {
                            task.run();
                        }
                    });
                }

                @Override
                public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                    isInitializing = false;
                    isInitFailed = true;
                    Log.e(TAG, "Unity Ads Failed to Initialize: " + message);

                    // Batalkan antrean, kirim sinyal gagal ke semua request
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
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Banner load delayed. Waiting for Unity initialization...");
            pendingTasks.add(() -> loadBanner(activity, container, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        container.removeAllViews();
        BannerView bannerView = new BannerView(activity, adUnitId, new UnityBannerSize(320, 50));

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER;

        bannerView.setListener(new BannerView.IListener() {
            @Override
            public void onBannerLoaded(BannerView banner) {
                Log.d(TAG, "Unity Banner loaded");
                container.addView(banner, layoutParams);
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onBannerFailedToLoad(BannerView banner, BannerErrorInfo errorInfo) {
                Log.e(TAG, "Unity Banner Failed: " + errorInfo.errorMessage);
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onBannerClick(BannerView banner) {
            }

            @Override
            public void onBannerLeftApplication(BannerView banner) {
            }

            @Override
            public void onBannerShown(BannerView banner) {
            }
        });

        bannerView.load();
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Native (MREC) load delayed. Waiting for Unity initialization...");
            pendingTasks.add(() -> loadNative(activity, container, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        // Trik: Menggunakan MREC (300x250) sebagai fallback Native Ad
        container.removeAllViews();
        BannerView mrecView = new BannerView(activity, adUnitId, new UnityBannerSize(300, 250));

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
        int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
        int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
        int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);
        layoutParams.setMargins(marginLeft, marginTop, marginRight, marginBottom);
        layoutParams.gravity = Gravity.CENTER;

        mrecView.setListener(new BannerView.IListener() {
            @Override
            public void onBannerLoaded(BannerView banner) {
                Log.d(TAG, "Unity Native (MREC) loaded");
                container.addView(banner, layoutParams);
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onBannerFailedToLoad(BannerView banner, BannerErrorInfo errorInfo) {
                Log.e(TAG, "Unity Native (MREC) Failed: " + errorInfo.errorMessage);
                if (listener != null) listener.onAdFailed();
            }

            @Override
            public void onBannerClick(BannerView banner) {
            }

            @Override
            public void onBannerLeftApplication(BannerView banner) {
            }

            @Override
            public void onBannerShown(BannerView banner) {
            }
        });

        mrecView.load();
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Interstitial load delayed. Waiting for Unity initialization...");
            pendingTasks.add(() -> loadInterstitial(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        isInterstitialReady = false;
        loadedInterstitialId = adUnitId;

        UnityAds.load(adUnitId, new UnityAdsLoadOptions(), new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                Log.d(TAG, "Unity Interstitial loaded: " + placementId);
                isInterstitialReady = true;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                Log.e(TAG, "Unity Interstitial failed: " + message);
                isInterstitialReady = false;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        if (isInterstitialReady && !loadedInterstitialId.isEmpty()) {
            UnityAds.show(activity, loadedInterstitialId, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                @Override
                public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                    Log.e(TAG, "Unity Interstitial show failure: " + message);
                    isInterstitialReady = false;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onUnityAdsShowStart(String placementId) {
                }

                @Override
                public void onUnityAdsShowClick(String placementId) {
                }

                @Override
                public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                    Log.d(TAG, "Unity Interstitial closed");
                    isInterstitialReady = false;
                    if (listener != null) listener.onAdDismissed();
                }
            });
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Rewarded load delayed. Waiting for Unity initialization...");
            pendingTasks.add(() -> loadRewarded(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        isRewardedReady = false;
        loadedRewardedId = adUnitId;

        UnityAds.load(adUnitId, new UnityAdsLoadOptions(), new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                Log.d(TAG, "Unity Rewarded loaded: " + placementId);
                isRewardedReady = true;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                Log.e(TAG, "Unity Rewarded failed: " + message);
                isRewardedReady = false;
                if (listener != null) listener.onAdFailed();
            }
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        if (isRewardedReady && !loadedRewardedId.isEmpty()) {
            UnityAds.show(activity, loadedRewardedId, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                @Override
                public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                    Log.e(TAG, "Unity Rewarded show failure: " + message);
                    isRewardedReady = false;
                    if (listener != null) listener.onAdDismissed();
                }

                @Override
                public void onUnityAdsShowStart(String placementId) {
                }

                @Override
                public void onUnityAdsShowClick(String placementId) {
                }

                @Override
                public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                    isRewardedReady = false;

                    // PERBAIKAN: Trigger onRewardEarned jika selesai
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        if (listener != null) {
                            listener.onRewardEarned();
                            Log.d(TAG, "Unity Reward Earned");
                        }
                    } else {
                        Log.d(TAG, "Unity Rewarded closed before completion");
                    }

                    // PERBAIKAN: WAJIB panggil onAdDismissed agar AdsManager tahu iklan sudah tertutup
                    // dan mengeksekusi unlock konten ke Activity.
                    if (listener != null) {
                        listener.onAdDismissed();
                    }
                }
            });
        } else {
            if (listener != null) listener.onAdDismissed();
        }
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        // Fallback App Open ke Interstitial
        loadInterstitial(activity, adUnitId, listener);
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        // Fallback App Open ke Interstitial
        showInterstitial(activity, listener);
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
        // Form privasi biasanya ditangani menggunakan Google UMP
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        return false;
    }
}