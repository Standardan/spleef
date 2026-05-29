package io.github.standardan.spleef.game;

/** The lifecycle of a Spleef match. */
public enum GameState {
    /** Not enough players yet; people can join freely. */
    WAITING,
    /** Enough players joined; a countdown to start is running. */
    COUNTDOWN,
    /** Match in progress. */
    PLAYING,
    /** Match just ended; brief pause before resetting to WAITING. */
    ENDING
}
