package com.solodroid.ads.core.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AdModel implements Serializable {

    @SerializedName("mainAds")
    private String mainAds;

    @SerializedName("backupAds")
    private String backupAds;

    @SerializedName("adStatus")
    private boolean adStatus;

    @SerializedName("interstitialInterval")
    private int interstitialInterval;

    // --- AD FORMAT STATUS ---
    @SerializedName("bannerStatus")
    private boolean bannerStatus;

    @SerializedName("interstitialStatus")
    private boolean interstitialStatus;

    @SerializedName("nativeStatus")
    private boolean nativeStatus;

    @SerializedName("rewardedStatus")
    private boolean rewardedStatus;

    @SerializedName("appOpenStatus")
    private boolean appOpenStatus;

    // --- MAIN INITIALIZER KEYS ---
    @SerializedName("mainStartappAppId")
    private String mainStartappAppId;

    @SerializedName("mainUnityGameId")
    private String mainUnityGameId;

    @SerializedName("mainIronsourceAppKey")
    private String mainIronsourceAppKey;

    @SerializedName("mainApplovinSdkKey")
    private String mainApplovinSdkKey;

    // --- MAIN UNIT IDS ---
    @SerializedName("mainBannerId")
    private String mainBannerId;

    @SerializedName("mainInterstitialId")
    private String mainInterstitialId;

    @SerializedName("mainNativeId")
    private String mainNativeId;

    @SerializedName("mainRewardedId")
    private String mainRewardedId;

    @SerializedName("mainAppOpenId")
    private String mainAppOpenId;

    // --- BACKUP INITIALIZER KEYS ---
    @SerializedName("backupStartappAppId")
    private String backupStartappAppId;

    @SerializedName("backupUnityGameId")
    private String backupUnityGameId;

    @SerializedName("backupIronsourceAppKey")
    private String backupIronsourceAppKey;

    @SerializedName("backupApplovinSdkKey")
    private String backupApplovinSdkKey;

    // --- BACKUP UNIT IDS ---
    @SerializedName("backupBannerId")
    private String backupBannerId;

    @SerializedName("backupInterstitialId")
    private String backupInterstitialId;

    @SerializedName("backupNativeId")
    private String backupNativeId;

    @SerializedName("backupRewardedId")
    private String backupRewardedId;

    @SerializedName("backupAppOpenId")
    private String backupAppOpenId;

    // --- GETTERS ---

    public String getMainAds() {
        return mainAds;
    }

    public String getBackupAds() {
        return backupAds;
    }

    public boolean isAdStatus() {
        return adStatus;
    }

    public int getInterstitialInterval() {
        return interstitialInterval;
    }

    // Ad Format Status Getters
    public boolean isBannerStatus() {
        return bannerStatus;
    }

    public boolean isInterstitialStatus() {
        return interstitialStatus;
    }

    public boolean isNativeStatus() {
        return nativeStatus;
    }

    public boolean isRewardedStatus() {
        return rewardedStatus;
    }

    public boolean isAppOpenStatus() {
        return appOpenStatus;
    }

    // Main Initializers
    public String getMainStartappAppId() {
        return mainStartappAppId;
    }

    public String getMainUnityGameId() {
        return mainUnityGameId;
    }

    public String getMainIronsourceAppKey() {
        return mainIronsourceAppKey;
    }

    public String getMainApplovinSdkKey() {
        return mainApplovinSdkKey;
    }

    // Main Unit IDs
    public String getMainBannerId() {
        return mainBannerId;
    }

    public String getMainInterstitialId() {
        return mainInterstitialId;
    }

    public String getMainNativeId() {
        return mainNativeId;
    }

    public String getMainRewardedId() {
        return mainRewardedId;
    }

    public String getMainAppOpenId() {
        return mainAppOpenId;
    }

    // Backup Initializers
    public String getBackupStartappAppId() {
        return backupStartappAppId;
    }

    public String getBackupUnityGameId() {
        return backupUnityGameId;
    }

    public String getBackupIronsourceAppKey() {
        return backupIronsourceAppKey;
    }

    public String getBackupApplovinSdkKey() {
        return backupApplovinSdkKey;
    }

    // Backup Unit IDs
    public String getBackupBannerId() {
        return backupBannerId;
    }

    public String getBackupInterstitialId() {
        return backupInterstitialId;
    }

    public String getBackupNativeId() {
        return backupNativeId;
    }

    public String getBackupRewardedId() {
        return backupRewardedId;
    }

    public String getBackupAppOpenId() {
        return backupAppOpenId;
    }
}