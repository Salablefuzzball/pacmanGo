package com.example.toukolohilahti.pacmango_native.util;

public class HighScoreRow extends HighScores {
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

    public String getPlayer() {
        return player;
    }

    public int getScore() {
        return score;
    }
}