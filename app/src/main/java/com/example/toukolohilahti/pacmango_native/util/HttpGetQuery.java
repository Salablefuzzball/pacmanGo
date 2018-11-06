package com.example.toukolohilahti.pacmango_native.util;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpGetQuery extends AsyncTask<String, String, JSONArray> {

    public HttpGetQuery(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected JSONArray doInBackground(String... params) {
        String urlString = params[0]; // URL to call

        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");


            urlConnection.connect();
            System.out.println(urlConnection.getResponseCode());
            if(urlConnection.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                JSONArray jsonResponse = new JSONArray(response.toString());
                return jsonResponse;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
