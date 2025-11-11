package br.cefet.segaudit.service;

import br.cefet.segaudit.Sender;
import lac.cnclib.net.NodeConnection;
import lac.cnclib.net.NodeConnectionListener;
import lac.cnclib.sddl.message.Message;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import br.cefet.segaudit.model.classes.ContextNetConfig;

public class ContextNetClient implements NodeConnectionListener {
    private final UUID myUUID;
    private final UUID destinationUUID;
    private final String gatewayIP;
    private final int gatewayPort;
    private Sender sender;
    private Consumer<String> messageHandler;
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isConnected = false;

    public ContextNetClient(ContextNetConfig config, Consumer<String> messageHandler) {
        this.gatewayIP = config.gatewayIP;
        this.gatewayPort = config.gatewayPort;
        this.myUUID = config.myUUID;
        this.destinationUUID = config.destinationUUID;
        this.messageHandler = messageHandler;

        System.out.println("Conectando ao gateway " + gatewayIP + ":" + gatewayPort);
        System.out.println("De: " + myUUID + " - Para: " + destinationUUID);

        this.sender = new Sender(gatewayIP, gatewayPort, myUUID, destinationUUID, this::handleIncomingMessage);
        this.sender.setConnectionListener(this);
        //enqueueMessage("<mid1," + destinationUUID + ",tell," + myUUID + ",numeroDaSorte(3337)>");
    }

    private void handleIncomingMessage(String message) {
        System.out.println("[WebSocket] Recebido da ContextNet: " + message);
        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public void sendToContextNet(String message) {
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }
        enqueueMessage(message);
    }

    private void enqueueMessage(String message) {
        if (isConnected) {
            System.out.println("[WebSocket] Enviando para ContextNet: " + message);
            sender.sendMessage(message);
        } else {
            System.out.println("[WebSocket] Conexão ainda não estabelecida. Enfileirando: " + message);
            messageQueue.add(message);
        }
    }

    @Override
    public void connected(NodeConnection remoteCon) {
        System.out.println("[WebSocket] Conexão UDP estabelecida com ContextNet.");
        isConnected = true;

        while (!messageQueue.isEmpty()) {
            String msg = messageQueue.poll();
            System.out.println("[WebSocket] Enviando mensagem da fila: " + msg);
            sender.sendMessage(msg);
        }
    }

    @Override
    public void newMessageReceived(NodeConnection arg0, Message message) {
        String received = (String) message.getContentObject();
        System.out.println("[WebSocket] Mensagem recebida Intern: " + received);
    }

    @Override
    public void reconnected(NodeConnection remoteCon, java.net.SocketAddress endPoint, boolean wasHandover,
            boolean wasMandatory) {
    }

    @Override
    public void disconnected(NodeConnection remoteCon) {
    }

    @Override
    public void unsentMessages(NodeConnection remoteCon, java.util.List<Message> unsentMessages) {
    }

    @Override
    public void internalException(NodeConnection remoteCon, Exception e) {
    }
}
