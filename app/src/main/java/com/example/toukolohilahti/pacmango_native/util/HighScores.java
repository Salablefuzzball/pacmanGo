package com.example.toukolohilahti.pacmango_native.util;

import com.example.toukolohilahti.pacmango_native.util.HttpGetQuery;
import com.example.toukolohilahti.pacmango_native.util.HttpPostQuery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class HighScores {

    private String url = "http://pacmango.dy.fi:6565/highscore";

    public HighScores() {

    }

    public ArrayList<HighScoreRow> getHighScores() {
        ArrayList<HighScoreRow> results = new ArrayList<>();
        try {
            JSONArray response = new HttpGetQuery().execute(url).get();
            for (int index = 0; index < response.length(); index++) {
                JSONObject obj = response.getJSONObject(index);
                results.add(new HighScoreRow(obj.getInt("score_id"), obj.getString("player"), obj.getInt("time"),obj.getInt("score"), obj.getInt("difficulty"), obj.getInt("timestamp")));
            }
            return results;
        } catch (ExecutionException | JSONException | InterruptedException e) {
            e.printStackTrace();
        }
        return results;
    }

    public void SendHighScore(String player, int time, int score, int difficulty) {
        JSONObject object = new JSONObject();
        try {
            object.put("player", player);
            object.put("time", time);
            object.put("score", score);
            object.put("difficulty", difficulty);
            new HttpPostQuery().execute(url, object.toString()).get();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

}
