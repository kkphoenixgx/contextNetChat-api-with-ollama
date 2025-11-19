teletelaUUID("b135dd8a-23e5-4b3e-9405-288c40b7fac3").
humanUUID("b2fc3586-245f-4c28-b1ed-56d8e7936a49").

plans("/**@Description Apenas tira o embarcado do chão tornando-o disponível para próximos comandos*/ takeOff /**@Description Se o drone não estiver voando, executa um takeOff interno e depois sobe o drone em X unidades e se já estiver voando, sobe o drone em X unidades*/ up(X) /**@Description desce o drone em X unidades*/ down(X) /**@Description pousa o drone*/ land /**@Description Comanda o drone para a direita em X unidades*/ right(X) /**@Description Controla o drone para frente em X unidades*/forward(X)  /**@Description Comanda o drone para a esquerda em X unidades*/left(X)/**@Description controla o drone em X unidades*/ backward(X)  /**@Description desliga o drone*/turnOff").
commandId(0).
currentLimit(0).

!connect.

+!connect : teletelaUUID(UUID) <- 
	.wait(2000);
	.connectCN("skynet.chon.group", 5000, UUID);
	.print("📺: Conectado à Skynet 🌐");
	+ready
.

//+ready <- !pathRequest.

+!pathRequest: teletelaUUID(UUID) & humanUUID(Human) & not running<-
	.print("📺: Waiting human command.");
	.random(R); 
	.wait(10000*R);
	!pathRequest
.

+!pathRequest: running.

-!pathRequest <- .print("📺: Secretary is not reachable").

+!path(Path) <-
  +running;
  .print("Path received", Path);
  .send(eye, tellHow, Path);
  .wait(3000);
  .send(eye, achieve, path)
.

//? ----------- Single Actions -----------

+!up(Limit)[source(H)] <-
  +running;
	?commandId(N);
	-+commandId(N+1);
  .print("📺: Up command received.");
	!upAjustNumber(Limit);
	?currentLimit(NewLimit);
	.send(navigator, tell, command(N+1, up(NewLimit)));
	.sendOut(H,tell,"👁️: Up command received.");
.
+!down(Limit)[source(H)] <-
  +running;
	?commandId(N);
	-+commandId(N+1);
	!downAjustNumber(Limit);
	?currentLimit(NewLimit);
	.send(navigator, tell, command(N+1, down(NewLimit)));
  .print("📺: Down command received.");
	.sendOut(H,tell,"👁️: Down command received.");
.
+!forward(Limit)[source(H)] <-
  +running;
	?commandId(N);
	-+commandId(N+1);
	!forwardAjustNumber(Limit);
	?currentLimit(NewLimit);
	.send(navigator, tell, command(N+1, forward(NewLimit)));
  .print("📺: Forward command received.");
	.sendOut(H,tell,"👁️: Forward command received.");
.
+!backward(Limit)[source(H)] <-
  +running;
	?commandId(N);
	-+commandId(N+1);
	!backwardAjustNumber(Limit);
	?currentLimit(NewLimit);
	.send(navigator, tell, command(N+1, backward(NewLimit)));
  .print("📺: Backward command received.");
	.sendOut(H,tell,"👁️: Backward command received.");
.
+!left(Limit)[source(H)] <-
  +running;
	?commandId(N);
	-+commandId(N+1);
	!leftAjustNumber(Limit);
	?currentLimit(NewLimit);
	.send(navigator, tell, command(N+1, left(NewLimit)));
  .print("📺: Left command received.");
	.sendOut(H,tell,"👁️: Left command received.");
.
+!right(Limit)[source(H)] <-
  +running;
	?commandId(N);
	-+commandId(N+1);
	!rightAjustNumber(Limit);
  .print("📺: Right command received.");
	?currentLimit(NewLimit);
	.send(navigator, tell, command(N+1, right(NewLimit)));
	.sendOut(H,tell,"👁️: Right command received.");
.

+!takeOff[source(H)] <-
	+running;
	?commandId(N);
	-+commandId(N+1);
	.send(navigator, tell, command(N+1, takeoff));
  .print("📺: Takeoff command received.");
	.sendOut(H,tell,"👁️: Take off command received.");
.
+!land[source(H)] <-
	+running;
	?commandId(N);
	-+commandId(N+1);
	.send(navigator, tell, command(N+1, land));
  .print("📺: Land command received.");
	.sendOut(H,tell,"👁️: Land command received.");
.
+!turnOff[source(H)] <-
	-running;
	?commandId(N);
	-+commandId(N+1);
	.send(navigator, tell, command(N+1, turnOff));
  .print("📺: Turn off command received.");
	.sendOut(H,tell,"👁️: Turn off command received.");
.


+!cancel <-
	.send(navigator, achieve, cancel);
  .print("Canceling all commands.");
.

+pathConcluded : teletelaUUID(UUID) & secretaryUUID(Secretary)  <- 
	.sendOut(UUID, tell, message(UUID, "Path Concluded."));
	.wait(1000);
	-pathConcluded
.


+!getPlans[source(N)] <-
	.print("GetPlans request received from ", N);
	?plans(P);
	.send(N, tell, plans(P))
.

//? ----------- Helpers -----------

+!upAjustNumber(N) : N > 0  <- NewLimit = N*1; -+currentLimit(NewLimit) .
+!upAjustNumber(N) : N == 0 <- NewLimit = 0; -+currentLimit(NewLimit) .
+!upAjustNumber(N) : N < 0  <- NewLimit = N*-1; -+currentLimit(NewLimit) .


+!downAjustNumber(N) : N > 0  <- NewLimit = N*-1; -+currentLimit(NewLimit) .
+!downAjustNumber(N) : N == 0 <- NewLimit= 0; -+currentLimit(NewLimit) .
+!downAjustNumber(N) : N < 0  <- NewLimit = N * -1; -+currentLimit(NewLimit) .


+!leftAjustNumber(N) : N > 0  <- NewLimit = N*-1; -+currentLimit(NewLimit) .
+!leftAjustNumber(N) : N == 0 <- NewLimit = 0; -+currentLimit(NewLimit) .
+!leftAjustNumber(N) : N < 0  <- NewLimit = N; -+currentLimit(NewLimit) .


+!rightAjustNumber(N) : N > 0  <- NewLimit = N*1; -+currentLimit(NewLimit) .
+!rightAjustNumber(N) : N == 0 <- NewLimit = 0; -+currentLimit(NewLimit) .
+!rightAjustNumber(N) : N < 0  <- NewLimit = N*-1; -+currentLimit(NewLimit) .


+!forwardAjustNumber(N) : N > 0  <- NewLimit = N*-1; -+currentLimit(NewLimit) .
+!forwardAjustNumber(N) : N == 0 <- NewLimit = 0; -+currentLimit(NewLimit) .
+!forwardAjustNumber(N) : N < 0  <- NewLimit = N*1; -+currentLimit(NewLimit) .


+!backwardAjustNumber(N) : N > 0  <- NewLimit = N*1;  -+currentLimit(NewLimit) .
+!backwardAjustNumber(N) : N == 0 <- NewLimit = 0;    -+currentLimit(NewLimit) .
+!backwardAjustNumber(N) : N < 0  <- NewLimit = N*-1; -+currentLimit(NewLimit) .