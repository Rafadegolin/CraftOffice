# Fase 0: resultados

Prova de conceito do estudo `CraftOffice estudo do mod de escritório virtual (Fabric 26.3).md`.

## Ambiente

| Item | Valor |
| --- | --- |
| Máquina | Windows 11, NVIDIA RTX 2050, OpenGL 3.3 (driver 616.92) |
| JDK | Temurin 25.0.4.1 |
| Gradle / Loom | 9.7.1 / 1.18.2. O modelo 26.3 já usa Loom 1.18, não a 1.17 citada no estudo |
| Minecraft / Loader / Fabric API | 26.3 / 0.19.5 / 0.161.0+26.3 |
| webrtc-java | 0.19.0 + nativo `windows-x86_64` |

## Medido

| Pergunta | Resultado |
| --- | --- |
| A webrtc-java carrega dentro do Fabric? | **Sim.** `PeerConnectionFactory` criada em 341 ms na thread `craftoffice-media`, sem erro de nativo nem de classloader |
| Lista dispositivos? | Sim: 2 câmeras (Integrated Camera com 18 formatos, OBS Virtual Camera), 3 microfones, 5 saídas |
| Tamanho do jar | 8,9 MB só com o nativo de Windows. O nativo é uma DLL de 21,7 MB que comprime para 8,8 MB. Estimativa com Windows, Linux e macOS (x86_64 e aarch64): 45 a 55 MB |
| Jar único funciona? | O loader extrai `webrtc-java-<sistema>-<arquitetura>` pelo nome, então os nativos de vários sistemas convivem no mesmo jar sem conflito. Falta testar em Linux |
| Volume por pessoa | **Não há `setVolume` na API.** Mas cada faixa remota aceita `AudioTrackSink`, que entrega o PCM daquela pessoa. Caminho: `HeadlessAudioDeviceModule` + mixer próprio com ganho por pessoa (ou OpenAL do jogo). Precisa de experimento na Fase 3 |

## A medir no jogo

Preencher depois do roteiro abaixo.

| Pergunta | Resultado |
| --- | --- |
| Própria câmera na HUD, fps | **20 fps**, já chegando em 320×240 da câmera integrada |
| Cópia + `upload()` de 320×240, média e p95 | **78 µs, p95 102 µs**. A 20 fps, cerca de 0,16% de um frame de 16,7 ms. Quatro vídeos ficariam abaixo de 0,5 ms por segundo de jogo |
| FPS do jogo com câmera ligada vs. desligada | 60 fps com câmera ligada. Parece limitado pelo VSync, então ainda não mostra o custo real. Medir com VSync desligado |
| Cores corretas (rosto não azulado)? | **Sim.** `FourCC.ABGR` bate com a `NativeImage` |
| Conexão fecha entre dois clientes? Tipo de par (host/srflx) | |
| Vídeo chega no outro cliente, fps | |
| Áudio chega no outro (callbacks/s e pico > 0 ao falar) | |

## Roteiro do teste

1. `./gradlew runClient` abre o Player1. Criar um mundo e apertar **V**. O quadro do canto superior direito mostra o rosto, os fps da câmera, o tempo de upload e o fps do jogo.
2. Anotar fps com V ligado e desligado. Se o rosto aparecer azulado, trocar `FourCC.ABGR` por `FourCC.RGBA` em `FrameSlot`.
3. No Player1: Esc → Abrir para LAN.
4. `./gradlew runClient2` abre o Player2, com padrão de teste no lugar da câmera. Entrar no mundo pela lista de LAN.
5. No Player1: `/office call Player2`. Os dois veem o vídeo um do outro na HUD.
6. `/office stats` nos dois. Anota estado, par selecionado, fps do vídeo remoto e áudio. Falar no microfone e ver o pico subir.
7. `/office hangup` encerra.

Alternativa ao LAN: `./gradlew runServer` com `eula=true` em `run/eula.txt` e `online-mode=false` em `run/server.properties`, e entrar em `localhost` com os dois clientes.

## Decisão do portão

Parcial: o risco que derrubaria o plano (biblioteca não carregar no Fabric) não se confirmou. A decisão final depende da tabela "A medir no jogo".
