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
 * Given a Persian word, guess what the user may have intended to type.
 * Author: David Yonge-Mallo
 */

package com.example.android.inputmethod.persian;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

/**
 * @author D. L. Yonge-Mallo
 *
 */
public class PersianWordGuesser {
    private Context mContext;
    final static int MAX_TOTAL_GUESSES = 90;
    final static int MAX_RETURNED_GUESSES = 30; // must be strictly < CandidateView.MAX_SUGGESTIONS

    // We recognise 48 Perso-Arabic characters.
    // We also allow a word to have a zero-width space (treat as a 49th character).
    // Furthermore, we have some expressions in our dictionary, and so some
    // entries will have (regular) spaces (treat as a 50th character).
    final static int NUM_VALID_CHARACTERS = 50;
    final static int NUM_MAIN_ARABIC_CHARACTERS = 42;


    private class TrieNode {
        TrieNode children[];
        boolean isTerminal;
        int rank;

        public TrieNode() {
            children = new TrieNode[NUM_VALID_CHARACTERS];
            rank = -1;
            isTerminal = false;
        }

        public void add(String s, int r) {
            if( s.equals("") ) {
                rank = r;
                isTerminal = true;
            } else {
                int index = charToIndex(s.charAt(0));
                if( index != -1 ) {
                    if( children[index] == null ) {
                        children[index] = new TrieNode();
                    }
                    children[index].add(s.substring(1), r);
                }
            }
        }

        private class PartialWord {
            public TrieNode node;
            public String sofar;
            public PartialWord(TrieNode n, String s) {
                node = n;
                sofar = s;
            }
        }

        private void findMatch(Hashtable<String,Integer> guessSet, String s, String sofar, int depth, LinkedList<PartialWord> partialWordList) {
            if( guessSet.size() == MAX_TOTAL_GUESSES ) {
                return;
            }

            if( !sofar.equals(s) ) {
                // Still trying to make the entire word s.
                char c = s.charAt(depth);
                TrieNode child = children[charToIndex(c)];
                if( child != null ) {
                    // So far, so good -- check the next character.
                    child.findMatch(guessSet, s, sofar + c, depth+1, partialWordList);
                }

                // Check for the special cases of mi- and nemi- (add a zero-width non-joiner).
                if( (depth == 2) && s.substring(0,2).equals("\u0645\u06CC") && (s.charAt(2) != '\u200C') ) {
                    children[charToIndex('\u200C')].findMatch(guessSet, s.substring(0,2) + "\u200C" + s.substring(2),
                        "\u0645\u06CC\u200C", depth+1, partialWordList);
                } else if( (depth == 3) && s.substring(0,3).equals("\u0646\u0645\u06CC") && (s.charAt(3) != '\u200C') ) {
                    children[charToIndex('\u200C')].findMatch(guessSet, s.substring(0,3) + "\u200C" + s.substring(3),
                        "\u0646\u0645\u06CC\u200C", depth+1, partialWordList);
                }

                // Inexact vowels: check for alef, vav, and yeh with various diacritics.
                StringBuilder t = new StringBuilder(s);
                if( c == '\u0627' ) {
                    // alef
                    child = children[charToIndex('\u0622')]; // alef with madda above
                    t.setCharAt(depth, '\u0622');
                    if( child != null ) {
                        child.findMatch(guessSet, t.toString(), sofar + '\u0622', depth+1, partialWordList);
                    }
                    child = children[charToIndex('\u0623')]; // alef with hamza above
                    t.setCharAt(depth, '\u0623');
                    if( child != null ) {
                        child.findMatch(guessSet, t.toString(), sofar + '\u0623', depth+1, partialWordList);
                    }
                    child = children[charToIndex('\u0625')]; // alef with hamza below
                    t.setCharAt(depth, '\u0625');
                    if( child != null ) {
                        child.findMatch(guessSet, t.toString(), sofar + '\u0625', depth+1, partialWordList);
                    }
                } else if ( c == '\u0648' ) {
                    // vav
                    child = children[charToIndex('\u0624')]; // vav with hamza above
                    t.setCharAt(depth, '\u0624');
                    if( child != null ) {
                        child.findMatch(guessSet, t.toString(), sofar + '\u0624', depth+1, partialWordList);
                    }
                } else if ( c == '\u06CC' ) {
                    // yeh
                    child = children[charToIndex('\u0626')]; // yeh with hamza above
                    t.setCharAt(depth, '\u0626');
                    if( child != null ) {
                        child.findMatch(guessSet, t.toString(), sofar + '\u0626', depth+1, partialWordList);
                    }
                }

            } else {

                // We've found the node for what the user has typed, add it, and
                // investigate its descendents.
                addDescendents(guessSet, this, s, partialWordList);
            }
        }

        private void addDescendents(Hashtable<String,Integer> guessSet, TrieNode node, String s,
            LinkedList<PartialWord> partialWordList) {

            if ( node.isTerminal ) {
                // We've found a word we're looking for, so add it.
                Integer intRank = (Integer)guessSet.get(s);
                if( ( intRank == null ) || (node.rank > intRank.intValue()) ) {
                    guessSet.put(s, new Integer(node.rank));
                    if( guessSet.size() == MAX_TOTAL_GUESSES ) {
                        return;
                    }
                }
            }

            // We want to add the descendents of the current node.
            // But we want to do this in a breadth-first fashion, so that
            // no branch monopolizes the remaining guesses.
            for( int i = 0; i < NUM_VALID_CHARACTERS; i++ ) {
                TrieNode child = node.children[i];
                if( child != null ) {
                    PartialWord partialWord = new PartialWord(child, s + indexToChar(i));
                    partialWordList.add(partialWord);
                }
            }
        }

        private void findPartialMatches(Hashtable<String,Integer> guessSet, LinkedList<PartialWord> partialWordList) {
            while( ( guessSet.size() < MAX_TOTAL_GUESSES ) && ( partialWordList.size() != 0 )) {
                PartialWord partialWord = partialWordList.remove();
                TrieNode node = partialWord.node;
                String sofar = partialWord.sofar;

                addDescendents(guessSet, node, sofar, partialWordList);
            }
        }

        private boolean hasInvalidCharacters(String s) {
            // Allow only the Persian characters, zero-width non-joiner, and regular space.
            for( char c : s.toCharArray() ) {
                if( !( ( c >= '\u0621' && c <= '\u065E' ) || // main body of Arabic characters
                       (c == '\u067E') || // Persian peh
                       (c == '\u0686') || // Persian cheh
                       (c == '\u0698') || // Persian cheh
                       (c == '\u06A9') || // Persian kaf
                       (c == '\u06AF') || // Persian gaf
                       (c == '\u06CC') || // Persian yeh
                       (c == '\u200C') || // zero-width non-joiner
                       (c == ' ') ) ) {
                    return true;
                }
            }
            return false;
        }

        public void guess(Hashtable<String,Integer> guessSet, String s) {
            if( hasInvalidCharacters(s) ) {
                return;
            }

            LinkedList<PartialWord> partialWordList = new LinkedList<PartialWord>();
            findMatch(guessSet, s, "", 0, partialWordList);
            findPartialMatches(guessSet, partialWordList);
        }

        private int charToIndex(char c) {
            if( ( c >= '\u0621' ) && ( c < '\u0621' + NUM_MAIN_ARABIC_CHARACTERS ) ) {
                return (int)c - 0x0621;
            } else if( c == '\u067E' ) {
                // Persian peh
                return 42;
            } else if( c == '\u0686' ) {
                // Persian cheh
                return 43;
            } else if( c == '\u0698' ) {
                // Persian zheh
                return 44;
            } else if( c == '\u06A9' ) {
                // Persian kaf
                return 45;
            } else if( c == '\u06AF' ) {
                // Persian gaf
                return 46;
            } else if( c == '\u06CC' ) {
                // Persian yeh
                return 47;
            } else if( c == '\u200C' ) {
                // Zero-width space
                return 48;
            } else if( c == ' ' ) {
                // Regular space
                return 49;
            } else {
                // Error!  This should never happen, since the data
                // has been stripped of all invalid characters.
                return -1;
            }
        }

        private char indexToChar(int index) {
            // Inverse of charToIndex.
            if( index >= 0 ) {
                if( index < NUM_MAIN_ARABIC_CHARACTERS ) {
                    return (char)(0x0621+index);
                } else if( index < NUM_VALID_CHARACTERS ) {
                    char persianChars[] = { '\u067E', '\u0686', '\u0698', '\u06A9', '\u06AF', '\u06CC', '\u200C', ' ' };
                    return persianChars[index - NUM_MAIN_ARABIC_CHARACTERS];
                }
            }
            // Error!  Should never happen.
            return '\0';
        }

    }

    private class RankedWord implements Comparable<RankedWord> {
        private String word;
        private int rank;

        public RankedWord(String w, int r) {
            word = w;
            rank = r;
        }

        public String getWord() {
            return word;
        }

        /* public int getRank() {
            return rank;
        }*/

        public int compareTo(RankedWord other) {
            // Note: for rank, higher is better.
            return other.rank - this.rank;
        }
    }

    // The data structures for holding fixed (program-supplied) words
    // and user words, and a count of how many there are.
    static private TrieNode mKnownWords = null;
    static private int mKnownWordsCount;
    static private int mKnownVerbsCount;
    static private LinkedList<String> mSelectedWords = null;

    // Constructor
    public PersianWordGuesser(Context context) {
        mContext = context;
        Resources r = context.getResources();

        //PreferenceManager.setSharedPreferencesMode(0);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Read in the words if we don't already have them.
        if( mKnownWords == null ) {
            mKnownWords = new TrieNode();
            mKnownWordsCount = 0;
            mSelectedWords = new LinkedList<String>();

            try {
                DataInputStream inStream = new DataInputStream(r.openRawResource(R.raw.persianwords));

                boolean endOfFile = false;
                String persianWord;
                while( !endOfFile ) {

                    try {
                        persianWord = inStream.readUTF();
                        mKnownWords.add(persianWord, mKnownWordsCount++);

                    } catch(EOFException e) {
                        // This is perfectly normal.
                        endOfFile = true;
                    }
                }
                inStream.close();

            } catch( IOException e ) {
                // This is bad.
            }
            mKnownVerbsCount = 0;

            // Restore words selected by the user.
            String selectedWordsBundle = sharedPrefs.getString("selected-words", null);
            if( selectedWordsBundle != null ) {
                String[] words = selectedWordsBundle.split("\\n");
                for( int i = 0; i < words.length; i++ ) {
                    selectWord(words[i]);
                }
            }

        }
    }

    public void saveState() {
        // Save the state of the word list.
        //PreferenceManager.setSharedPreferencesMode(0);
        SharedPreferences.Editor sharedPrefsEd = PreferenceManager.getDefaultSharedPreferences(mContext).edit();

        // Concatenate the selected words into one string, delimited by newlines.
        StringBuilder selectedWordsBuilder = new StringBuilder();
        Iterator<String> iterator = mSelectedWords.iterator();
        while( iterator.hasNext() ) {
            String word = iterator.next();
            selectedWordsBuilder.append(word);
            selectedWordsBuilder.append('\n');
        }

        sharedPrefsEd.putString("selected-words", selectedWordsBuilder.toString());
        sharedPrefsEd.commit();
    }

    private void addVerbRoot(String pastStem, String presentStem) {
        addVerbRoot(pastStem, presentStem, null);
    }

    private void addVerbRoot(String pastStem, String presentStem, String colloquialPresentStem) {
        // If pastStem is null, the presentStem is considered colloquial
        // (i.e., the third person ending is -e).  If both stems are
        // supplied, the presentStem is considered formal (i.e., the
        // third person ending is -ad).

        // The following ought to be much shorter, and make use of the patterns
        // in the various forms of conjugation.

        // The possible verb prefixes.
        String mi = "\u0645\u06CC\u200C";               // mi- prefix (with zero-width non-joiner)
        String nemi = "\u0646\u0645\u06CC\u200C";       // nemi- prefix (with zero-width non-joiner)
        String be = "\u0628";                           // be- prefix
        String na = "\u0646";                           // na- prefix

        // If the stem begins with alef with madda, treat it differently.
        if( ( pastStem.length() > 0 ) &&
            ( pastStem.charAt(0) == 'آ' ) ) {
            mi = "\u0645\u06CC";                        // mi- prefix (joined)
            nemi = "\u0646\u0645\u06CC";                // nemi- prefix (joined)
            be = "\u0628\u06CC";                        // bi- prefix
            na = "\u0646\u06CC\u06CC";                  // nay- prefix
            pastStem = "ا" + pastStem.substring(1);
            presentStem = "ا" + presentStem.substring(1);
            if( colloquialPresentStem != null ) {
                colloquialPresentStem =  "ا" + colloquialPresentStem.substring(1);
            }
        }

        // Add words in the reverse order of their likelihood.

        // Negated past forms.
        addVerbRootHelper(nemi, pastStem, false, true);
        addVerbRootHelper(na, pastStem, false, true);

        // Negated present forms.
        addVerbRootHelper(nemi, presentStem, true, true);
        addVerbRootHelper(nemi, colloquialPresentStem, false, false);

        // Past forms.
        addVerbRootHelper(mi, pastStem, false, true);
        addVerbRootHelper(null, pastStem, false, true);

        // Negated subjunctive and imperative forms.
        addVerbRootHelper(na, presentStem, true, true);
        addVerbRootHelper(na, colloquialPresentStem, false, false);

        // Subjunctive and imperative forms.
        addVerbRootHelper(be, presentStem, true, true);
        addVerbRootHelper(be, colloquialPresentStem, false, false);

        // Present forms.
        addVerbRootHelper(mi, presentStem, true, false);
        addVerbRootHelper(mi, colloquialPresentStem, false, false);
    }

    private void addVerbRootHelper(String prefix, String stem, boolean adEnding, boolean bareEnding) {
        if( prefix == null ) {
            prefix = "";
        }
        mKnownWords.add(prefix + stem + "\u0646\u062F", mKnownVerbsCount++);     // -nd
        mKnownWords.add(prefix + stem + "\u06CC\u062F", mKnownVerbsCount++);     // -id
        mKnownWords.add(prefix + stem + "\u06CC\u0645", mKnownVerbsCount++);     // -im
        if( adEnding ||
            ((stem != null) &&
             (stem.length() > 0) &&
             (stem.charAt(stem.length()-1) == '\u0627')) ) {
            // End in -ad when requested, or if the last letter of the stem is alef.
            mKnownWords.add(prefix + stem + "\u062F", mKnownVerbsCount++);       // -ad
        } else {
            mKnownWords.add(prefix + stem + "\u0647", mKnownVerbsCount++);       // -e
        }
        if( bareEnding ) {
            // If ends in yeh, remove it.
            if( stem.charAt(stem.length()-1) == '\u06CC' ) {
                stem = stem.substring(0,stem.length()-1);
            }
            mKnownWords.add(prefix + stem, mKnownVerbsCount++);                  // -(nothing)
        }
        mKnownWords.add(prefix + stem + "\u06CC", mKnownVerbsCount++);           // -i
        mKnownWords.add(prefix + stem + "\u0645", mKnownVerbsCount++);           // -am
    }

    public void selectWord(String word) {
        // User has picked the word, so increase its rank.
        mKnownWords.add(word, mKnownWordsCount++);

        // Add the word to the list of selected words, but first remove it
        // to ensure that it is always added at the end.
        mSelectedWords.remove(word);
        mSelectedWords.add(word);
    }

    public ArrayList<String> guess(String word) {
        // First, get the guesses along with their ranks.
        Hashtable<String,Integer> guessSet = new Hashtable<String,Integer>();
        mKnownWords.guess(guessSet, word);

        // Now, sort them by rank.
        ArrayList<RankedWord> rankedList = new ArrayList<RankedWord>();
        Enumeration<String> words = guessSet.keys();
        while( words.hasMoreElements() ) {
            String persianWord = (String)words.nextElement();
            int rank = (Integer)guessSet.get(persianWord).intValue();
            rankedList.add(new RankedWord(persianWord, rank));
        }
        Collections.sort(rankedList);

        // Copy the words into another list and return it, up to a
        // maximum of MAX_RETURNED_GUESSES.
        ArrayList<String> guessList = new ArrayList<String>();
        for( int i = 0; (i < rankedList.size()) && (i < MAX_RETURNED_GUESSES); i++ ) {
            guessList.add(rankedList.get(i).getWord());
        }
        return guessList;
    }

}
