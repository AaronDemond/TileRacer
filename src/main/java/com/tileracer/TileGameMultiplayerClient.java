package com.tileracer;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

final class TileGameMultiplayerClient implements WebSocket.Listener
{
    private final URI serverUri;
    private final Gson gson;
    private final Consumer<TileGameMultiplayerMessage> messageHandler;
    private final Consumer<String> errorHandler;
    private final StringBuilder textBuffer = new StringBuilder();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile WebSocket webSocket;
    private volatile CompletableFuture<WebSocket> connecting;
    private volatile String registeredPlayer = "";

    TileGameMultiplayerClient(
            URI serverUri,
            Gson gson,
            Consumer<TileGameMultiplayerMessage> messageHandler,
            Consumer<String> errorHandler)
    {
        this.serverUri = serverUri;
        this.gson = gson;
        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
    }

    void send(String playerName, TileGameMultiplayerMessage message)
    {
        if (playerName == null || playerName.trim().isEmpty())
        {
            errorHandler.accept("Log in before using Tile Racer multiplayer.");
            return;
        }

        connect(playerName.trim())
                .thenAccept(socket -> socket.sendText(gson.toJson(message), true))
                .exceptionally(throwable ->
                {
                    errorHandler.accept("Tile Racer multiplayer could not send because the websocket is not connected.");
                    return null;
                });
    }

    void ensureRegistered(String playerName)
    {
        if (playerName == null || playerName.trim().isEmpty())
        {
            return;
        }

        connect(playerName.trim());
    }

    boolean isRegisteredAs(String playerName)
    {
        WebSocket socket = webSocket;
        return socket != null && playerName != null && playerName.trim().equals(registeredPlayer);
    }

    void close()
    {
        WebSocket socket = webSocket;
        webSocket = null;
        connecting = null;
        registeredPlayer = "";
        if (socket != null)
        {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Tile Racer plugin stopped");
        }
    }

    private CompletableFuture<WebSocket> connect(String playerName)
    {
        WebSocket socket = webSocket;
        if (socket != null && playerName.equals(registeredPlayer))
        {
            return CompletableFuture.completedFuture(socket);
        }

        CompletableFuture<WebSocket> existing = connecting;
        if (existing != null && !existing.isDone())
        {
            return existing;
        }

        connecting = httpClient.newWebSocketBuilder()
                .buildAsync(serverUri, this)
                .thenCompose(connected ->
                {
                    webSocket = connected;
                    return register(playerName, connected);
                })
                .whenComplete((connected, throwable) ->
                {
                    if (throwable != null)
                    {
                        errorHandler.accept("Tile Racer multiplayer could not connect to " + serverUri + ".");
                        connecting = null;
                    }
                });

        return connecting;
    }

    private CompletableFuture<WebSocket> register(String playerName, WebSocket socket)
    {
        registeredPlayer = playerName;
        TileGameMultiplayerMessage register = new TileGameMultiplayerMessage();
        register.type = "register";
        register.player = playerName;
        return socket.sendText(gson.toJson(register), true).thenApply(unused -> socket);
    }

    @Override
    public void onOpen(WebSocket webSocket)
    {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)
    {
        textBuffer.append(data);
        if (last)
        {
            String json = textBuffer.toString();
            textBuffer.setLength(0);
            try
            {
                messageHandler.accept(gson.fromJson(json, TileGameMultiplayerMessage.class));
            }
            catch (RuntimeException ex)
            {
                errorHandler.accept("Tile Racer multiplayer received invalid JSON.");
            }
        }

        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason)
    {
        this.webSocket = null;
        this.connecting = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error)
    {
        this.webSocket = null;
        this.connecting = null;
        errorHandler.accept("Tile Racer multiplayer websocket error: " + error.getMessage());
    }
}
