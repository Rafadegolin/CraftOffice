# CraftOffice

Mod Fabric de escritório virtual: câmera e microfone por proximidade, salas privadas, status e palco. O plano está em `docs/CraftOffice estudo do mod de escritório virtual (Fabric 26.3).md` e os resultados da prova de conceito em `docs/fase0-resultados.md`.

## Ambiente

- Minecraft Java 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.18, Java 25.
- O JDK 25 pode não estar no PATH. No Windows: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`.
- `./gradlew build` compila. `./gradlew runClient` abre o Player1 e `./gradlew runClient2` o Player2, com padrão de teste no lugar da webcam (a câmera só abre em um processo).
- Teste de dois clientes: Player1 abre o mundo para LAN, Player2 entra pela lista, os dois aceitam o consentimento e chegam a 6 blocos um do outro. `/office debug` lista os vizinhos. Com conexão aberta, os números vão para o log a cada 5 s (`[auto]`).
- `./gradlew test` roda os testes do motor de proximidade (`src/test`).

## Estrutura

- `src/main`: comum e servidor. `net/` avisos, `server/OfficeServer` repasse, quem tem o mod e o tick de proximidade, `proximity/ProximityEngine` motor puro sem tipos do Minecraft, `config/ServerConfig` em `config/craftoffice-server.json`.
- `src/client`: só cliente. `ClientSettings` em `config/craftoffice-client.json`, `DistanceVolume` volume pela distância, `media/` webrtc-java e `media/audio/` mixer, `render/` texturas e HUD de vídeo, `ui/` teclas, telas e indicadores.
- Nada de mídia ou nativo em `src/main`: o servidor dedicado nunca carrega a webrtc-java.

## Nomes que mudaram no 26.3

Os modelos ainda escrevem os nomes antigos. Na dúvida, conferir com `javap` nos jars em `~/.gradle/caches/fabric-loom/minecraftMaven` ou na branch 26.3 do Fabric API, não na documentação do Fabric (ainda na 26.2).

| Antes | No 26.3 |
| --- | --- |
| Nomes Yarn (`ServerPlayerEntity`) | Nomes oficiais da Mojang (`ServerPlayer`) |
| `modImplementation`, `remapJar`, linha `mappings` | `implementation`, `jar`, sem mappings |
| `PayloadTypeRegistry.playC2S()` / `playS2C()` | `serverboundPlay()` / `clientboundPlay()` |
| `KeyBindingHelper.registerKeyBinding` | `KeyMappingHelper.registerKeyMapping`, categoria com `KeyMapping.Category.register(id)` |
| `ClientCommandManager` | `ClientCommands` |
| `HudRenderCallback` | `HudElementRegistry.addLast(id, (graphics, delta) -> ...)` |
| `GuiGraphics`, `drawString`, `render(...)` | `GuiGraphicsExtractor`, `text(...)`, `extractRenderState(...)` |
| `WorldRenderEvents` | `LevelRenderEvents`, sem `AFTER_ENTITIES` |
| `MultiBufferSource.getBuffer` | `SubmitNodeCollector.submitCustomGeometry` |
| `com.mojang.blaze3d.pipeline` / `.textures` | `com.mojang.renderpearl.api.*` |
| `Minecraft.setScreen()` / `mc.screen` | `mc.gui.setScreen()` / `mc.gui.screen()` |
| `GLFW.GLFW_KEY_*` | `InputConstants.KEY_*` (o jogo usa SDL3) |
| `glTexSubImage2D` | `DynamicTexture.upload()`; copiar para `NativeImage.getPointer()` |
| `displayClientMessage(msg, true)` | `player.sendOverlayMessage(msg)` |
| `options.hideGui` | Não existe; elementos do `HudElementRegistry` já somem com F1 |
| Access widener | Class tweaker (`.classtweaker`) |
| `PlayerRenderState` para outros players | `AvatarRenderState`; `PlayerRenderState` é só o jogador local |

## webrtc-java (0.19.0)

- Todas as chamadas passam pela thread `craftoffice-media` (`MediaEngine.run`). Callbacks chegam em threads nativas; mandar pacote do jogo sempre via `Minecraft.getInstance().execute`.
- Quem atende só anexa faixas depois de `setRemoteDescription`, com `addTrack`. Transceptores criados antes não se casam com a oferta e a resposta sai só para receber.
- Não destruir faixas enquanto a fábrica vive: conexões seguram referência mesmo fechadas. A faixa de vídeo é criada uma vez; desligar a câmera para a captura e chama `setEnabled(false)`.
- Escolher dispositivos explicitamente. Ignorar câmeras sem formato (câmera virtual do OBS).
- A captura da webcam deixa uma thread nativa não-daemon presa à JVM. Por isso existe a guarda de saída em `CraftOfficeClient.startExitGuard`.
- Sem volume por faixa na API, então o áudio é próprio (`media/audio/`): fábrica com `HeadlessAudioDeviceModule`, microfone por `AudioRecorder` + `AudioProcessing` + `CustomAudioSource`, cada vizinho num `PeerAudio` alimentado pelo `AudioTrackSink` da faixa remota, e `AudioMixer` tocando por `AudioPlayer`. O mix também vai para `processReverseStream` como referência do cancelamento de eco.
- Formato interno: 48 kHz, mono, 16 bits little-endian, blocos de 10 ms (480 amostras).
- No libyuv, `FourCC.ABGR` é a ordem de bytes R, G, B, A, a mesma da `NativeImage`.

## Regras do projeto

- Câmera e microfone sempre começam desligados. Nada abre conexão sem o consentimento do servidor atual.
- Pacotes do jogo são só para avisos pequenos (limite de 32 KiB indo ao servidor). Mídia nunca passa pelo servidor do jogo.
- Commits em português, uma linha de resumo e corpo explicando o porquê.
