/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mpme.sample;

import android.os.Handler;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author j
 */
public class Log {
    public static final String TAG = "DRRadio";

    /**
     * Logfunktion uden TAG som tager et objekt. Sparer bytekode og tid
     */
    public static void d(Object o) {
        log("d " + o);
    }

    /**
     * Logfunktion uden TAG som tager et objekt. Sparer bytekode og tid
     */
    public static void e(Object o, Exception e) {
        log("e " + o + " " + e);
    }

    /**
     * Logfunktion uden TAG som tager et objekt. Sparer bytekode og tid
     */
    public static void e(Exception o) {
        log("e " + o);
        o.printStackTrace();
    }


    public static void e(String TAG, String string, Exception e) {
        log("e " + string + " " + e);
    }

    public static void d(String TAG, String string) {
        log("d " + string);
    }

    public static void i(String TAG, String string) {
        log("i " + string);
    }

    public static void e(String TAG, String string) {
        log("e " + string);
    }

    public static void v(String TAG, String string) {
        log("v " + string);
    }

    static Handler handler = new Handler();

    private static PrintStream originalSystemOut = null;


    static TextView textView = null;

    /**
     * Appends all output written to System.out to a given TextView. <br/>
     * This is handy for showing output of simple programs logging and for logging.<br/>
     * Call <code>systemOutToTextView(null)</code> to stop appending.
     *
     * @param tv The view to print to. If null this cancels previous redirections.
     */
    public static void systemOutToTextView(final TextView tv) {
        textView = tv;

        if (originalSystemOut == null) originalSystemOut = System.out;
        if (tv == null) {
            System.setOut(originalSystemOut);
            return;
        }

        System.setOut(new PrintStream(new OutputStream() {
            public void write(final int arg0) {
                print(tv, Character.toString((char) arg0));
            }

            public void write(final byte[] b, final int off, final int len) {
                print(tv, new String(b, off, len));
            }

            public void write(byte[] b) {
                write(b, 0, b.length);
            }
        }));
    }

    public static boolean scroll_tv_til_bund = true;
    private static int linjer = 0;

    public static void print(final TextView tv, final String text) {
        handler.post(new Runnable() {
            public void run() {
                if (linjer++ > 1000) { // Nulstil for ikke at overbelaste GUI-tråd
                    tv.setText("");
                    linjer = 0;
                }
                tv.append(text);


                // scroll til bund
                if (scroll_tv_til_bund && tv.getParent() instanceof ScrollView) {
                    ScrollView sv = (ScrollView) tv.getParent();
                    sv.scrollTo(0, tv.getHeight());
                }
            }
        });
    }

    public static void log(Object o) {
        String txt = "" + o;
        android.util.Log.d("JJJ", txt);
        //System.out.println(o);
        if (textView != null) print(textView, txt + "\n");
    }


}
