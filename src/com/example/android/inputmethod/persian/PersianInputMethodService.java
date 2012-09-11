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
 * modified by: David Yonge-Mallo
 */

package com.example.android.inputmethod.persian;

import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.ExtractEditText;
import android.preference.PreferenceManager;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class PersianInputMethodService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener, OnSharedPreferenceChangeListener {
    static final boolean DEBUG = false;

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;

    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;

    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    // private long mLastShiftTime;
    private long mMetaState;

    private PersianKeyboard mSymbolsKeyboard;
    private PersianKeyboard mSymbolsShiftedKeyboard;
    private PersianKeyboard mStandardKeyboard;

    private PersianKeyboard mCurKeyboard;

    private String mWordSeparators;

    // Preferences settings.
    private boolean mPrefSelectSuggestion;
    private boolean mPrefUseReducedKeys;
    private boolean mPrefPreferFullscreenMode;
    private boolean mPrefShowRedundantKeyboard;

    // Persian vocabulary
    static private PersianWordGuesser mGuesser = null;
    static private ArrayList<String> mCandidateList = null;
    static private String mBestGuess = null;

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mWordSeparators = getResources().getString(R.string.word_separators);

        // Initialise the Persian word mGuesser (and restore its state).
        if( mGuesser == null ) {
            mGuesser = new PersianWordGuesser(getBaseContext());
        }
        if( mCandidateList == null ) {
            mCandidateList = new ArrayList<String>();
        }

        // Register the listener for a shared preference change.
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    /*
     * Called when service is no longer used and is being removed.
     */
    @Override public void onDestroy() {
        // Save the selected words, before discarding them.
        // However, do NOT set mGuesser to null here, as the updateCandidates
        // is sometimes called after onDestroy.
        mGuesser.saveState();

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        // Release everything.
        super.onDestroy();
    }

    // Implements the interface for OnSharedPreferenceChangeListener.
    public void onSharedPreferenceChanged(final SharedPreferences sharedPrefs, final String key) {
        // If shared preferences have changed, update the values.

        if (key.equals(Preferences.KEY_SELECT_SUGGESTION_CHECKBOX_PREFERENCE)) {
            mPrefSelectSuggestion = sharedPrefs.getBoolean(Preferences.KEY_SELECT_SUGGESTION_CHECKBOX_PREFERENCE, true);

        } else if (key.equals(Preferences.KEY_USE_REDUCED_KEYS_CHECKBOX_PREFERENCE)) {
            mPrefUseReducedKeys = sharedPrefs.getBoolean(Preferences.KEY_USE_REDUCED_KEYS_CHECKBOX_PREFERENCE, false);
            configureKeyboards();

        } else if (key.equals(Preferences.KEY_PREFER_FULLSCREEN_CHECKBOX_PREFERENCE)) {
            mPrefPreferFullscreenMode = sharedPrefs.getBoolean(Preferences.KEY_PREFER_FULLSCREEN_CHECKBOX_PREFERENCE, false);

        } else if (key.equals(Preferences.KEY_SHOW_REDUNDANT_KEYBOARD_CHECKBOX_PREFERENCE)) {
            mPrefShowRedundantKeyboard = sharedPrefs.getBoolean(Preferences.KEY_SHOW_REDUNDANT_KEYBOARD_CHECKBOX_PREFERENCE, true);
        }

    }

    /*
     * Configure the keyboard views, depending on the preferences.
     */
    private void configureKeyboards()
    {
        if( mPrefUseReducedKeys ) {
            mStandardKeyboard = new PersianKeyboard(this, R.xml.reduced_keys);
        } else {
            mStandardKeyboard = new PersianKeyboard(this, R.xml.standard);
        }
        mSymbolsKeyboard = new PersianKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new PersianKeyboard(this, R.xml.symbols);
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        // Get the preferences.
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Input preferences.
        mPrefSelectSuggestion = sharedPrefs.getBoolean(Preferences.KEY_SELECT_SUGGESTION_CHECKBOX_PREFERENCE, true);

        // Display preferences.
        mPrefUseReducedKeys = sharedPrefs.getBoolean(Preferences.KEY_USE_REDUCED_KEYS_CHECKBOX_PREFERENCE, false);
        mPrefPreferFullscreenMode = sharedPrefs.getBoolean(Preferences.KEY_PREFER_FULLSCREEN_CHECKBOX_PREFERENCE, false);
        mPrefShowRedundantKeyboard = sharedPrefs.getBoolean(Preferences.KEY_SHOW_REDUNDANT_KEYBOARD_CHECKBOX_PREFERENCE, true);

        if (mStandardKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }

        // Configure the keyboards.
        configureKeyboards();
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (KeyboardView) getLayoutInflater().inflate(
                R.layout.persian_keyboard, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mStandardKeyboard);
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        setCandidatesViewShown(true);
        return mCandidateView;
    }

    /**
     * Called by the framework to create the layout for showing extracted text.
     */
    @Override public View onCreateExtractTextView() {
        View view = super.onCreateExtractTextView();
        ExtractEditText inputExtractEditText = (ExtractEditText)view.findViewById(android.R.id.inputExtractEditText);
        inputExtractEditText.setGravity(android.view.Gravity.RIGHT);
        inputExtractEditText.setScrollBarStyle(android.view.View.SCROLLBARS_OUTSIDE_OVERLAY);
        return view;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();

        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }

        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mStandardKeyboard;
                mPredictionOn = true;

                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false;
                }

                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false;
                }

                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // on it displaying its own UI.
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mStandardKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();

        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);

        mCurKeyboard = mStandardKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }

        // Save the selected words.
        mGuesser.saveState();
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        //boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            //dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }

        onKey(c, null);

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            /* case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false; */

            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    InputConnection ic = getCurrentInputConnection();
                    if( ic != null ) {
                        if (!event.isAltPressed() &&
                                !event.isSymPressed()) {
                            // Neither Alt nor Sym is pressed (but Shift might be pressed).

                            // The following code is really ugly.  It should
                            // probably be changed to use an array or hash table.
                            switch(keyCode) {
                                // Row 1
                                case KeyEvent.KEYCODE_Q:
                                    mComposing.append("\u0636");
                                      break;

                                case KeyEvent.KEYCODE_W:
                                    mComposing.append("\u0635");
                                      break;

                                case KeyEvent.KEYCODE_E:
                                    mComposing.append("\u062B");
                                      break;

                                case KeyEvent.KEYCODE_R:
                                    mComposing.append("\u0642");
                                    break;

                                case KeyEvent.KEYCODE_T:
                                    mComposing.append("\u0641");
                                    break;

                                case KeyEvent.KEYCODE_Y:
                                    mComposing.append("\u063A");
                                    break;

                                case KeyEvent.KEYCODE_U:
                                      mComposing.append("\u0639");
                                      break;

                                case KeyEvent.KEYCODE_I:
                                      mComposing.append("\u0647");
                                      break;

                                case KeyEvent.KEYCODE_O:
                                      mComposing.append("\u062E");
                                      break;

                                case KeyEvent.KEYCODE_P:
                                      mComposing.append("\u062D");
                                      break;
                                  // Note that "[" and "]" are treated as Alt-V and Alt-B.

                                  // Row 2
                                case KeyEvent.KEYCODE_A:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's vav with hamza above.
                                        mComposing.append("\u0624");
                                    } else {
                                        // Regular shin.
                                          mComposing.append("\u0634");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_S:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's yeh with hamza above.
                                        mComposing.append("\u0626");
                                    } else {
                                        // Regular sin.
                                          mComposing.append("\u0633");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_D:
                                      mComposing.append("\u06CC");
                                      break;

                                case KeyEvent.KEYCODE_F:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's alef with hamza below.
                                        mComposing.append("\u0625");
                                    } else {
                                        // Regular beh.
                                          mComposing.append("\u0628");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_G:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's alef with hamza above.
                                        mComposing.append("\u0623");
                                    } else {
                                        // Regular lam.
                                          mComposing.append("\u0644");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_H:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's an alef with madda above.
                                        mComposing.append("\u0622");
                                    } else {
                                        // Regular alef.
                                          mComposing.append("\u0627");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_J:
                                      mComposing.append("\u062A");
                                      break;

                                case KeyEvent.KEYCODE_K:
                                      mComposing.append("\u0646");
                                      break;

                                case KeyEvent.KEYCODE_L:
                                      mComposing.append("\u0645");
                                      break;
                                  // Note that ";" and "'" are treated as Alt-J and Alt-L.

                                case KeyEvent.KEYCODE_Z:
                                      mComposing.append("\u0638");
                                      break;

                                case KeyEvent.KEYCODE_X:
                                      mComposing.append("\u0637");
                                      break;

                                case KeyEvent.KEYCODE_C:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's a zheh.
                                        mComposing.append("\u0698");
                                    } else {
                                        // Regular zeh.
                                          mComposing.append("\u0632");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_V:
                                      mComposing.append("\u0631");
                                      break;

                                case KeyEvent.KEYCODE_B:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's a zero-width non-joiner.
                                        mComposing.append("\u200C");
                                    } else {
                                        // The letter zal.
                                          mComposing.append("\u0630");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_N:
                                      mComposing.append("\u062F");
                                      break;

                                case KeyEvent.KEYCODE_M:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's a hamza.
                                        mComposing.append("\u0621");
                                    } else {
                                        // The letter peh.
                                          mComposing.append("\u067E");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_COMMA:
                                      if( event.isShiftPressed() ) {
                                        // If shift is pressed, then it's a Persian question mark.
                                        mComposing.append("\u0621");
                                    } else {
                                        // The letter vav.
                                          mComposing.append("\u0648");
                                    }
                                      break;

                                case KeyEvent.KEYCODE_0:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append(")");
                                    } else {
                                        mComposing.append("\u06F0");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_1:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("!");
                                    } else {
                                        mComposing.append("\u06F1");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_2:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\u066C");
                                    } else {
                                        mComposing.append("\u06F2");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_3:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\u066B");
                                    } else {
                                        mComposing.append("\u06F3");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_4:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\uFDFC");
                                    } else {
                                        mComposing.append("\u06F4");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_5:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\u066A");
                                    } else {
                                        mComposing.append("\u06F5");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_6:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\u00D7");
                                    } else {
                                        mComposing.append("\u06F6");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_7:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\u060C");
                                    } else {
                                        mComposing.append("\u06F7");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_8:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("\u002A");
                                    } else {
                                        mComposing.append("\u06F8");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_9:
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("(");
                                    } else {
                                        mComposing.append("\u06F9");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_PERIOD:
                                    // TODO: Find out why this is never reached.
                                    commitTyped(ic);
                                      if( event.isShiftPressed() ) {
                                        mComposing.append("/");
                                    } else {
                                        mComposing.append(".");
                                    }
                                    commitTyped(ic);
                                    break;

                                case KeyEvent.KEYCODE_ENTER:
                                    commitTyped(ic);
                                    // Commit what is currently being typed, but
                                    // let the underlying editor handle the Enter key.
                                    return false;

                                case KeyEvent.KEYCODE_SPACE:
                                    // Handle Space so the insert symbol dialog doesn't show.
                                    commitTyped(ic);
                                    mComposing.append(" ");
                                    commitTyped(ic);
                                    break;

                                  default:
                                    // Let the underlying text editor handle this.
                                      return false;

                            }
                            ic.setComposingText(mComposing, 1);
                            updateShiftKeyState(getCurrentInputEditorInfo());
                              updateCandidates();
                              return true;

                        } else if (event.isAltPressed() &&
                                !event.isShiftPressed() &&
                                !event.isSymPressed()) {
                            // The Alt meta key is pressed.

                            switch(keyCode) {
                                case KeyEvent.KEYCODE_V:
                                    // Alt-V is "[", which is jim.
                                      mComposing.append("\u062C");
                                      break;

                                case KeyEvent.KEYCODE_B:
                                    // Alt-B is "]", which is cheh.
                                    mComposing.append("\u0686");
                                    break;

                                case KeyEvent.KEYCODE_J:
                                    // Alt-J is ";", which is kaf.
                                    mComposing.append("\u06A9");
                                    break;

                                case KeyEvent.KEYCODE_L:
                                    // Alt-L is "'", which is gaf.
                                    mComposing.append("\u06AF");
                                    break;

                                case KeyEvent.KEYCODE_SPACE:
                                    // Handle Alt-Space so the insert symbol dialog doesn't show.
                                    commitTyped(ic);
                                    mComposing.append(" ");
                                    commitTyped(ic);
                                    break;

                                  default:
                                      return false;

                            }
                            ic.setComposingText(mComposing, 1);
                            updateShiftKeyState(getCurrentInputEditorInfo());
                              updateCandidates();
                              return true;

                        }

                    } // if ( ic != NULL )

                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection, boolean isManuallyPicked) {
        if ( !isManuallyPicked && mPrefSelectSuggestion && (mBestGuess != null) ) {
            // If the word is manually picked, don't override the user's choice.
            // Otherwise, if the user has requested to select the suggestion,
            // replace the typed text with the best guess.
            mGuesser.selectWord(mBestGuess);
            mComposing = new StringBuilder(mBestGuess);
        }

        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    private void commitTyped(InputConnection inputConnection) {
        commitTyped(inputConnection, false);
    }


    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null
                && mInputView != null && mStandardKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == PersianKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                current = mStandardKeyboard;
            } else {
                current = mSymbolsKeyboard;
            }
            mInputView.setKeyboard(current);
            if (current == mStandardKeyboard || current == mSymbolsKeyboard) {
                current.setShifted(false);
            }
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        mBestGuess = null;
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                mCandidateList.clear();

                // Add the current composed string to the suggestions, and
                // determine if it is in the word list.
                mCandidateList.add(mComposing.toString());
                boolean isInWordList = false;

                // Add other candidates.
                ArrayList<String> guessList = mGuesser.guess(mComposing.toString());
                if( guessList.size() > 0 ) {
                    mBestGuess = guessList.get(0);
                }
                for( String persianWord : guessList ) {
                    if( persianWord.equals(mComposing.toString()) ) {
                        isInWordList = true;
                    } else{
                        mCandidateList.add(persianWord);
                    }
                }

                // Send the candidates to CandidateView for display.
                setSuggestions(mCandidateList, true, isInWordList);

            } else {
                // No suggestions.
                setSuggestions(null, false, false);
            }
        }
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }

        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mStandardKeyboard == currentKeyboard) {
            mStandardKeyboard.setShifted(false);

        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);

        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            getCurrentInputConnection().commitText(
                    String.valueOf((char) primaryCode), 1);
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // Increase the rank of the selected word.
            mGuesser.selectWord(mCandidateList.get(index));

            // User has selected one of the suggestions, so commit it.
            mComposing = new StringBuilder(mCandidateList.get(index) + " ");
            commitTyped(getCurrentInputConnection(), true);
        }
    }

    public void swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }

    /*
     * Always show the on-screen keyboard (unless dismissed), if that is
     * the user's preference.  The on-screen keyboard would then be
     * displayed even when the hardware keyboard is open.
     */
    @Override public boolean onEvaluateInputViewShown() {
        // If this value is changed for any reason, remember to call updateInputViewShown().
        return mPrefShowRedundantKeyboard || super.onEvaluateInputViewShown();
    }

    /*
     * Show the keyboard in fullscreen mode, depending on the user's preference.
     */
    @Override public boolean onEvaluateFullscreenMode() {
        // If this value is changed for any reason, remember to call updateFullscreenMode().
        return mPrefPreferFullscreenMode || super.onEvaluateFullscreenMode();
    }

    // User has long-pressed the first word, so add it.
    public boolean addWordToDictionary(String word) {
        mGuesser.selectWord(word);
        return true;
    }
}
