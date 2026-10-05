# CraftOffice: estudo do mod de escritório virtual (Fabric 26.3)

Oct 5, 2026 · @Daniel de Souza

## Resumo

O mod é viável na 26.3, e o único risco capaz de derrubar o plano cabe em uma prova de conceito de 3 a 5 dias.

Já existem mods que mostram webcam e tela dentro do jogo. Nenhum dos que encontrei anuncia a parte de escritório: salas privadas, trancar e bater na porta, status de ocupado e palco. É aí que o projeto se diferencia.

| Decisão | Escolha |
| --- | --- |
| Jogo | Minecraft Java 26.3, Java 25 |
| Base do mod | Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.17 |
| Câmera, tela, microfone e codecs | webrtc-java 0.19.0, uma biblioteca só |
| Como o vídeo trafega (versão 1) | Direto entre os players (P2P). O servidor do Minecraft só apresenta um ao outro |
| Como o vídeo trafega (versão 2, opcional) | Servidor de mídia (SFU) para grupos acima de 4 pessoas |
| Como aparece no jogo | Textura dinâmica desenhada acima da cabeça, na HUD e em telões |
| Servidor extra na versão 1 | Nenhum obrigatório. Um TURN no VPS é recomendado para redes difíceis |

**Tamanho.** São 9 fases, de 36 a 52 dias de trabalho focado. O número é estimativa minha, não medição. As fases 0 a 4 (18 a 26 dias) já entregam a cena do vídeo de portfólio: chegou perto, a câmera abre.

**Maior risco.** Não encontrei nenhum mod público que embarque a webrtc-java. A Fase 0 existe para provar que ela carrega dentro do Fabric antes de escrever o resto.

**Limite assumido.** Na versão 1 cada conversa mostra no máximo 4 vídeos ao mesmo tempo. Áudio não tem esse limite.

## Escopo

O mod copia a mecânica clássica do Gather 1.0, trocando tiles por blocos, e deixa de fora tudo que é produto corporativo.

A piada tem base real. O Gather [acabou com o plano gratuito em 15/09/2025](https://support.gather.town/hc/en-us/articles/39590892978196-Understanding-Gather-s-pricing-changes-2025) e cobra de US$ 12 a US$ 15 por membro ao mês. O [Gather 2.0 também removeu](https://support.gather.town/articles/2163640255-gather-1-0-vs-gather-2-0) o envio de mapas e objetos próprios. Num servidor de Minecraft, o mapa é o que você construir.

### O que entra

| Recurso do Gather | Como fica no Minecraft | Fase |
| --- | --- | --- |
| [Áudio e vídeo por proximidade](https://support.gather.town/articles/3513901174-best-practices-in-office-design): conecta a cerca de 5 tiles, some a 6 | Conecta a 6 blocos, desconecta a 8. O volume cai com a distância | 3 e 4 |
| [Câmera e microfone desligados por padrão](https://support.help.gather.town/articles/4462418552-meeting-overview) | Igual, com tela de consentimento na primeira vez em cada servidor | 1 |
| Grade de vídeos da conversa | Vídeo acima da cabeça de cada player e grade na HUD | 4 |
| [Área privada](https://support.gather.town/articles/2550999600-overview-of-meeting-rooms-private-areas): só quem está dentro se vê e se ouve | Zona com nome, marcada por dois cantos | 5 |
| Trancar a sala e aprovar quem pede para entrar | Zona trancada. Quem chega bate, alguém de dentro aceita | 5 |
| Status: disponível, ocupado, não perturbe | Ícone ao lado do nome. Não perturbe não conecta com ninguém | 5 |
| Modo silencioso: alcance de 1 tile | Status foco: alcance cai para 2 blocos | 5 |
| [Spotlight](https://support.gather.town/articles/5684809314-spotlight-tiles): quem pisa no tile fala para a sala toda | Bloco-palco: quem pisa fala para a zona inteira | 5 (áudio), 9 (vídeo) |
| Compartilhar tela, várias ao mesmo tempo | Telão: bloco que mostra a tela de quem compartilha | 6 |

Os raios de 6 e 8 blocos são escolha minha para imitar a sensação do Gather. Ficam configuráveis no servidor.

### O que fica de fora

- Chat, calendário, gravação, transcrição e notas de IA.
- Editor de mapas. O próprio Minecraft já é o editor.
- Acenar, ir até a pessoa e seguir. Entram depois, se sobrar vontade.
- Bedrock, Forge, NeoForge e plugin para Paper. O alvo é só Fabric na 26.3, com o mod instalado no cliente e no servidor.

## O que já existe

Nenhum código existente pode ser copiado, mas dois projetos servem de mapa: o Webcam mostra o que funciona na 26.3 e o WorkAdventure mostra como agrupar pessoas por proximidade.

| Projeto | O que faz | Como trafega | Na 26.3 | Licença | O que aproveitar |
| --- | --- | --- | --- | --- | --- |
| [StreamCraft Live](https://modrinth.com/mod/streamcraft-live) | Webcam acima da cabeça, tela em blocos, voz | LiveKit Cloud. Vídeo entre players é pago após 10 horas de teste | Sim | Fechada, sem código público | Prova que o conceito funciona. Mostra o erro a evitar: um jar por sistema operacional |
| [Webcam](https://modrinth.com/plugin/webcam-mod), de DimasKama | Webcam no rosto ou acima da cabeça | H.264 em UDP próprio, porta 25454 | Sim | [NUDL](https://github.com/DimasKama/Webcam/blob/master/LICENSE.md): pode ler, não pode copiar | Melhor referência de renderização na 26.3 |
| [WebcamMod](https://github.com/Lichcodes/WebcamMod), de Lichcode | Webcam na skin | JPEG dentro dos pacotes do próprio jogo | Não, parou na 1.21.4 | GPL v2 | Só a ideia. Usa OpenGL direto, que deixou de ser aceito |
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | Voz por proximidade e grupos | Opus em UDP próprio, porta 24454 | Sim, em beta | Todos os direitos reservados | Receita de áudio e o conceito de grupo isolado |
| [WorkAdventure](https://github.com/workadventure/workadventure) | Clone aberto do Gather, no navegador | P2P até 4 pessoas, LiveKit a partir da 5ª | Não é mod | Código público | Regras de formação de grupo |

Três lições saem dessa tabela.

1. **Um jar único.** O autor do StreamCraft [relata](https://raw.githubusercontent.com/slashdaemon/StreamCraft-Releases/main/README.md) que os apps do Modrinth e do CurseForge sempre instalam o arquivo principal, que é o de Windows. Quem usa Mac ou Linux recebe o jar errado.
2. **Vídeo fora dos pacotes do jogo.** O próprio Lichcode [avisa](https://github.com/Lichcodes/WebcamMod) que o mod dele não serve para servidor público. Os dois mods sérios abriram um canal separado.
3. **Limite de 4 no P2P.** O WorkAdventure [troca para servidor de mídia](https://workadventu.re/release/workadventure-1-27-0-the-road-to-workadventure-2/) quando entra a 5ª pessoa. É de lá que vem o limite da versão 1.

Nenhum dos mods encontrados anuncia sala privada com vídeo, trancar e bater, status ou palco. Uma busca no Modrinth por [office meeting](https://api.modrinth.com/v2/search?query=office%20meeting&limit=10) voltou vazia.

## Ambiente: Minecraft 26.3 e Fabric

A 26.3 invalida quase todo tutorial anterior a 2026: o jogo deixou de ser ofuscado, os nomes das classes mudaram e desenhar com OpenGL direto não é mais aceito.

### Versões para fixar

| Item | Versão | Observação |
| --- | --- | --- |
| [Minecraft](https://minecraft.wiki/w/Java_Edition_26.3) | 26.3, de 15/09/2026 | Exige Java 25 |
| [Fabric Loader](https://fabricmc.net/2026/09/15/263.html) | 0.19.5 | Única versão marcada como estável |
| [Fabric API](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml) | 0.161.0+26.3 | Não usar latest: hoje aponta para a 26.4 |
| [Fabric Loom](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml) | 1.17 | É a linha que o modelo oficial da 26.3 usa. A mais nova é a 1.18.2 |
| Gradle | 9.5.1, a que vem no modelo | O blog do Fabric cita a 9.6.0. Se o Loom reclamar, atualizar com `./gradlew wrapper --gradle-version latest` |
| [webrtc-java](https://github.com/devopvoid/webrtc-java) | 0.19.0, de 27/09/2026 | Apache-2.0 |
| Mod Menu e Cloth Config | 21.0.0 e 26.3.159 | Opcionais, para a tela de configuração |

### Ponto de partida

O caminho mais curto é clonar a branch 26.3 do [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod/tree/26.3). Ela já separa o código em `src/main` (comum e servidor) e `src/client` (só cliente).

```properties
minecraft_version=26.3
loader_version=0.19.5
loom_version=1.17-SNAPSHOT
fabric_api_version=0.161.0+26.3
```

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

loom {
    splitEnvironmentSourceSets()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}
```

Não existe mais a linha `mappings` nem a etapa de remapeamento.

### O que mudou e quebra código antigo

| Antes | Na 26.3 |
| --- | --- |
| Nomes Yarn, como `ServerPlayerEntity` | Nomes oficiais da Mojang, como `ServerPlayer` |
| Plugin `fabric-loom`, `modImplementation`, `remapJar` | Plugin `net.fabricmc.fabric-loom`, `implementation`, `jar` |
| `PayloadTypeRegistry.playC2S()` e `playS2C()` | `serverboundPlay()` e `clientboundPlay()` |
| `KeyBindingHelper.registerKeyBinding` | `KeyMappingHelper.registerKeyMapping` |
| `ClientCommandManager` | `ClientCommands` |
| `HudRenderCallback` | `HudElementRegistry` |
| `WorldRenderEvents` | `LevelRenderEvents`, sem o evento `AFTER_ENTITIES` |
| `LivingEntityFeatureRendererRegistrationCallback` | `LivingEntityRenderLayerRegistrationCallback` |
| `MultiBufferSource` e `getBuffer(renderType)` | `SubmitNodeCollector.submitCustomGeometry(...)` |
| `GuiGraphics` | `GuiGraphicsExtractor` |
| Pacotes `com.mojang.blaze3d.pipeline` e `.textures` | `com.mojang.renderpearl.api.*` |
| `Minecraft.getInstance().setScreen()` | `Minecraft.getInstance().gui.setScreen()` |
| Constantes `GLFW.GLFW_KEY_*` | `InputConstants.KEY_*`. A 26.3 trocou GLFW por SDL3 |
| `glTexSubImage2D` e outras chamadas OpenGL | `DynamicTexture.upload()` |
| Access widener | Class tweaker, arquivo `.classtweaker` |

As trocas estão no [guia de migração do Fabric](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api), nos posts da [26.1](https://fabricmc.net/2026/03/14/261.html), [26.2](https://fabricmc.net/2026/06/15/262.html) e [26.3](https://fabricmc.net/2026/09/15/263.html) e no [primer da NeoForge para a 26.3](https://github.com/neoforged/.github/blob/main/primers/26.3/index.md).

Se o código for escrito com IA, essa tabela vai para o arquivo de instruções do repositório. Os modelos ainda escrevem os nomes antigos.

### Regras do ambiente

- **Pacotes do jogo são só para avisos.** O limite é de [menos de 32 KiB para o servidor e 1 MiB para o cliente](https://docs.neoforged.net/docs/networking/payload). Confirmei o texto para a 26.1, não para a 26.3.
- **OpenGL direto está proibido.** A 26.2 trouxe Vulkan como opção experimental e a Mojang [pretende remover o OpenGL](https://www.minecraft.net/en-us/article/another-step-towards-vibrant-visuals-for-java-edition), sem data.
- **Não embarcar o que o jogo já traz.** A 26.3 vem com LWJGL 3.4.3, Netty 4.2.16 e JNA 5.17.0.
- **A documentação do Fabric ainda está na 26.2.** Os exemplos de renderização usam imports antigos. As fontes confiáveis são a [branch 26.3 do Fabric API](https://github.com/FabricMC/fabric-api/tree/26.3) e o [mcsrc.dev](https://mcsrc.dev), que mostra o código do jogo.

## Arquitetura

São três peças: o mod no servidor decide quem pode falar com quem, o mod no cliente captura e desenha, e a mídia vai direto de um player para o outro.

&#91;embedded content: arquitetura · 2 clientes, 1 servidor, TURN opcional\]

A linha destacada é o único caminho por onde passam voz e imagem. O servidor do jogo troca apenas avisos pequenos com cada cliente.

### As peças

| Peça | Onde roda | O que faz |
| --- | --- | --- |
| Motor de proximidade | Servidor | A cada 5 ticks mede distâncias e zonas e monta a lista de vizinhos de cada player. Envia só as mudanças |
| Repasse de apresentação | Servidor | Encaminha a negociação da conexão entre dois players, e só entre vizinhos |
| Zonas e status | Servidor | Guarda zonas, trancas e status. Salva em JSON na pasta do mundo |
| Sessões de mídia | Cliente | Mantém uma conexão por vizinho. Abre e fecha conforme o servidor avisa |
| Captura | Cliente | Webcam, tela e microfone, pela webrtc-java |
| Renderização | Cliente | Transforma cada frame em textura e desenha acima da cabeça, na HUD e no telão |
| Interface | Cliente | Teclas, tela de consentimento, indicadores e configuração |
| TURN | VPS, opcional | Retransmite a mídia quando a conexão direta não fecha |

### Como nasce uma conversa

1. A anda até B. O servidor mede a distância entre os dois a cada 5 ticks.
2. A distância cai para 6 blocos ou menos. Os dois estão na mesma dimensão, na mesma zona, e ninguém está em não perturbe.
3. O servidor marca o par como vizinho e avisa os dois. Quem tem o menor UUID inicia a conexão, para os dois não ligarem ao mesmo tempo.
4. O cliente de A manda a proposta de conexão ao servidor, que repassa a B. B responde pelo mesmo caminho.
5. A conexão fecha direto entre A e B, ou pelo TURN. O áudio começa. O vídeo só trafega para quem ligou a câmera.
6. O volume e o tamanho do vídeo acompanham a distância.
7. A distância passa de 8 blocos por mais de 1 segundo. O servidor avisa e os dois fecham a conexão.

Esse desenho protege as conversas sem esforço extra. Sem o repasse do servidor a conexão não existe, então um cliente adulterado não consegue escutar quem está longe ou em outra zona.

### Avisos entre cliente e servidor

Todos viajam como pacotes personalizados do próprio jogo e têm poucos KB.

| Aviso | Sentido | Conteúdo |
| --- | --- | --- |
| `hello` | Cliente para servidor | Versão do protocolo do mod |
| `config` | Servidor para cliente | Raios, limite de vídeos, endereços de STUN e TURN com senha temporária |
| `peer_add` e `peer_remove` | Servidor para cliente | UUID do vizinho, quem inicia a conexão, zona |
| `signal` | Os dois sentidos | UUID de destino e o texto da negociação |
| `state` | Os dois sentidos | Microfone, câmera, tela e status de cada player |
| `zone_update` | Servidor para cliente | Zonas próximas e se estão trancadas |
| `knock` e `knock_reply` | Os dois sentidos | Pedido para entrar em zona trancada e a resposta |

## Decisões técnicas

Cada decisão abaixo troca alcance por simplicidade: uma biblioteca de mídia em vez de quatro, nenhum servidor extra na versão 1 e nenhum código de terceiros copiado.

### 1. Biblioteca de mídia: webrtc-java

A [webrtc-java](https://github.com/devopvoid/webrtc-java) é a única opção que cobre câmera, tela, codecs, áudio e transporte em uma dependência só, com versão lançada no último mês.

| Opção | Webcam | Tela | Áudio | Problema principal |
| --- | --- | --- | --- | --- |
| [webrtc-java 0.19.0](https://jrtc.dev/guide/get-started) | Sim, lista câmeras e resoluções | Sim, em Windows, X11 e Wayland | Sim, com cancelamento de eco | Nunca vista dentro de um mod. A versão 0.18 [corrigiu falhas de memória](https://raw.githubusercontent.com/devopvoid/webrtc-java/main/CHANGELOG.md) na parte nativa |
| OpenCV com OpenH264, a receita do mod Webcam | Sim, testando índice por índice | Não | Não | Exige escrever a ponte do codec e todo o transporte. O jar do Webcam tem cerca de 87 MB |
| [JavaCV com FFmpeg](https://github.com/bytedeco/javacv) 1.5.14 | Sim | Sim, sem Wayland | Só o codec | Sem transporte e pesado. O FFmpeg do WATERMeDIA ocupa 137 MB |
| Aba do navegador controlada pelo mod | Sim | Sim | Sim | O vídeo não aparece dentro do jogo |

Três opções foram descartadas de saída. A [sarxos/webcam-capture](https://github.com/sarxos/webcam-capture/releases) teve o último lançamento em janeiro de 2016. O `java.awt.Robot` [não funciona em modo headless](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/Robot.html), que é como um relato de modder indica que o cliente roda. O GStreamer exige instalação separada no computador do jogador.

### 2. Transporte: direto entre players primeiro

A versão 1 usa P2P porque é o único caminho sem servidor extra e com cliente Java pronto.

| Opção | Servidor extra | Cliente Java | Limite prático | Veredito |
| --- | --- | --- | --- | --- |
| P2P, com o servidor do jogo apresentando os players | Nenhum. TURN recomendado | Pronto na webrtc-java | 4 vídeos por conversa | Versão 1 |
| [LiveKit](https://github.com/livekit/livekit) 1.13.7 próprio | VPS com domínio, certificado e portas 7881/TCP e 7882/UDP | [Não existe oficial](https://github.com/livekit/livekit) para Java no desktop | Dezenas de pessoas | Candidato à fase 9 |
| [Cloudflare Realtime](https://developers.cloudflare.com/realtime/sfu/pricing/) | Nenhum. 1.000 GB grátis por mês | Pronto, fala o WebRTC padrão por HTTPS | Dezenas de pessoas | Candidato à fase 9 |
| UDP próprio no servidor do jogo | Uma porta UDP | Tudo por escrever | A banda do servidor | Descartado. Seria reescrever o WebRTC |
| Pacotes do próprio jogo | Nenhum | Pronto | Um pacote perdido trava jogo e vídeo juntos | Descartado |

O P2P tem três custos conhecidos.

- **Upload cresce com o grupo.** Cada vizinho recebe uma cópia. Com rosto a 140 kbps e voz a 24 kbps, são 164 kbps por vizinho: 0,66 Mbps com 4 e 1,3 Mbps com 8.
- **Players enxergam o IP uns dos outros.** Aceitável entre colegas, ruim em servidor público.
- **Nem toda rede fecha conexão direta.** O WorkAdventure [estima cerca de 15% de falha](https://raw.githubusercontent.com/workadventure/workadventure/develop/docs/others/self-hosting/install.md) sem TURN. Por isso o TURN no VPS entra na fase 7.

Fora da rede local, cada player também precisa de um STUN para descobrir o próprio endereço público. Serve um STUN público ou o mesmo servidor que faz o TURN.

### 3. Áudio: na mesma conexão do vídeo

O áudio vai pelo mesmo WebRTC para ganhar sincronia com o vídeo e [cancelamento de eco](https://jrtc.dev/guide/audio/audio-processing), que o Simple Voice Chat não lista entre os recursos.

O custo é perder o som 3D. Sobra o volume por distância, que é exatamente o que o Gather faz. Falta confirmar na Fase 0 se a webrtc-java deixa ajustar o volume de cada pessoa separadamente. A documentação não diz.

Depender do Simple Voice Chat foi a alternativa considerada. Ele já resolve voz 3D e tem [grupo isolado](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/Group.Type.html), que equivale a uma sala privada. Pesaram contra: licença fechada, versão 26.3 ainda em beta, uma segunda porta UDP e nenhuma sincronia com o vídeo. Para quem já usa o Simple Voice Chat, o mod terá a opção de desligar o próprio áudio.

### 4. Codec e perfis: VP8

O VP8 evita a questão de patente do H.264. O binário da Cisco só é coberto se for [baixado separadamente pelo usuário](https://www.openh264.org/BINARY_LICENSE.txt), o que não combina com um jar único.

| Uso | Resolução | Quadros por segundo | Banda |
| --- | --- | --- | --- |
| Rosto | 320×240 | 20 | 140 kbps |
| Voz |  |  | 24 kbps |
| Tela, padrão | 1280×720 | 5 | 800 kbps |
| Tela, fluida | 1280×720 | 15 | 1,5 Mbps |

Os valores são os [perfis padrão do LiveKit](https://raw.githubusercontent.com/livekit/client-sdk-js/main/src/room/track/options.ts), usados aqui como ponto de partida.

### 5. Renderização: textura dinâmica e geometria submetida

A receita abaixo foi conferida em código que já roda na 26.3: o [Fabric API](https://github.com/FabricMC/fabric-api/tree/26.3/fabric-rendering-v1), o [Iris](https://github.com/IrisShaders/Iris/tree/26.3) e o mod Webcam.

1. Cada pessoa tem uma `NativeImage` RGBA e uma `DynamicTexture`, registrada no `TextureManager` com um `Identifier`.
2. A decodificação roda fora da thread do jogo e guarda só o frame mais novo.
3. Na thread do cliente, o frame é copiado para a memória da imagem e enviado com `upload()`. Isso só acontece quando há frame novo e o player está sendo desenhado.
4. Acima da cabeça: uma camada no `AvatarRenderer`, registrada por `LivingEntityRenderLayerRegistrationCallback`, chama `submitCustomGeometry` com um retângulo virado para a câmera.
5. O `RenderType` próprio usa o shader `core/position_tex` do jogo. Como ele ignora a iluminação, o vídeo fica claro à noite.
6. Na HUD: `HudElementRegistry` e `graphics.blit(RenderPipelines.GUI_TEXTURED, ...)`.
7. No telão: `LevelRenderEvents.COLLECT_SUBMITS` com um retângulo em posição fixa. Dispensa criar bloco com entidade.
8. Com shaders: chamar `IrisApi.assignPipeline(pipeline, IrisProgram.TEXTURED)` quando o Iris estiver instalado.

Duas armadilhas. Na 26.3, `PlayerRenderState` passou a ser o estado do jogador local. Os outros players usam `AvatarRenderState`. E, pelo primer da NeoForge, usar transparência no vídeo joga o desenho para o caminho de transparência novo da 26.3, que pede shaders próprios. Cantos retos e opacos evitam isso.

Ninguém publicou quanto custa enviar um frame por textura na 26.3, nem quantos vídeos cabem a 60 fps. Isso se mede no jogo, na fase 4.

### 6. Regras de proximidade

- **Dois raios.** Conecta a 6 blocos, desconecta a 8 e só depois de 1 segundo fora. Evita liga e desliga na borda.
- **Espera de meio segundo.** Quem só passa correndo não abre conexão. O WorkAdventure [faz parecido](https://raw.githubusercontent.com/workadventure/workadventure/develop/back/src/Model/GameRoom.ts): quem está andando não entra em grupo.
- **Zona vence distância.** Mesma zona conecta. Zonas diferentes não conectam, mesmo encostados na parede.
- **Limite de vídeos.** Os 4 vizinhos mais próximos mandam vídeo. Os demais, só áudio.
- **Volume.** Cheio até 3 blocos, cai em linha reta até zero aos 8.

### 7. Empacotamento

- **Um jar para todos os sistemas**, com os nativos de 64 bits de Windows, Linux e macOS. Pelo guia, a dependência padrão traz só o nativo do [sistema em uso](https://jrtc.dev/guide/get-started). Para um jar único é preciso declarar os de todos os sistemas e conferir na Fase 0 se o certo é carregado.
- **Código de mídia só em `src/client`.** O servidor dedicado nunca toca em nativo.
- **macOS pede atenção.** A permissão de microfone pertence ao launcher. O Simple Voice Chat [documenta](https://modrepo.de/minecraft/voicechat/wiki/macos) que o launcher oficial não consegue pedir e o Prism consegue. A câmera deve se comportar igual.

### 8. Licença do projeto: MIT

Com código escrito do zero sobre webrtc-java (Apache-2.0) e libwebrtc (BSD), MIT e Apache-2.0 servem. MIT é a mais reconhecível em portfólio.

## Fases

São 9 fases obrigatórias e 1 opcional. A Fase 0 decide se o plano segue como está, e a Fase 4 entrega a cena do vídeo de portfólio.

&#91;embedded content: roteiro · 10 fases, 1 portão, 2 marcos\]

Os prazos são estimativas minhas em dias de trabalho focado.

### Fase 0: prova de conceito (3 a 5 dias)

Prova os três pontos que podem derrubar o plano: a biblioteca carrega dentro do Fabric, o frame vira textura e dois clientes se conectam.

O primeiro passo é ligar o projeto ao [repositório remoto](https://github.com/Rafadegolin/CraftOffice), para tudo ficar versionado desde o primeiro dia.

```bash
git clone --branch 26.3 https://github.com/FabricMC/fabric-example-mod.git CraftOffice
cd CraftOffice
rm -rf .git
git init -b main
git remote add origin https://github.com/Rafadegolin/CraftOffice.git
git add .
git commit -m "Base do mod a partir do fabric-example-mod 26.3"
git push -u origin main
```

No Windows, `rm -rf .git` vira `rmdir /s /q .git`. Se o repositório já nasceu com README, rode `git pull origin main --allow-unrelated-histories` antes do push. O modelo do Fabric é CC0, então pode ser reaproveitado sem crédito.

- [ ] Clonar o modelo, trocar o id do mod para `craftoffice` e conectar ao repositório remoto
- [ ] Rodar o jogo com `./gradlew runClient` em Java 25
- [ ] Adicionar a webrtc-java com o nativo de Windows e listar as câmeras dentro do jogo
- [ ] Capturar a webcam e mostrar o próprio rosto em um quadrado da HUD
- [ ] Abrir dois clientes de teste e fechar uma conexão de áudio e vídeo entre eles
- [ ] Conferir se dá para ajustar o volume de cada pessoa separadamente
- [ ] Medir o tempo de envio de um frame de 320×240 para a textura

**Pronto quando:** o rosto aparece na HUD a 20 quadros por segundo e o áudio e o vídeo de um cliente chegam no outro.

**Portão:** se a webrtc-java não carregar dentro do Fabric, o plano muda antes de qualquer outra fase. As saídas estão em Riscos.

### Fase 1: fundação (2 a 3 dias)

Deixa o esqueleto do mod pronto para receber as peças.

- [ ] Separar o código: `src/main` para servidor e comum, `src/client` para mídia e tela
- [ ] Avisos `hello` e `config`, para cada lado descobrir se o outro tem o mod
- [ ] Arquivo de configuração do servidor: raios, limite de vídeos, STUN e TURN
- [ ] Teclas: microfone, câmera, tela e painel
- [ ] Tela de consentimento na primeira entrada em cada servidor
- [ ] Indicadores de microfone, câmera e tela na HUD
- [ ] GitHub Actions compilando o jar a cada push
- [ ] Arquivo de instruções para a IA com a tabela de nomes da 26.3

**Pronto quando:** entrar em um servidor com o mod mostra a tela de consentimento, entrar em um servidor sem o mod não quebra nada e a compilação automática passa.

### Fase 2: motor de proximidade (3 a 4 dias)

O servidor passa a saber quem é vizinho de quem.

- [ ] Classe pura, sem tipos do Minecraft, que recebe posições e devolve quem entrou e quem saiu
- [ ] Dois raios, espera de entrada e atraso de saída
- [ ] Limite de vídeos pelos mais próximos
- [ ] Rodar a cada 5 ticks em `ServerTickEvents.END_SERVER_TICK` e limpar na desconexão
- [ ] Avisos `peer_add` e `peer_remove`
- [ ] Testes JUnit: borda do raio, três pessoas em fila, troca de dimensão e desconexão
- [ ] Comando `/office debug` que lista os vizinhos

**Pronto quando:** dois clientes de teste recebem entrou e saiu nos blocos certos, sem piscar na borda.

### Fase 3: conexão e áudio (5 a 7 dias)

Dois players se ouvem ao chegar perto.

- [ ] Gerenciador de sessões no cliente: uma conexão por vizinho, com estados claros
- [ ] Aviso `signal` com validação no servidor: só repassa entre vizinhos, com limite de tamanho e de frequência
- [ ] Faixa de áudio com cancelamento de eco, redução de ruído e ganho automático
- [ ] Volume por distância, atualizado a cada tick do cliente
- [ ] Tecla de mudo e ícone de quem está falando
- [ ] Escolha de microfone e de saída de som
- [ ] Opção de desligar o áudio do mod, para quem usa o Simple Voice Chat

**Pronto quando:** dois players em máquinas diferentes se ouvem a 6 blocos, o volume cai ao afastar e corta aos 8.

### Fase 4: webcam (5 a 7 dias)

A cena do portfólio: chegou perto, o rosto aparece.

- [ ] Captura da câmera em 320×240 a 20 quadros, VP8 a 140 kbps
- [ ] Ligar e desligar o vídeo sem derrubar a conexão
- [ ] Converter o frame para RGBA fora da thread do jogo e guardar só o mais novo
- [ ] Uma textura por pessoa, criada e destruída junto com a sessão
- [ ] Vídeo acima da cabeça, virado para quem olha
- [ ] Grade na HUD com nome e destaque em quem fala
- [ ] Prévia da própria câmera na tela de configuração
- [ ] Aviso visível para os outros de que a câmera está ligada
- [ ] Compatibilidade com o Iris
- [ ] Medir o FPS com 1, 2 e 4 vídeos

**Pronto quando:** A anda até B e os rostos aparecem, ao afastar somem, e 4 vídeos custam menos de 10% do FPS.

### Fase 5: camada de escritório (6 a 8 dias)

O que nenhum mod existente tem.

- [ ] Zonas: `/office zone create <nome>` com dois cantos, salvas em JSON
- [ ] Regra de zona no motor de proximidade, com testes
- [ ] Aviso ao entrar e sair de zona e contorno visível
- [ ] Trancar, destrancar, bater e aceitar
- [ ] Status: disponível, ocupado, foco e não perturbe, com ícone ao lado do nome
- [ ] Bloco-palco: quem pisa fala para a zona toda
- [ ] Permissão para criar zona restrita a operadores

**Pronto quando:** duas salas vizinhas não se ouvem pela parede, sala trancada exige aceite e o palco alcança a zona inteira.

### Fase 6: compartilhamento de tela (5 a 7 dias)

- [ ] Seletor de tela ou janela, pedido toda vez que alguém compartilha
- [ ] Faixa de tela em 720p a 5 quadros, com opção fluida a 15
- [ ] Telão: comando que define posição e tamanho de um retângulo no mundo
- [ ] Desenhar a tela no telão da zona. Sem telão, mostrar grande na HUD
- [ ] Tecla para ver em tela cheia
- [ ] Indicador de que a tela está sendo compartilhada

**Pronto quando:** um player compartilha o editor de código no telão e outro lê o texto a 5 blocos.

### Fase 7: robustez e empacotamento (5 a 8 dias)

- [ ] TURN no VPS, com senha temporária gerada pelo servidor do mod
- [ ] Reconexão após queda de rede, troca de dimensão, morte e nova entrada
- [ ] Um jar único com nativos de Windows, Linux e macOS
- [ ] Teste dos nativos ao iniciar, com mensagem clara em caso de falha
- [ ] Matriz de teste: OpenGL, Vulkan, Sodium e Sodium com Iris
- [ ] Teste em Linux e, havendo acesso, em macOS com o Prism
- [ ] Tecla de pânico que corta toda a captura
- [ ] Sessão de teste com 6 a 8 pessoas reais

**Pronto quando:** 6 pessoas em redes diferentes passam 30 minutos sem precisar reiniciar e o mesmo jar funciona em Windows e Linux.

### Fase 8: publicação (2 a 3 dias)

- [ ] README com GIF no topo, instalação em até 5 passos, diagrama e seção de privacidade
- [ ] Vídeo de 60 a 90 segundos
- [ ] Ícone feito à mão e capturas de tela reais
- [ ] Página no Modrinth com a declaração de para onde o áudio e o vídeo vão
- [ ] Publicação automática por tag, versão `0.1.0+26.3`
- [ ] Licença MIT e aviso exigido pela Mojang

**Pronto quando:** alguém de fora instala seguindo só o README.

### Fase 9, opcional: grupos grandes (2 a 3 semanas)

- [ ] Prova de conceito com Cloudflare Realtime e com LiveKit, para escolher um
- [ ] Trocar para o servidor de mídia quando a conversa passa de 4 vídeos
- [ ] Palco com vídeo

**Pronto quando:** 10 pessoas com vídeo na mesma sala.

## Riscos

Só o primeiro risco muda o plano inteiro. Os outros têm saída dentro das próprias fases.

| Risco | Como aparece | Saída |
| --- | --- | --- |
| A webrtc-java não carrega dentro do Fabric | Erro de nativo ou de carregamento de classe na Fase 0 | Trocar a biblioteca. Ver abaixo |
| Não dá para ajustar o volume por pessoa | A API não expõe o controle | Corte seco no raio, sem queda gradual. Ou tocar o áudio pelo OpenAL do jogo |
| A conexão direta não fecha em algumas redes | Dois players próximos sem áudio na fase 3 | Antecipar o TURN da fase 7 |
| Enviar frames para a textura derruba o FPS | Medição da fase 4 acima de 10% | Baixar para 15 quadros, limitar envios por frame do jogo, juntar os rostos em uma textura só |
| A próxima versão do jogo quebra o desenho | Cada versão desde a 1.21.5 mudou a API de renderização | Isolar o desenho atrás de três funções: enviar textura, desenhar no mundo, desenhar na HUD |
| macOS não libera câmera e microfone | O launcher oficial não mostra o pedido de permissão | Declarar suporte no Mac só com o Prism. Windows e Linux primeiro |
| Eco com caixa de som aberta | Reclamação nos testes com gente real | Cancelamento de eco ligado por padrão e modo apertar para falar |
| IP dos players exposto no P2P | Inerente ao desenho | Opção no servidor para forçar tudo pelo TURN |
| Jar pesado | Não consegui obter o tamanho dos nativos da webrtc-java | Medir na Fase 0. O Webcam, com cerca de 87 MB, está publicado no Modrinth |
| Revisão do Modrinth demora | O [verificador automático](https://support.modrinth.com/en/articles/8793355-modrinth-project-review-times) pode pedir análise manual | Código aberto vinculado e explicação dos nativos na descrição |
| Dois mods capturando o microfone | Simple Voice Chat instalado junto | Opção de desligar o áudio do CraftOffice |

O jogo sai a cada três meses: 26.1 em março, 26.2 em junho, 26.3 em setembro. A 26.4 já tem snapshots, então convém esperar um port perto de dezembro.

### Se a Fase 0 falhar

1. **livekit-ffi com LiveKit próprio.** É a biblioteca nativa que o StreamCraft declara embarcar, e ele roda no Fabric 26.3. São [quatro funções em C](https://raw.githubusercontent.com/livekit/python-sdks/main/livekit-rtc/livekit/rtc/_ffi_client.py) chamadas a partir do Java. Exige o servidor LiveKit desde o início e outra biblioteca para capturar câmera e tela.
2. **Aba do navegador.** O mod só informa quem está perto, e uma página local cuida de câmera, microfone e conexão. Fica pronto em dias, mas o vídeo não aparece dentro do jogo.

### Privacidade

Um mod de câmera só é aceitável se nunca surpreender ninguém.

- Câmera e microfone começam desligados em toda entrada no servidor.
- A tela de consentimento aparece uma vez por servidor e diz para onde a mídia vai.
- Quem está transmitindo vê um indicador fixo na HUD, e os outros veem um ícone ao lado do nome.
- A tela a compartilhar é escolhida de novo a cada vez.
- Uma tecla de pânico corta toda a captura.
- Nada é gravado e o servidor não guarda mídia.

O README deve recomendar servidores fechados, com lista de permitidos. A [LGPD](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm) não se aplica ao uso particular e não econômico entre pessoas (art. 4º, I). Uma empresa que adote o mod como ferramenta de trabalho sai dessa exceção e precisa tratar imagem e voz como dado pessoal. Isso é leitura da lei, não parecer jurídico.

## Portfólio e publicação

Para portfólio, o repositório no GitHub com um GIF da cena de aproximação já cumpre o objetivo. Publicar no Modrinth é opcional e tem uma regra sobre IA que pode barrar o projeto.

### Estrutura do repositório

```text
CraftOffice/
  src/main/java/.../craftoffice/
    CraftOffice.java         entrada comum
    net/                     avisos entre cliente e servidor
    proximity/               motor de proximidade, sem tipos do Minecraft
    zone/                    zonas, trancas e status
    signal/                  repasse da negociação
  src/client/java/.../craftoffice/client/
    CraftOfficeClient.java   entrada do cliente
    media/                   captura, sessões e áudio
    render/                  textura, mundo e HUD
    ui/                      teclas, telas e indicadores
  src/test/java/             testes do motor
  docs/                      diagrama e GIF
  .github/workflows/build.yml
  README.md
```

### README, nesta ordem

1. Uma frase e o GIF: dois players se aproximam e as câmeras abrem.
2. Vídeo de 60 a 90 segundos.
3. Tabela do que o Gather faz e do que o CraftOffice faz.
4. Instalação no cliente e no servidor, em até 5 passos.
5. Como funciona, com o diagrama da arquitetura.
6. Privacidade: o que sai da máquina e para onde.
7. Compatibilidade: versão do jogo, sistemas, Sodium, Iris e Simple Voice Chat.
8. Licença e aviso da Mojang.

### Regras que afetam a publicação

| Regra | O que exige |
| --- | --- |
| [Modrinth, regra 1.11](https://modrinth.com/legal/rules) | Declarar todo dado enviado a um servidor que o jogador não escolheu no jogo. Vale para STUN e TURN, na descrição e na tela de consentimento |
| [Modrinth, seção 6](https://modrinth.com/legal/rules) | Declarar código feito com IA. O projeto não pode ser inteira ou principalmente saída de IA. Ícone, galeria e descrição não aceitam imagem gerada por IA |
| [Diretrizes da Mojang](https://www.minecraft.net/en-us/usage-guidelines) | Minecraft não pode ser o nome principal. Incluir o aviso de que não é produto oficial |
| [EULA do Minecraft](https://www.minecraft.net/en-us/eula) | O mod precisa ser gratuito |

A seção 6 pesa para quem desenvolve com IA. Se a maior parte do código vier de um assistente, o Modrinth pode recusar. O release no GitHub não depende disso.

O nome CraftOffice está livre no Modrinth: a [busca](https://api.modrinth.com/v2/search?query=craftoffice&limit=6) não retornou nada em 05/10/2026. Na descrição, o Gather entra só como comparação, nunca no nome.

### Automação

- **Compilação.** O [build.yml do modelo do Fabric](https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/.github/workflows/build.yml) já compila com Java 25 e guarda o jar a cada push.
- **Publicação.** A ação [mc-publish](https://github.com/Kir-Antipov/mc-publish) envia para GitHub Releases, Modrinth e CurseForge a partir de uma tag.
- **Versão.** `0.1.0+26.3`, no mesmo formato que o Simple Voice Chat usa.

## Fontes

Pesquisa feita em 05/10/2026. O que não pôde ser confirmado para a 26.3 está marcado no próprio texto.

**Minecraft e Fabric**

- Blog do Fabric: [26.3](https://fabricmc.net/2026/09/15/263.html), [26.2](https://fabricmc.net/2026/06/15/262.html), [26.1](https://fabricmc.net/2026/03/14/261.html) e [fim da ofuscação](https://fabricmc.net/2025/10/31/obfuscation.html)
- [fabric-example-mod, branch 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3) e [Fabric API, branch 26.3](https://github.com/FabricMC/fabric-api/tree/26.3)
- [Guia de migração para o Fabric API 26.1](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api) e [documentação de rede](https://docs.fabricmc.net/develop/networking)
- Primers da NeoForge: [26.1](https://github.com/neoforged/.github/blob/main/primers/26.1/index.md), [26.2](https://github.com/neoforged/.github/blob/main/primers/26.2/index.md) e [26.3](https://github.com/neoforged/.github/blob/main/primers/26.3/index.md)
- [Minecraft Wiki: Java Edition 26.3](https://minecraft.wiki/w/Java_Edition_26.3) e [manifesto de versões da Mojang](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Mojang sobre Vulkan e o fim do OpenGL](https://www.minecraft.net/en-us/article/another-step-towards-vibrant-visuals-for-java-edition)

**Mídia e transporte**

- webrtc-java: [repositório](https://github.com/devopvoid/webrtc-java), [guia](https://jrtc.dev/guide/get-started), [histórico de versões](https://raw.githubusercontent.com/devopvoid/webrtc-java/main/CHANGELOG.md) e [codecs](https://raw.githubusercontent.com/devopvoid/webrtc-java/main/docs/guide/advanced/video-codecs.md)
- LiveKit: [servidor](https://github.com/livekit/livekit), [portas](https://docs.livekit.io/home/self-hosting/ports-firewall/), [SDK em Rust e FFI](https://github.com/livekit/rust-sdks) e [perfis de vídeo](https://raw.githubusercontent.com/livekit/client-sdk-js/main/src/room/track/options.ts)
- Cloudflare Realtime: [preço](https://developers.cloudflare.com/realtime/sfu/pricing/) e [API](https://developers.cloudflare.com/realtime/sfu/https-api/)
- [Licença do binário OpenH264 da Cisco](https://www.openh264.org/BINARY_LICENSE.txt)

**Mods e produtos de referência**

- [StreamCraft Live](https://modrinth.com/mod/streamcraft-live) e [notas do autor](https://raw.githubusercontent.com/slashdaemon/StreamCraft-Releases/main/README.md)
- [Webcam, de DimasKama](https://github.com/DimasKama/Webcam) e [sua licença](https://github.com/DimasKama/Webcam/blob/master/LICENSE.md)
- [WebcamMod, de Lichcode](https://github.com/Lichcodes/WebcamMod)
- Simple Voice Chat: [página](https://modrinth.com/plugin/simple-voice-chat), [API](https://modrepo.de/minecraft/voicechat/api/getting_started) e [nota sobre macOS](https://modrepo.de/minecraft/voicechat/wiki/macos)
- WorkAdventure: [versão 1.27.0](https://workadventu.re/release/workadventure-1-27-0-the-road-to-workadventure-2/) e [regras de grupo](https://raw.githubusercontent.com/workadventure/workadventure/develop/back/src/Model/GameRoom.ts)
- Gather: [preços de 2025](https://support.gather.town/hc/en-us/articles/39590892978196-Understanding-Gather-s-pricing-changes-2025), [1.0 e 2.0](https://support.gather.town/articles/2163640255-gather-1-0-vs-gather-2-0), [distâncias](https://support.gather.town/articles/3513901174-best-practices-in-office-design), [áreas privadas](https://support.gather.town/articles/2550999600-overview-of-meeting-rooms-private-areas) e [spotlight](https://support.gather.town/articles/5684809314-spotlight-tiles)

**Publicação**

- [Regras de conteúdo do Modrinth](https://modrinth.com/legal/rules) e [prazos de revisão](https://support.modrinth.com/en/articles/8793355-modrinth-project-review-times)
- [Diretrizes de uso](https://www.minecraft.net/en-us/usage-guidelines) e [EULA](https://www.minecraft.net/en-us/eula) do Minecraft
- [mc-publish](https://github.com/Kir-Antipov/mc-publish)
- [LGPD, Lei 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
