package org.investpro;

public class Game {
    public String gameShortName;
    public String shortName;
    public String gameId;
    public String creatorUserId;
    public String creatorUserName;
    public String gameShortCode;

    public Game(String gameShortName, String shortName, String gameId, String creatorUserId, String creatorUserName, String gameShortCode) {
        this.gameShortName = gameShortName;
        this.shortName = shortName;
        this.gameId = gameId;
        this.creatorUserId = creatorUserId;
        this.creatorUserName = creatorUserName;
        this.gameShortCode = gameShortCode;
    }
}
