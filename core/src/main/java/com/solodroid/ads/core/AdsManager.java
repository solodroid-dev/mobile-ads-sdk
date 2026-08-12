package com.solodroid.ads.core;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.solodroid.ads.core.models.AdModel;
import com.solodroid.ads.core.utils.AdsPrefManager;

public class AdsManager implements DefaultLifecycleObserver {

    private static AdsManager instance;
    private final AdsPrefManager adsPref;
    private AdProvider mainProvider, backupProvider;
    private int clickCount = 0;

    private boolean isMainLoaded = false, isBackupLoaded = false;
    private boolean isMainRewardedLoaded = false, isBackupRewardedLoaded = false;
    private boolean isMainAppOpenLoaded = false, isBackupAppOpenLoaded = false;

    private boolean isAdShowing = false;
    private Activity currentActivity;

    public static synchronized AdsManager getInstance(Activity activity) {
        if (instance == null) instance = new AdsManager(activity);
        return instance;
    }

    private AdsManager(Activity activity) {
        this.adsPref = new AdsPrefManager(activity);
    }

    public interface InitializationListener {
        void onInitComplete();
    }

    public interface AdFinishedListener {
        void onFinished();
    }

    public interface RewardFinishedListener {
        void onRewardEarned();
        void onAdClosed();
        void onAdFailed();
    }

    private abstract static class SimpleAdListener implements AdInternalListener {
        @Override public void onAdLoaded() {}
        @Override public void onAdFailed() {}
        @Override public void onAdDismissed() {}
        @Override public void onRewardEarned() {}
    }

    // --- INIT LOGIC ---
    public void init(Activity activity, InitializationListener listener) {
        this.currentActivity = activity;

        activity.getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) { currentActivity = activity; }
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityCreated(@NonNull Activity activity, Bundle bundle) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
        });

        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus()) {
            if (listener != null) listener.onInitComplete();
            return;
        }

        mainProvider = getProviderInstance(ads.getMainAds());
        if (mainProvider != null) {
            mainProvider.init(activity, ads, new InitializationListener() {
                @Override
                public void onInitComplete() {
                    if (!ads.getBackupAds().equals("none")) {
                        backupProvider = getProviderInstance(ads.getBackupAds());
                        if (backupProvider != null) backupProvider.init(activity, ads, null);
                    }

                    // PERBAIKAN 1: Pindahkan observer ke SINI.
                    // Jangan daftarkan observer sebelum inisialisasi AdMob benar-benar selesai.
                    ProcessLifecycleOwner.get().getLifecycle().addObserver(AdsManager.this);

                    if (listener != null) listener.onInitComplete();
                }
            });
        } else {
            ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
            if (listener != null) listener.onInitComplete();
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (currentActivity == null || isAdShowing) return;

        String activityName = currentActivity.getClass().getSimpleName();

        // PERBAIKAN 2: Gunakan toLowerCase().contains("splash") agar fleksibel.
        // Ini akan lolos untuk "SplashActivity" maupun "ActivitySplash".
        if (activityName.toLowerCase().contains("splash")) {
            Log.d("AdsManager", "Ignore App Open on Splash to prevent stuck.");
            return;
        }

        showAppOpen(currentActivity, () -> Log.d("AdsManager", "App Open dismissed on Return to App"));
    }

    // --- INTERSTITIAL LOGIC ---
    public void loadInterstitial(Activity activity) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || !ads.isInterstitialStatus() || mainProvider == null) return;

        mainProvider.loadInterstitial(activity, ads.getMainInterstitialId(), new SimpleAdListener() {
            @Override
            public void onAdLoaded() {
                isMainLoaded = true;
            }

            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadInterstitial(activity, ads.getBackupInterstitialId(), new SimpleAdListener() {
                        @Override
                        public void onAdLoaded() {
                            isBackupLoaded = true;
                        }

                        @Override
                        public void onAdFailed() {
                            isBackupLoaded = false;
                        }
                    });
                }
            }
        });
    }

    public void showInterstitial(Activity activity, boolean useInterval, AdFinishedListener callback) {
        this.currentActivity = activity;
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus()) {
            callback.onFinished();
            return;
        }

        if (!useInterval) {
            showInterstitialProcess(activity, callback);
            return;
        }

        clickCount++;
        if (clickCount >= ads.getInterstitialInterval()) {
            if (isMainLoaded || isBackupLoaded) {
                showInterstitialProcess(activity, callback);
                clickCount = 0;
            } else {
                callback.onFinished();
                clickCount--;
                loadInterstitial(activity);
            }
        } else {
            callback.onFinished();
        }
    }

    private void showInterstitialProcess(Activity activity, AdFinishedListener callback) {
        isAdShowing = true;
        SimpleAdListener internalListener = new SimpleAdListener() {
            @Override
            public void onAdDismissed() {
                isAdShowing = false;
                callback.onFinished();
                loadInterstitial(activity);
            }
        };

        if (isMainLoaded && mainProvider != null) {
            mainProvider.showInterstitial(activity, internalListener);
            isMainLoaded = false;
        } else if (isBackupLoaded && backupProvider != null) {
            backupProvider.showInterstitial(activity, internalListener);
            isBackupLoaded = false;
        } else {
            isAdShowing = false;
            callback.onFinished();
            loadInterstitial(activity);
        }
    }

    // --- REWARDED LOGIC ---
    public void loadRewarded(Activity activity) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || !ads.isRewardedStatus() || mainProvider == null) return;

        mainProvider.loadRewarded(activity, ads.getMainRewardedId(), new SimpleAdListener() {
            @Override
            public void onAdLoaded() {
                isMainRewardedLoaded = true;
            }

            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadRewarded(activity, ads.getBackupRewardedId(), new SimpleAdListener() {
                        @Override
                        public void onAdLoaded() {
                            isBackupRewardedLoaded = true;
                        }

                        @Override
                        public void onAdFailed() {
                            isBackupRewardedLoaded = false;
                        }
                    });
                }
            }
        });
    }

    public void showRewarded(Activity activity, RewardFinishedListener callback) {
        this.currentActivity = activity;
        isAdShowing = true;
        final boolean[] isEarned = {false};

        SimpleAdListener internalListener = new SimpleAdListener() {
            @Override
            public void onAdFailed() {
                isAdShowing = false;
                callback.onAdFailed();
                loadRewarded(activity);
            }

            @Override
            public void onRewardEarned() {
                isEarned[0] = true;
            }

            @Override
            public void onAdDismissed() {
                isAdShowing = false;
                if (isEarned[0]) {
                    callback.onRewardEarned();
                } else {
                    callback.onAdClosed();
                }
                loadRewarded(activity);
            }
        };

        if (isMainRewardedLoaded && mainProvider != null) {
            mainProvider.showRewarded(activity, internalListener);
            isMainRewardedLoaded = false;
        } else if (isBackupRewardedLoaded && backupProvider != null) {
            backupProvider.showRewarded(activity, internalListener);
            isBackupRewardedLoaded = false;
        } else {
            isAdShowing = false;
            callback.onAdFailed();
            loadRewarded(activity);
        }
    }

    // --- APP OPEN LOGIC ---
    public void loadAppOpen(Activity activity, AdInternalListener listener) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || !ads.isAppOpenStatus() || mainProvider == null) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        mainProvider.loadAppOpen(activity, ads.getMainAppOpenId(), new SimpleAdListener() {
            @Override
            public void onAdLoaded() {
                isMainAppOpenLoaded = true;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadAppOpen(activity, ads.getBackupAppOpenId(), new SimpleAdListener() {
                        @Override
                        public void onAdLoaded() {
                            isBackupAppOpenLoaded = true;
                            if (listener != null) listener.onAdLoaded();
                        }

                        @Override
                        public void onAdFailed() {
                            isBackupAppOpenLoaded = false;
                            if (listener != null) listener.onAdFailed();
                        }
                    });
                } else {
                    if (listener != null) listener.onAdFailed();
                }
            }
        });
    }

    public void showAppOpen(Activity activity, AdFinishedListener callback) {
        this.currentActivity = activity;
        if (isAdShowing) {
            callback.onFinished();
            return;
        }

        isAdShowing = true;
        SimpleAdListener internalListener = new SimpleAdListener() {
            @Override
            public void onAdDismissed() {
                isAdShowing = false;
                callback.onFinished();
                loadAppOpen(activity, null);
            }
        };

        if (isMainAppOpenLoaded && mainProvider != null) {
            mainProvider.showAppOpen(activity, internalListener);
            isMainAppOpenLoaded = false;
        } else if (isBackupAppOpenLoaded && backupProvider != null) {
            backupProvider.showAppOpen(activity, internalListener);
            isBackupAppOpenLoaded = false;
        } else {
            isAdShowing = false;
            callback.onFinished();
            loadAppOpen(activity, null);
        }
    }

    // --- BANNER & NATIVE ---
    public void loadBanner(Activity activity, ViewGroup container) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || !ads.isBannerStatus() || mainProvider == null) return;

        mainProvider.loadBanner(activity, container, ads.getMainBannerId(), new SimpleAdListener() {
            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadBanner(activity, container, ads.getBackupBannerId(), null);
                }
            }
        });
    }

    public void loadNative(Activity activity, ViewGroup container) {
        loadNative(activity, container, "medium");
    }

    public void loadNative(Activity activity, ViewGroup container, String style) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || !ads.isNativeStatus() || mainProvider == null) return;

        mainProvider.loadNative(activity, container, ads.getMainNativeId(), style, new SimpleAdListener() {
            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadNative(activity, container, ads.getBackupNativeId(), style, null);
                }
            }
        });
    }

    public void showPrivacyOptions(Activity activity) {
        if (mainProvider != null) {
            mainProvider.showPrivacyOptions(activity);
        } else if (backupProvider != null) {
            backupProvider.showPrivacyOptions(activity);
        }
    }

    public boolean isPrivacyOptionsRequired(Activity activity) {
        if (mainProvider != null) {
            return mainProvider.isPrivacyOptionsRequired(activity);
        } else if (backupProvider != null) {
            return backupProvider.isPrivacyOptionsRequired(activity);
        }
        return false;
    }

    private AdProvider getProviderInstance(String adNetwork) {
        String className = "";
        switch (adNetwork) {
            case "admob":
                className = "com.solodroid.ads.admob.AdMobProvider";
                break;
            case "google_ad_manager":
                className = "com.solodroid.ads.gam.GamProvider";
                break;
            case "facebook":
                className = "com.solodroid.ads.facebook.FacebookProvider";
                break;
            case "startapp":
                className = "com.solodroid.ads.startapp.StartAppProvider";
                break;
            case "unity":
                className = "com.solodroid.ads.unity.UnityProvider";
                break;
            case "applovin_max":
                className = "com.solodroid.ads.applovin.AppLovinMaxProvider";
                break;
            case "applovin_discovery":
                className = "com.solodroid.ads.applovin.AppLovinDiscoveryProvider";
                break;
            case "ironsource":
                className = "com.solodroid.ads.ironsource.IronSourceProvider";
                break;
        }
        try {
            return (AdProvider) Class.forName(className).newInstance();
        } catch (Exception e) {
            return null;
        }
    }
}