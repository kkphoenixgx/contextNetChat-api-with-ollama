package br.cefet.segaudit.service;

import lac.cnclib.net.NodeConnection;
import lac.cnclib.net.NodeConnectionListener;
import lac.cnclib.sddl.message.Message;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Map<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong messageIdCounter = new AtomicLong(0);

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

        // Tenta identificar se esta é uma resposta a uma requisição pendente.
        if (message.startsWith("<")) {
            String innerMessage = message.substring(1, message.length() - 1);
            int firstComma = innerMessage.indexOf(',');

            if (firstComma != -1) {
                String header = innerMessage.substring(0, firstComma);
                if (header.contains("->")) {
                    String originalRequestId = header.substring(header.indexOf("->") + 2);

                    if (pendingRequests.containsKey(originalRequestId)) {
                        CompletableFuture<String> future = pendingRequests.remove(originalRequestId);
                        if (future != null && !future.isDone()) {
                            // Lógica robusta para encontrar o conteúdo, que é a última parte da mensagem.
                            // Ex: <mid,sender,performative,receiver,CONTENT>
                            // O conteúdo começa após a 4ª vírgula.
                            int contentStartIndex = findNthOccurrence(innerMessage, ',', 4);
                            if (contentStartIndex == -1) {
                                throw new IllegalStateException("Invalid KQML message format received from agent: " + message);
                            }
                            String content = innerMessage.substring(contentStartIndex + 1);
                            logger.info("Completing future for request '{}' with content: {}", originalRequestId, content);
                            future.complete(content.trim());
                        }
                    }
                }
            }
        }

        // Independentemente de ser uma resposta ou não, repassamos a mensagem para o cliente WebSocket.
        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    // Helper para encontrar a n-ésima ocorrência de um caractere.
    private int findNthOccurrence(String str, char c, int n) {
        int pos = -1;
        for (int i = 0; i < n; i++) {
            pos = str.indexOf(c, pos + 1);
            if (pos == -1) {
                break;
            }
        }
        return pos;
    }


    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public CompletableFuture<String> fetchAgentPlans() {
        CompletableFuture<String> future = new CompletableFuture<>();
        String messageId = "mid" + messageIdCounter.incrementAndGet();

        pendingRequests.put(messageId, future);

        String getPlansCommand = String.format("<%s,%s,askOne,%s,plans(N)>", messageId, myUUID, destinationUUID);

        logger.info("Requesting agent plans with command: {}", getPlansCommand);
        sendToContextNet(getPlansCommand);

        return future;
    }

    public UUID getDestinationUUID() {
        return destinationUUID;
    }

    public void sendToContextNet(String message) {
        String formattedMessage;
        // Se a mensagem já estiver no formato <...>, use-a como está (para o fetchPlans).
        // Caso contrário, formate-a como uma nova mensagem para o agente.
        if (message.trim().startsWith("<")) {
            formattedMessage = message;
        } else {
            // Formata a mensagem KQML no padrão que o Concierge espera.
            String messageId = "mid" + messageIdCounter.incrementAndGet();
            formattedMessage = String.format("<%s,%s,%s>", messageId, myUUID, message);
        }
        enqueueMessage(formattedMessage);
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
