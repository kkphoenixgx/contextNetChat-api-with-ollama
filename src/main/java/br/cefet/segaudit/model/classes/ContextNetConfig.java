package br.cefet.segaudit.model.classes;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ContextNetConfig {
    public String gatewayIP;
    public int gatewayPort;
    @JsonProperty("agentUUID") // Mapeia o campo JSON "agentUUID" para este atributo
    public UUID myUUID;
    @JsonProperty("destinationUUID") // Mapeia o campo JSON "destinationUUID" para este atributo
    public UUID destinationUUID;
}
