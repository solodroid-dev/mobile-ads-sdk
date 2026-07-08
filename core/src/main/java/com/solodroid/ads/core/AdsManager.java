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

    // Flag untuk mencegah iklan tumpang tindih
    private boolean isAdShowing = false;
    private Activity currentActivity;

    public static synchronized AdsManager getInstance(Activity activity) {
        if (instance == null) instance = new AdsManager(activity);
        return instance;
    }

    private AdsManager(Activity activity) {
        this.adsPref = new AdsPrefManager(activity);
    }

    public void init(Activity activity) {
        this.currentActivity = activity;

        activity.getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivity = activity;
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, Bundle bundle) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }
        });

        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus()) return;

        mainProvider = getProviderInstance(ads.getMainAds());
        if (mainProvider != null) mainProvider.init(activity, ads);

        if (!ads.getBackupAds().equals("none")) {
            backupProvider = getProviderInstance(ads.getBackupAds());
            if (backupProvider != null) backupProvider.init(activity, ads);
        }

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (currentActivity == null || isAdShowing) return;

        String activityName = currentActivity.getClass().getSimpleName();
        if (activityName.equals("SplashActivity")) {
            Log.d("AdsManager", "Ignore App Open on Splash to prevent stuck.");
            return;
        }

        showAppOpen(currentActivity, () -> Log.d("AdsManager", "App Open dismissed on Return to App"));
    }

    public interface AdFinishedListener {
        void onFinished();
    }

    public interface RewardFinishedListener {
        void onFinished(boolean rewardEarned);
    }

    // --- INTERSTITIAL LOGIC ---
    public void loadInterstitial(Activity activity) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || mainProvider == null) return;

        mainProvider.loadInterstitial(activity, ads.getMainInterstitialId(), new AdInternalListener() {
            @Override
            public void onAdLoaded() {
                isMainLoaded = true;
            }

            @Override
            public void onAdDismissed() {
            }

            @Override
            public void onRewardEarned() {
            }

            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadInterstitial(activity, ads.getBackupInterstitialId(), new AdInternalListener() {
                        @Override
                        public void onAdLoaded() {
                            isBackupLoaded = true;
                        }

                        @Override
                        public void onAdFailed() {
                            isBackupLoaded = false;
                        }

                        @Override
                        public void onAdDismissed() {
                        }

                        @Override
                        public void onRewardEarned() {
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
                clickCount--; // Jangan reset klik jika gagal tayang
                loadInterstitial(activity);
            }
        } else {
            callback.onFinished();
        }
    }

    private void showInterstitialProcess(Activity activity, AdFinishedListener callback) {
        isAdShowing = true;
        AdInternalListener internalListener = new AdInternalListener() {
            @Override
            public void onAdLoaded() {
            }

            @Override
            public void onAdFailed() {
            }

            @Override
            public void onRewardEarned() {
            }

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
        if (ads == null || !ads.isAdStatus() || mainProvider == null) return;

        mainProvider.loadRewarded(activity, ads.getMainRewardedId(), new AdInternalListener() {
            @Override
            public void onAdLoaded() {
                isMainRewardedLoaded = true;
            }

            @Override
            public void onAdDismissed() {
            }

            @Override
            public void onRewardEarned() {
            }

            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadRewarded(activity, ads.getBackupRewardedId(), new AdInternalListener() {
                        @Override
                        public void onAdLoaded() {
                            isBackupRewardedLoaded = true;
                        }

                        @Override
                        public void onAdFailed() {
                            isBackupRewardedLoaded = false;
                        }

                        @Override
                        public void onAdDismissed() {
                        }

                        @Override
                        public void onRewardEarned() {
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

        AdInternalListener internalListener = new AdInternalListener() {
            @Override
            public void onAdLoaded() {
            }

            @Override
            public void onAdFailed() {
            }

            @Override
            public void onRewardEarned() {
                isEarned[0] = true;
            }

            @Override
            public void onAdDismissed() {
                isAdShowing = false;
                callback.onFinished(isEarned[0]);
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
            callback.onFinished(false);
            loadRewarded(activity);
        }
    }

    // --- APP OPEN LOGIC ---
    public void loadAppOpen(Activity activity, AdInternalListener listener) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || mainProvider == null) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        mainProvider.loadAppOpen(activity, ads.getMainAppOpenId(), new AdInternalListener() {
            @Override
            public void onAdDismissed() {
            }

            @Override
            public void onRewardEarned() {
            }

            @Override
            public void onAdLoaded() {
                isMainAppOpenLoaded = true;
                if (listener != null) listener.onAdLoaded();
            }

            @Override
            public void onAdFailed() {
                if (backupProvider != null) {
                    backupProvider.loadAppOpen(activity, ads.getBackupAppOpenId(), new AdInternalListener() {
                        @Override
                        public void onAdDismissed() {
                        }

                        @Override
                        public void onRewardEarned() {
                        }

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
        AdInternalListener internalListener = new AdInternalListener() {
            @Override
            public void onAdLoaded() {
            }

            @Override
            public void onAdFailed() {
            }

            @Override
            public void onRewardEarned() {
            }

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

    // --- REVISI: BANNER & NATIVE FALLBACK ---
    public void loadBanner(Activity activity, ViewGroup container) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || mainProvider == null) return;

        mainProvider.loadBanner(activity, container, ads.getMainBannerId(), new AdInternalListener() {
            @Override
            public void onAdLoaded() {
            }

            @Override
            public void onAdDismissed() {
            }

            @Override
            public void onRewardEarned() {
            }

            @Override
            public void onAdFailed() {
                // Panggil backup jika main provider gagal
                if (backupProvider != null) {
                    backupProvider.loadBanner(activity, container, ads.getBackupBannerId(), null);
                }
            }
        });
    }

    public void loadNative(Activity activity, ViewGroup container) {
        AdModel ads = adsPref.getAdsData();
        if (ads == null || !ads.isAdStatus() || mainProvider == null) return;

        mainProvider.loadNative(activity, container, ads.getMainNativeId(), new AdInternalListener() {
            @Override
            public void onAdLoaded() {
            }

            @Override
            public void onAdDismissed() {
            }

            @Override
            public void onRewardEarned() {
            }

            @Override
            public void onAdFailed() {
                // Panggil backup jika main provider gagal
                if (backupProvider != null) {
                    backupProvider.loadNative(activity, container, ads.getBackupNativeId(), null);
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