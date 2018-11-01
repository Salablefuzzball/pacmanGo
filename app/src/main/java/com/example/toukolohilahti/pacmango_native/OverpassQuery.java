package com.example.toukolohilahti.pacmango_native;

import android.os.AsyncTask;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class OverpassQuery extends AsyncTask<ArrayList, String, String> {

    public OverpassQuery(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(ArrayList... params) {
        String urlString = params[0].get(0).toString(); // URL to call
        String data = "";
        OutputStream out = null;

        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            out = new BufferedOutputStream(urlConnection.getOutputStream());

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
            writer.write(data);
            writer.flush();
            writer.close();
            out.close();

            urlConnection.connect();
            if(urlConnection.getResponseCode() == 200) {
                return urlConnection.getResponseMessage();
            } else {
                return "";
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "";
        }
    }
}