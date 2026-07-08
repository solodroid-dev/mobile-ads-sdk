package com.solodroid.ads.core.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.solodroid.ads.core.models.AdModel;
import com.google.gson.Gson;

public class AdsPrefManager {

    private static final String PREF_NAME = "solodroid_ads_pref";
    private static final String KEY_ADS_DATA = "ads_data_cache";
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public AdsPrefManager(Context context) {
        // Gunakan PREF_NAME yang unik agar tidak bentrok dengan pref aplikasi utama
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.editor = sharedPreferences.edit();
    }

    /**
     * Simpan objek AdModel (hasil parsing JSON API) ke dalam Local Storage
     */
    public void saveAdsData(AdModel adModel) {
        String json = new Gson().toJson(adModel);
        editor.putString(KEY_ADS_DATA, json);
        editor.apply();
    }

    /**
     * Ambil data iklan yang tersimpan.
     * Return object AdModel jika ada, atau null jika kosong.
     */
    public AdModel getAdsData() {
        String json = sharedPreferences.getString(KEY_ADS_DATA, "");
        if (json.isEmpty()) {
            return null;
        }
        try {
            return new Gson().fromJson(json, AdModel.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Hapus cache data iklan jika diperlukan
     */
    public void clearAdsData() {
        editor.remove(KEY_ADS_DATA);
        editor.apply();
    }
}