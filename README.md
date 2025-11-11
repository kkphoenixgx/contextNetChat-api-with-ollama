# ContextNet-api Gateway.

Final Project (TCC) for the Information Systems degree.
This Java application, developed with Spring Boot, acts as a **gateway** between a client web application (via WebSocket) and the **ContextNet** network, enabling real-time message sending and receiving.

## Objective

Enable communication between a web frontend and **ContextNet** (a node-based middleware network for IoT), focusing on sending commands and receiving responses through standardized message formats.

---

## Project Structure

```
contextnetchat-api/
├── src/
│   └── main/
│       ├── java/
│       │   └── br/cefet/segaudit/
│       │       ├── service/
│       │       │   └── ContextNetClient.java
│       │       ├── dto/
│       │       │   └── ContextNetConfig.java
│       │       ├── factory/
│       │       │   └── ContextNetClientFactory.java
│       │       ├── controller/
│       │       │   └── ContextNetWebSocketHandler.java
│       │       ├── config/
│       │       │   └── WebSocketConfig.java
│       │       ├── Sender.java
│       └── resources/
│           └── application.properties
├── libs/
│   └── contextnet-2.7-spring.jar
├── pom.xml
└── README.md
```

---

## System Architecture

![Application Architecture](./docs/images/ContextNet-chat-interface-diagram.jpg)

---

## Requirements

* Java 17
* Maven 3.8+
* Spring Boot 3.2+
* ContextNet dependency (`contextnet-2.7-spring.jar`) manually placed in the `libs/` directory

---

## How to Run

1. **Clone the project**

   ```bash
   git clone https://github.com/gustavoxav/contextNetChat-api.git
   cd contextnet-encrypt
   ```

2. **Ensure the `contextnet-2.7-spring.jar` file is present**
   Place the provided JAR inside `libs/contextnet-2.7-spring.jar`.

   > The project depends on this library and includes it via `systemPath`.

3. **Build and run**

   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **Active WebSocket**
   The application will open a WebSocket endpoint at:

   ```
   ws://localhost:8080/ws
   ```

---

## How It Works

1. The **WebSocket client** connects to the server and sends a JSON configuration object:

```json
{
  "gatewayIP": "192.168.0.100",
  "gatewayPort": 5500,
  "myUUID": "641f18ae-6c0c-45c2-972f-d37c309a9b72",
  "destinationUUID": "cc2528b7-fecc-43dd-a1c6-188546f0ccbf"
}
```

2. The server creates an instance of `ContextNetClient`, which uses the `Sender` class to connect to the **ContextNet** gateway and send the registration message.

3. After that, the client can send text messages through the WebSocket, which are redirected to ContextNet.

4. Responses coming from ContextNet are received by `Sender`, passed to `ContextNetClient`, and then forwarded back to the frontend via WebSocket.

---

## Main Classes

| Class                        | Function                                                                |
| ---------------------------- | ----------------------------------------------------------------------- |
| `Sender`                     | Manages the connection to ContextNet via MRUDP.                         |
| `ContextNetClient`           | Acts as a bridge between `Sender` and the WebSocket.                    |
| `ContextNetWebSocketHandler` | Handles WebSocket connections and creates `ContextNetClient` instances. |
| `ContextNetClientFactory`    | Builds clients configured with the received connection data.            |
| `ContextNetConfig`           | DTO that represents the necessary data to connect to ContextNet.        |
| `WebSocketConfig`            | Configures the WebSocket endpoint in Spring.                            |

---

## Example of a ContextNet Message

```text
<mid1,641f18ae-6c0c-45c2-972f-d37c309a9b72,tell,cc2528b7-fecc-43dd-a1c6-188546f0ccbf,numeroDaSorte(3337)>
```

> This format follows the specification expected by ContextNet (mid, sender, type, receiver, content).

---

## References

* [ContextNet (PUC-Rio)](https://gitlab.com/contextnet)
* Internal documentation of the library `contextnet-2.7-patched.jar`

---

## Team

### Developers

* **Names**: Gustavo Xavier Saldanha and Mateus Façanha Lima de Souza
* **Program**: Information Systems
* **Institution**: CEFET/RJ – Nova Friburgo Campus
* **Emails**: [gustavosaldxav@gmail.com](mailto:gustavosaldxav@gmail.com) and [facanhalima85@gmail.com](mailto:facanhalima85@gmail.com)
* **LinkedIn**: [https://www.linkedin.com/in/gustavosaldxav](https://www.linkedin.com/in/gustavosaldxav) and [https://www.linkedin.com/in/mateusfacanha](https://www.linkedin.com/in/mateusfacanha)

### Advisor

* **Prof. Dr.**: Nilson Mori Lazarin
* **Email**: [nilsonmori@gmail.com](nilsonmori@gmail.com)

---

<div align="center">
  <p>Developed for the Information Systems Final Project (TCC)</p>
  <p>© 2025</p>
</div>
