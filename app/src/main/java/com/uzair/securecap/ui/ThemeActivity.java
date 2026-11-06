package com.uzair.securecap.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import com.uzair.securecap.Preferences;

/** Applies the user's theme override without changing the device-wide theme. */
public abstract class ThemeActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        String theme = new Preferences(base).theme();
        if ("system".equals(theme)) {
            super.attachBaseContext(base);
            return;
        }
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | ("dark".equals(theme) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }
}
