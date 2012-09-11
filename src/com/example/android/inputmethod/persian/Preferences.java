/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Author: David Yonge-Mallo
 */

package com.example.android.inputmethod.persian;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    public static final String KEY_SELECT_SUGGESTION_CHECKBOX_PREFERENCE = "select_suggestion_checkbox_preference";
    public static final String KEY_GROUP_VARIANTS_CHECKBOX_PREFERENCE = "group_variants_checkbox_preference";
    public static final String KEY_USE_REDUCED_KEYS_CHECKBOX_PREFERENCE = "use_reduced_keys_checkbox_preference";
    public static final String KEY_PREFER_FULLSCREEN_CHECKBOX_PREFERENCE = "prefer_fullscreen_checkbox_preference";
    public static final String KEY_SHOW_REDUNDANT_KEYBOARD_CHECKBOX_PREFERENCE = "show_redundant_keyboard_checkbox_preference";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(final SharedPreferences sharedPrefs, final String key) {
        if (key.equals(KEY_SELECT_SUGGESTION_CHECKBOX_PREFERENCE)) {
        } else if (key.equals(KEY_GROUP_VARIANTS_CHECKBOX_PREFERENCE)) {
        } else if (key.equals(KEY_USE_REDUCED_KEYS_CHECKBOX_PREFERENCE)) {
        } else if (key.equals(KEY_PREFER_FULLSCREEN_CHECKBOX_PREFERENCE)) {
        } else if (key.equals(KEY_SHOW_REDUNDANT_KEYBOARD_CHECKBOX_PREFERENCE)) {
        }
    }

}

