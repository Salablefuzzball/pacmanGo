package com.example.toukolohilahti.pacmango_native;

import com.example.toukolohilahti.pacmango_native.util.HttpGetQuery;
import com.example.toukolohilahti.pacmango_native.util.HttpPostQuery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class HighScores {

    private class HighScoreRow {
        public int scoreId;
        public String player;
        public int time;
        public int score;
        public int difficulty;
        public int timestamp;

        public HighScoreRow(int scoreId, String player, int time, int score, int difficulty, int timestamp) {
            this.scoreId = scoreId;
            this.player = player;
            this.time = time;
            this.score = score;
            this.difficulty = difficulty;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "HighScoreRow{" +
                    "scoreId=" + scoreId +
                    ", player='" + player + '\'' +
                    ", time=" + time +
                    ", score=" + score +
                    ", difficulty=" + difficulty +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

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

    public int SendHighScore(String player, int time, int score, int difficulty) {
        JSONObject object = new JSONObject();
        try {
            object.put("player", player);
            object.put("time", time);
            object.put("score", score);
            object.put("difficulty", difficulty);
            new HttpPostQuery().execute(url, object.toString()).get();
        } catch (JSONException e) {
            e.printStackTrace();
            return 1;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return 0;
    }

}
