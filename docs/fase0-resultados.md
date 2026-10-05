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

## Medido no jogo

| Pergunta | Resultado |
| --- | --- |
| Própria câmera na HUD, fps | **20 fps**, já chegando em 320×240 da câmera integrada |
| Cópia + `upload()` de 320×240, média e p95 | **78 µs, p95 102 µs**. A 60 fps, menos de 1% do tempo de um frame. Quatro vídeos cabem com folga |
| FPS do jogo com câmera ligada vs. desligada | 60 fps com câmera ligada, limitado pelo VSync. Custo real ainda não medido: fica para a Fase 4, com VSync desligado |
| Cores corretas? | **Sim.** `FourCC.ABGR` bate com a `NativeImage` |
| Conexão fecha entre dois clientes? | **Sim.** `CONNECTED` em menos de 1 s, par `host` na rede local. Sinalização pelo servidor funcionou de primeira |
| Vídeo chega nos dois sentidos, fps | **Sim. 19,6 a 20,2 fps em 320×240 nos dois sentidos** (webcam de um lado, padrão sintético do outro) |
| Áudio chega nos dois sentidos | **Sim.** 48 kHz, mono, 16 bits, 100 callbacks/s. Pico de 6828 (de 32767) no Player2 com fala no Player1. O microfone padrão do Windows é o certo; no primeiro teste ninguém falou |
| Jogo fecha sem travar? | **Sim**, com duas correções: encerrar a mídia ao fechar e uma guarda de saída. A captura da webcam deixa uma thread nativa não-daemon presa à JVM, e a guarda chama `System.exit(0)` 2 s depois do fim da thread principal |

## O que a Fase 0 ensinou

- **Quem atende só anexa faixas depois de ler a oferta**, com `addTrack`. Transceptores criados antes não se casam com os da oferta e a resposta sai só para receber.
- **Faixas não podem ser destruídas enquanto a fábrica vive.** As conexões seguram referência mesmo fechadas. A faixa de vídeo é criada uma vez e desligar só para a captura.
- **Escolher os dispositivos explicitamente.** A ordem das câmeras muda entre processos (o OBS apareceu primeiro num deles) e câmeras virtuais sem formato precisam ser ignoradas.
- **Thread nativa da webcam segura a JVM.** Vale abrir um issue na webrtc-java com o caso mínimo.
- **Volume por pessoa:** sem `setVolume`, mas o PCM de cada pessoa chega por `AudioTrackSink`. A Fase 3 testa `HeadlessAudioDeviceModule` com mixer próprio.

## Roteiro do teste

1. `./gradlew runClient` abre o Player1. Criar um mundo e apertar **V**.
2. Esc → Abrir para LAN.
3. `./gradlew runClient2` abre o Player2, com padrão de teste no lugar da câmera. Entrar pela lista de LAN.
4. No Player1: `/office call Player2`. Os números vão para o log a cada 5 s (`[auto]`).
5. Se o pico do áudio ficar em 0, `/office devices` e `/office mic <n>`.

Alternativa ao LAN: `./gradlew runServer` com `eula=true` em `run/eula.txt` e `online-mode=false` em `run/server.properties`.

## Decisão do portão

**Aprovado.** A webrtc-java carrega no Fabric 26.3, o frame vira textura a 20 fps com custo desprezível, e dois clientes trocam áudio e vídeo nos dois sentidos com a sinalização pelo servidor do jogo. O plano segue como no estudo, a partir da Fase 1.

Pendências que não bloqueiam: medir o FPS sem VSync (Fase 4), testar o jar em Linux (Fase 7) e testar entre duas máquinas, com webcam dos dois lados.
