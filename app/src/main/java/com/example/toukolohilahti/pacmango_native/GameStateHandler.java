package com.example.toukolohilahti.pacmango_native;

public class GameStateHandler {

    private int gameTime = 60;
    private int points = 0;
    private boolean gameStarted = false;
    private boolean gameOver = false;
    private boolean gameInitialized = false;
    private boolean leaderBoardOpen = false;

    public boolean isLeaderBoardOpen() {
        return leaderBoardOpen;
    }

    public void setLeaderBoardOpen(boolean leaderBoardOpen) {
        this.leaderBoardOpen = leaderBoardOpen;
    }

    public boolean isGameInitialized() {
        return gameInitialized;
    }

    public void newGame() {
        gameOver = false;
        gameInitialized = true;
        gameTime = 60;
        points = 0;
    }

    public void startGame() {
        gameStarted = true;
    }

    public void reduceGameTime(int reduce) {
        gameTime -= reduce;
    }

    public void addGameTime(int add) {
        gameTime += add;
    }

    public void addPoints() {
        points++;
    }

    public int getGameTime() {
        return gameTime;
    }

    public void setGameTime(int gameTime) {
        this.gameTime = gameTime;
    }

    public int getPoints() {
        return points;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }
}
