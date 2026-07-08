package com.solodroid.ads.gam;

import android.app.Activity;
import android.util.Log;

import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class GamGdpr {

    private static final String TAG = "GamGdpr";
    private final ConsentInformation consentInformation;
    private final Activity activity;

    public interface OnConsentCompleteListener {
        void onConsentComplete();
    }

    public GamGdpr(Activity activity) {
        this.activity = activity;
        this.consentInformation = UserMessagingPlatform.getConsentInformation(activity);
    }

    public void gatherConsent(OnConsentCompleteListener listener) {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            activity,
                            formError -> {
                                if (formError != null) {
                                    Log.w(TAG, "GDPR Form Error: " + formError.getErrorCode() + " - " + formError.getMessage());
                                }
                                if (consentInformation.canRequestAds()) {
                                    listener.onConsentComplete();
                                }
                            }
                    );
                },
                requestConsentError -> {
                    Log.w(TAG, "GDPR Request Error: " + requestConsentError.getErrorCode() + " - " + requestConsentError.getMessage());
                    if (consentInformation.canRequestAds()) {
                        listener.onConsentComplete();
                    }
                }
        );

        if (consentInformation.canRequestAds()) {
            listener.onConsentComplete();
        }
    }

    public boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void showPrivacyOptionsForm(Activity activity, ConsentForm.OnConsentFormDismissedListener listener) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, listener);
    }
}