package br.cefet.segaudit.model.classes;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ContextNetConfig {
    public String gatewayIP;
    public int gatewayPort;
    @JsonProperty("agentUUID")
    public UUID myUUID;
    @JsonProperty("destinationUUID")
    public UUID destinationUUID;
}
