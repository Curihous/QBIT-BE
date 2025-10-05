package com.curihous.qbit.alpaca.dto.internal;

// Alpaca 연결 상태 DTO
public class AlpacaConnectionStatus {
    private final boolean connected;
    private final boolean paperTrading;
    private final String connectionStatus;
    private final boolean tokenExpired;

    public AlpacaConnectionStatus(boolean connected, boolean paperTrading, String connectionStatus, boolean tokenExpired) {
        this.connected = connected;
        this.paperTrading = paperTrading;
        this.connectionStatus = connectionStatus;
        this.tokenExpired = tokenExpired;
    }

    public boolean isConnected() { return connected; }
    public boolean isPaperTrading() { return paperTrading; }
    public String getConnectionStatus() { return connectionStatus; }
    public boolean isTokenExpired() { return tokenExpired; }
}
