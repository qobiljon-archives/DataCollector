package com.example.datacollector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static com.example.datacollector.MainActivity.TAG;

class Tools {
    static void initData(Context context) {
        Tools.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Tools.executor = Executors.newCachedThreadPool();
    }

    // region Variables
    private static ConnectivityManager connectivityManager;
    private static ExecutorService executor;
    // endregion

    static boolean isConnectedToInternet() {
        if (connectivityManager == null)
            return true;
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    static synchronized int post(Context context, @StringRes int api, @Nullable Pair<String, String> parameter) throws MalformedURLException, InterruptedException, ExecutionException {
        if (!isConnectedToInternet())
            return -1;

        CallAPIForResult callAPI = new CallAPIForResult(context, api);
        if (parameter == null)
            return callAPI.execute().get();
        else
            return callAPI.execute(parameter.first, parameter.second).get();
    }

    static synchronized String post(Context context, @StringRes int api) throws MalformedURLException, InterruptedException, ExecutionException {
        if (!isConnectedToInternet())
            return null;
        return new CallAPIForData(context, api).execute().get();
    }

    static boolean isLocationPermissionDenied(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    static void addBackgroundTask(Runnable runnable) {
        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "addBackgroundTask: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void shutdownBackgroundTasks() {
        executor.shutdownNow();
    }

    static String readCharacterFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        FileReader reader = new FileReader(file);

        char[] buf = new char[128];
        int read;
        while ((read = reader.read(buf)) > 0)
            sb.append(buf, 0, read);
        reader.close();

        if (sb.length() == 0)
            return "";

        if (sb.charAt(sb.length() - 1) == '\n')
            sb = sb.replace(sb.length() - 1, sb.length(), "");

        return sb.toString();
    }


    private static class CallAPIForResult extends AsyncTask<String, Void, Integer> {
        CallAPIForResult(Context context, @StringRes int api) throws MalformedURLException {
            this.url = new URL(context.getString(R.string.post_format, context.getString(api)));
        }

        private URL url;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Integer doInBackground(String... params) {
            boolean parameterPassed = params.length != 0;
            String key = null, value = null; // POST body parameter
            if (parameterPassed) {
                key = params[0];
                value = params[1];
            }

            try {
                HttpURLConnection urlConnection = (HttpURLConnection) this.url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setReadTimeout(10000);
                urlConnection.setConnectTimeout(10000);

                if (parameterPassed) {
                    OutputStream outputStream = urlConnection.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, Charset.defaultCharset()));
                    writer.write(paramToString(key, value));
                    writer.flush();
                    writer.close();
                    outputStream.close();
                }

                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[128];
                int read;
                while ((read = inputStream.read(buffer, 0, buffer.length)) > 0)
                    sb.append(new String(buffer, 0, read, Charset.defaultCharset()));

                JSONObject res = new JSONObject(sb.toString());

                inputStream.close();
                urlConnection.disconnect();

                return res.getInt("result");
            } catch (Exception e) {
                Log.e(TAG, "Tools.post(): " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
        }

        private static String paramToString(String key, String value) throws UnsupportedEncodingException {
            return String.format(
                    "%s=%s",
                    URLEncoder.encode(key, "ASCII"),
                    URLEncoder.encode(value, "ASCII")
            );
        }
    }

    private static class CallAPIForData extends AsyncTask<String, Void, String> {
        CallAPIForData(Context context, @StringRes int api) throws MalformedURLException {
            this.url = new URL(context.getString(R.string.post_format, context.getString(api)));
        }

        private URL url;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... params) {
            boolean parameterPassed = params.length != 0;
            String key = null, value = null; // POST body parameter
            if (parameterPassed) {
                key = params[0];
                value = params[1];
            }

            try {
                HttpURLConnection urlConnection = (HttpURLConnection) this.url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setReadTimeout(10000);
                urlConnection.setConnectTimeout(10000);

                if (parameterPassed) {
                    OutputStream outputStream = urlConnection.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, Charset.defaultCharset()));
                    writer.write(paramToString(key, value));
                    writer.flush();
                    writer.close();
                    outputStream.close();
                }

                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[128];
                int read;
                while ((read = inputStream.read(buffer, 0, buffer.length)) > 0)
                    sb.append(new String(buffer, 0, read, Charset.defaultCharset()));

                inputStream.close();
                urlConnection.disconnect();

                return sb.toString();
            } catch (Exception e) {
                Log.e(TAG, "Tools.post(): " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        private static String paramToString(String key, String value) throws UnsupportedEncodingException {
            return String.format(
                    "%s=%s",
                    URLEncoder.encode(key, "ASCII"),
                    URLEncoder.encode(value, "ASCII")
            );
        }
    }
}
