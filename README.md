# AI Gateway para ContextNet

Este projeto é um servidor Java construído com Spring Boot que atua como um gateway de IA (`AIGateway`). Ele gerencia múltiplas conexões WebSocket, traduzindo comandos de usuários em linguagem natural para comandos KQML que podem ser executados por um agente na ContextNet.

A arquitetura foi desenhada para ser modular e escalável, permitindo a fácil integração de diferentes modelos de IA no futuro.

Link para o Diagrama da Arquitetura

## Estrutura do Projeto

A aplicação segue uma estrutura padrão de projetos Spring Boot, organizada por funcionalidade.

```
contextnetchat-api/
├── libs/
│   └── contextnet-2.7-spring.jar  # Dependência local da ContextNet
├── src/main/java/br/cefet/segaudit/
│   ├── AIContextManager/            # Lógica de interação com o modelo de IA (Ollama)
│   │   ├── gemma3/                  # Implementação específica para um modelo
│   │   │   └── Gemma3Manager.java
│   │   ├── IO/
│   │   │   └── FileUtil.java
│   │   └── Model/                   # DTOs para a API do Ollama
│   ├── config/                      # Configurações do Spring (WebSocketConfig)
│   ├── controller/                  # Ponto de entrada da aplicação (ContextNetWebSocketController)
│   ├── interfaces/                  # Contratos da aplicação (IModelManagaer)
│   ├── model/                       # DTOs e Factories da aplicação
│   ├── service/                     # Lógica de negócio e clientes de rede
│   │   ├── AIService.java
│   │   └── ContextNetClient.java
│   ├── SegAudit.java                # Classe principal da aplicação Spring Boot
│   └── Sender.java                  # Classe de baixo nível para comunicação com a ContextNet
├── src/main/resources/
│   ├── br/cefet/segaudit/.../gemma3/ # Arquivos de recurso espelhando a estrutura de pacotes
│   │   └── gemma3Context.txt        # Prompt de contexto base para o modelo
│   └── application.properties       # Arquivo principal de configuração
└── pom.xml                          # Arquivo de configuração do Maven
```

## To-Do List

- [x] Assim que a conexão com a web socket morrer, precisa fechar a conexão com o modelo (Manager).
  - *Implementado no método `afterConnectionClosed` do `ContextNetWebSocketController`.*

## Perguntas e Respostas

> **Pergunta:** "Ele recupera o `long[] context` da sessão atual do mapa `activeSessions`. Este contexto é a 'memória' da conversa." O quanto isso é necessário? A gente não precisa só manter esse chat aberto para não precisar mandar de novo o contexto inicial?

**Resposta:** É **absolutamente necessário**. O `long[] context` é a essência do funcionamento stateful da API `/api/generate` do Ollama. Ele é muito mais do que uma forma de evitar reenviar o prompt inicial.

A cada chamada, o Ollama usa o `context` anterior para entender a nova mensagem dentro da "memória" da conversa. Sem ele, cada mensagem seria tratada como o início de uma conversa totalmente nova, e a IA não teria como relacionar comandos sequenciais (ex: "suba 10 metros" e depois "agora vire à esquerda").

Ao receber a resposta, o Ollama envia um **novo** `context` atualizado. É fundamental que o nosso servidor armazene este novo contexto para usá-lo na próxima requisição, mantendo assim a continuidade da conversa.

---

## Fluxo de Execução

Abaixo estão detalhados os dois fluxos principais da aplicação.

### 1. Inicialização da Conexão WebSocket

Este fluxo ocorre quando um novo cliente se conecta ao servidor.

1.  **Conexão e Primeira Mensagem**:
    *   O cliente conecta-se ao endpoint `/ws` e envia um JSON com as configurações da ContextNet.
    *   **`ContextNetWebSocketController.handleTextMessage`** recebe a mensagem e, por ser a primeira, delega para `handleFirstMessage`.

2.  **Orquestração Inicial**:
    *   **`ContextNetWebSocketController.handleFirstMessage`** desserializa a configuração, cria um `ContextNetClient` e inicia a busca pelos planos do agente.
    *   É invocado o método **`ContextNetClient.fetchAgentPlans`**.

3.  **Busca Assíncrona dos Planos**:
    *   **`ContextNetClient.fetchAgentPlans`** cria um `CompletableFuture<String>` (uma "promessa" da resposta), envia a mensagem `(achieve :content (getPlans))` para o agente e retorna a promessa imediatamente.
    *   O `ContextNetWebSocketController` anexa um callback (`.thenAccept(...)`) a essa promessa, agendando os próximos passos sem bloquear o servidor.

4.  **Resposta do Agente**:
    *   O agente responde, e a mensagem é recebida pelo **`Sender.newMessageReceived`**, que a repassa.
    *   **`ContextNetClient.handleIncomingMessage`** recebe a resposta e cumpre a promessa (`future.complete(message)`), entregando os planos do agente.

5.  **Inicialização da IA**:
    *   O cumprimento da promessa dispara o callback no `ContextNetWebSocketController`.
    *   Um `AIService` é criado, que por sua vez chama **`Gemma3Manager.initializeUserSession`**.
    *   Este método concatena o contexto base com os planos recebidos e faz a **primeira chamada** ao Ollama para carregar o contexto inicial e obter a "memória" (`long[] context`).

6.  **Finalização**:
    *   O servidor envia a mensagem "Connection stabilized and IA session ready." para o cliente, que agora pode enviar comandos.

### 2. Processamento de Mensagens do Usuário

Este fluxo ocorre para cada mensagem que o cliente envia após a inicialização.

1.  **Recebimento da Mensagem**:
    *   **`ContextNetWebSocketController.handleTextMessage`** recebe o comando em linguagem natural (ex: "suba 10 metros") e delega para `handleSubsequentMessages`.

2.  **Tradução com IA**:
    *   **`ContextNetWebSocketController.handleSubsequentMessages`** chama **`AIService.getKQMLMessages`**.
    *   O `AIService` delega para **`Gemma3Manager.translateMessage`**.

3.  **Comunicação com Ollama**:
    *   **`Gemma3Manager.translateMessage`** recupera o `context` atual da sessão, cria a requisição e chama **`makeRequest`**.
    *   A requisição `POST` é enviada para a API do Ollama, contendo o prompt do usuário e o contexto da conversa.

4.  **Processamento da Resposta**:
    *   De volta ao `Gemma3Manager`, a resposta do Ollama é processada.
    *   O **novo contexto** (`long[]`) é salvo, atualizando a memória da conversa.
    *   A resposta em texto (ex: `(achieve :content (up(10)))`) é dividida em uma lista de comandos KQML.

5.  **Envio para o Agente**:
    *   O fluxo retorna para o `ContextNetWebSocketController`, que itera sobre a lista de comandos.
    *   Para cada comando, **`ContextNetClient.sendToContextNet`** é chamado, enviando a mensagem pela conexão UDP para o agente final executar a ação.
