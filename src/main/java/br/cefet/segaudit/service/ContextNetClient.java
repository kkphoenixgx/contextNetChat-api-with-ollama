package br.cefet.segaudit.service;

import br.cefet.segaudit.Sender;
import lac.cnclib.net.NodeConnection;
import lac.cnclib.net.NodeConnectionListener;
import lac.cnclib.sddl.message.Message;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.cefet.segaudit.model.classes.ContextNetConfig;

public class ContextNetClient implements NodeConnectionListener {
    private static final Logger logger = LoggerFactory.getLogger(ContextNetClient.class);
    private final UUID myUUID;
    private final UUID destinationUUID;
    private final String gatewayIP;
    private final int gatewayPort;
    private Sender sender;
    private Consumer<String> messageHandler;
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isConnected = false;
    private final AtomicReference<CompletableFuture<String>> initialPlanFuture = new AtomicReference<>();

    public ContextNetClient(ContextNetConfig config, Consumer<String> messageHandler) {
        this.gatewayIP = config.gatewayIP;
        this.gatewayPort = config.gatewayPort;
        this.myUUID = config.myUUID;
        this.destinationUUID = config.destinationUUID;
        this.messageHandler = messageHandler;

        logger.info("Connecting to ContextNet gateway at {}:{}", gatewayIP, gatewayPort);
        logger.info("Session UUID: {}, Destination UUID: {}", myUUID, destinationUUID);

        this.sender = new Sender(gatewayIP, gatewayPort, myUUID, destinationUUID, this::handleIncomingMessage);
        this.sender.setConnectionListener(this);
    }

    private void handleIncomingMessage(String message) {
        logger.debug("Received from ContextNet: {}", message);
        CompletableFuture<String> future = initialPlanFuture.get();
        // Se estivermos esperando pela resposta dos planos, complete o Future.
        if (future != null && !future.isDone()) {
            initialPlanFuture.set(null); // Consome o future
            future.complete(message);
        }
        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public CompletableFuture<String> fetchAgentPlans() {
        CompletableFuture<String> future = new CompletableFuture<>();
        initialPlanFuture.set(future);
        sendToContextNet("(achieve :content (getPlans))");

        return future;
    }

    public void sendToContextNet(String message) {
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }
        enqueueMessage(message);
    }

    private void enqueueMessage(String message) {
        if (isConnected) {
            logger.debug("Sending to ContextNet: {}", message);
            sender.sendMessage(message);
        } else {
            logger.warn("Connection not yet established. Enqueuing message: {}", message);
            messageQueue.add(message);
        }
    }

    @Override
    public void connected(NodeConnection remoteCon) {
        logger.info("UDP connection established with ContextNet.");
        isConnected = true;

        while (!messageQueue.isEmpty()) {
            String msg = messageQueue.poll();
            logger.info("Sending enqueued message: {}", msg);
            sender.sendMessage(msg);
        }
    }

    @Override
    public void newMessageReceived(NodeConnection arg0, Message message) {
        // This method appears redundant as the Sender's callback is already handling messages.
        // The primary logic is in handleIncomingMessage.
    }

    @Override
    public void reconnected(NodeConnection remoteCon, java.net.SocketAddress endPoint, boolean wasHandover,
            boolean wasMandatory) {
    }

    @Override
    public void disconnected(NodeConnection remoteCon) {
        logger.warn("Disconnected from ContextNet.");
        isConnected = false;
    }

    @Override
    public void unsentMessages(NodeConnection remoteCon, java.util.List<Message> unsentMessages) {
        logger.warn("There are {} unsent messages.", unsentMessages.size());
    }

    @Override
    public void internalException(NodeConnection remoteCon, Exception e) {
        logger.error("Internal exception in ContextNet connection.", e);
    }
}
