package dev.rafadegolin.craftoffice.client.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCRtpCodecCapability;
import dev.onvoid.webrtc.RTCRtpEncodingParameters;
import dev.onvoid.webrtc.RTCRtpSendParameters;
import dev.onvoid.webrtc.RTCRtpSender;
import dev.onvoid.webrtc.RTCRtpTransceiver;
import dev.onvoid.webrtc.RTCRtpTransceiverDirection;
import dev.onvoid.webrtc.RTCRtpTransceiverInit;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.MediaType;
import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.audio.AudioTrackSink;
import dev.onvoid.webrtc.media.video.VideoTrack;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.client.media.audio.PeerAudio;
import dev.rafadegolin.craftoffice.net.ConfigPayload;
import dev.rafadegolin.craftoffice.net.SignalPayload;

/**
 * Uma conexão WebRTC com outro player. Quem chama manda a oferta, o outro
 * responde. A negociação viaja em {@link SignalPayload} pelo servidor.
 * Todos os métodos rodam na thread de mídia.
 */
public final class PeerSession implements PeerConnectionObserver {

	private final MediaEngine engine;
	private final UUID peer;
	private final boolean initiator;
	private final RTCPeerConnection connection;
	private RTCRtpSender videoSender;
	/** O servidor limita quantos vizinhos trocam vídeo. Fora do limite, só áudio. */
	private volatile boolean videoAllowed;

	private final List<RTCIceCandidate> pendingCandidates = new ArrayList<>();
	private boolean remoteDescriptionSet;

	private final FrameSlot remoteSlot = new FrameSlot();
	private volatile RTCPeerConnectionState state = RTCPeerConnectionState.NEW;
	private volatile String selectedPair = "-";

	/** Números do áudio recebido, para as estatísticas. */
	private final RateCounter remoteAudioCallbacks = new RateCounter();
	private volatile String remoteAudioFormat = "-";
	private volatile int remoteAudioPeak;

	PeerSession(MediaEngine engine, UUID peer, boolean initiator, boolean videoAllowed) {
		this.engine = engine;
		this.peer = peer;
		this.initiator = initiator;
		this.videoAllowed = videoAllowed;

		RTCConfiguration config = new RTCConfiguration();
		for (ConfigPayload.IceServer server : engine.iceServers()) {
			RTCIceServer ice = new RTCIceServer();
			ice.urls.add(server.url());
			if (!server.username().isEmpty()) {
				ice.username = server.username();
				ice.password = server.credential();
			}
			config.iceServers.add(ice);
		}
		connection = engine.factory().createPeerConnection(config, this);

		// Quem atende só anexa as faixas depois de ler a oferta: transceptores
		// criados antes não se casam com os da oferta e a resposta sai sem envio.
		if (initiator) {
			addInitiatorTracks();
			connection.createOffer(new RTCOfferOptions(), describe(RTCSdpType.OFFER));
		}
	}

	private void addInitiatorTracks() {
		RTCRtpTransceiverInit audioInit = new RTCRtpTransceiverInit();
		audioInit.direction = RTCRtpTransceiverDirection.SEND_RECV;
		audioInit.streamIds.add("craftoffice");
		connection.addTransceiver(engine.audioTrack(), audioInit);

		VideoTrack video = engine.videoTrack();
		if (video != null) {
			RTCRtpEncodingParameters encoding = new RTCRtpEncodingParameters();
			encoding.maxBitrate = 140_000;
			encoding.maxFramerate = 20.0;
			RTCRtpTransceiverInit videoInit = new RTCRtpTransceiverInit();
			videoInit.direction = RTCRtpTransceiverDirection.SEND_RECV;
			videoInit.streamIds.add("craftoffice");
			videoInit.sendEncodings.add(encoding);
			RTCRtpTransceiver transceiver = connection.addTransceiver(video, videoInit);
			preferVp8(transceiver);
			videoSender = transceiver.getSender();
			if (!sendingVideo()) {
				detachVideo();
			}
		}
	}

	/** Lado que atende: {@code addTrack} reaproveita os transceptores criados pela oferta. */
	private void addAnswererTracks() {
		connection.addTrack(engine.audioTrack(), List.of("craftoffice"));
		VideoTrack video = engine.videoTrack();
		if (video != null) {
			videoSender = connection.addTrack(video, List.of("craftoffice"));
			if (!sendingVideo()) {
				detachVideo();
			}
		}
	}

	/** Rosto a 140 kbps e 20 quadros, como no estudo. */
	private void limitVideo() {
		if (videoSender == null) {
			return;
		}
		try {
			RTCRtpSendParameters parameters = videoSender.getParameters();
			if (parameters.encodings != null && !parameters.encodings.isEmpty()) {
				parameters.encodings.getFirst().maxBitrate = 140_000;
				parameters.encodings.getFirst().maxFramerate = 20.0;
				videoSender.setParameters(parameters);
			}
		}
		catch (Throwable t) {
			CraftOffice.LOGGER.warn("Não deu para limitar o vídeo: {}", t.toString());
		}
	}

	private void preferVp8(RTCRtpTransceiver transceiver) {
		List<RTCRtpCodecCapability> codecs = new ArrayList<>(
				engine.factory().getRtpSenderCapabilities(MediaType.VIDEO).getCodecs());
		codecs.sort(Comparator.comparing(c -> c.getName().equalsIgnoreCase("VP8") ? 0 : 1));
		transceiver.setCodecPreferences(codecs);
	}

	private CreateSessionDescriptionObserver describe(RTCSdpType type) {
		return new CreateSessionDescriptionObserver() {
			@Override
			public void onSuccess(RTCSessionDescription description) {
				engine.run(() -> connection.setLocalDescription(description, observer("local " + type, () -> {
					send(type == RTCSdpType.OFFER ? "offer" : "answer", description.sdp);
					if (type == RTCSdpType.ANSWER) {
						limitVideo();
					}
				})));
			}

			@Override
			public void onFailure(String error) {
				CraftOffice.LOGGER.warn("Falha ao criar {}: {}", type, error);
			}
		};
	}

	private SetSessionDescriptionObserver observer(String what, Runnable then) {
		return new SetSessionDescriptionObserver() {
			@Override
			public void onSuccess() {
				engine.run(then);
			}

			@Override
			public void onFailure(String error) {
				CraftOffice.LOGGER.warn("Falha em {}: {}", what, error);
			}
		};
	}

	/** Trata um aviso {@code signal} vindo do outro player. */
	public void onSignal(String kind, String data) {
		switch (kind) {
			case "offer" -> connection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, data),
					observer("remote offer", () -> {
						addAnswererTracks();
						flushCandidates();
						connection.createAnswer(new RTCAnswerOptions(), describe(RTCSdpType.ANSWER));
					}));
			case "answer" -> connection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, data),
					observer("remote answer", this::flushCandidates));
			case "ice" -> {
				String[] parts = data.split("\\|", 3);
				RTCIceCandidate candidate = new RTCIceCandidate(parts[0], Integer.parseInt(parts[1]), parts[2]);
				if (remoteDescriptionSet) {
					connection.addIceCandidate(candidate);
				}
				else {
					pendingCandidates.add(candidate);
				}
			}
			default -> CraftOffice.LOGGER.warn("Aviso desconhecido: {}", kind);
		}
	}

	private void flushCandidates() {
		remoteDescriptionSet = true;
		pendingCandidates.forEach(connection::addIceCandidate);
		pendingCandidates.clear();
	}

	private void send(String kind, String data) {
		if (data.length() > SignalPayload.MAX_DATA) {
			CraftOffice.LOGGER.warn("Aviso {} grande demais: {} caracteres", kind, data.length());
			return;
		}
		Minecraft.getInstance().execute(() -> {
			if (ClientPlayNetworking.canSend(SignalPayload.TYPE)) {
				ClientPlayNetworking.send(new SignalPayload(peer, kind, data));
			}
		});
	}

	private boolean sendingVideo() {
		return engine.videoOn() && videoAllowed;
	}

	/** Câmera ligada: manda vídeo se o servidor permitir para este vizinho. */
	void attachVideo() {
		if (videoSender != null && videoAllowed) {
			videoSender.replaceTrack(engine.videoTrack());
		}
	}

	/** O servidor mudou o limite de vídeos. Roda na thread de mídia. */
	public void setVideoAllowed(boolean allowed) {
		videoAllowed = allowed;
		if (sendingVideo()) {
			attachVideo();
		}
		else {
			detachVideo();
		}
	}

	void detachVideo() {
		if (videoSender != null) {
			videoSender.replaceTrack(null);
		}
	}

	void close() {
		send("bye", "");
		connection.close();
		remoteSlot.close();
	}

	@Override
	public void onIceCandidate(RTCIceCandidate candidate) {
		engine.run(() -> send("ice", candidate.sdpMid + "|" + candidate.sdpMLineIndex + "|" + candidate.sdp));
	}

	@Override
	public void onConnectionChange(RTCPeerConnectionState newState) {
		state = newState;
		CraftOffice.LOGGER.info("Conexão com {}: {}", peer, newState);
	}

	@Override
	public void onSelectedCandidatePairChanged(String remoteAddress, int remotePort, String candidateType) {
		selectedPair = candidateType + " " + remoteAddress + ":" + remotePort;
	}

	@Override
	public void onTrack(RTCRtpTransceiver transceiver) {
		MediaStreamTrack track = transceiver.getReceiver().getTrack();
		if (track instanceof VideoTrack video) {
			video.addSink(remoteSlot);
		}
		else if (track instanceof AudioTrack audio) {
			// O áudio de cada vizinho vai para o mixer, que aplica o volume da distância.
			PeerAudio target = engine.mixer().peer(peer);
			audio.addSink((AudioTrackSink) (data, bitsPerSample, sampleRate, channels, frames) -> {
				remoteAudioCallbacks.tick();
				remoteAudioFormat = sampleRate + " Hz, " + channels + " canal(is), " + bitsPerSample + " bits";
				target.onData(data, bitsPerSample, sampleRate, channels, frames);
				remoteAudioPeak = target.peak();
			});
		}
		CraftOffice.LOGGER.info("Faixa remota de {}: {}", peer, track.getKind());
	}

	public UUID peer() {
		return peer;
	}

	public boolean initiator() {
		return initiator;
	}

	public FrameSlot remoteSlot() {
		return remoteSlot;
	}

	public RTCPeerConnectionState state() {
		return state;
	}

	public String selectedPair() {
		return selectedPair;
	}

	public double remoteAudioCallbacksPerSecond() {
		return remoteAudioCallbacks.rate();
	}

	public String remoteAudioFormat() {
		return remoteAudioFormat;
	}

	public int remoteAudioPeak() {
		return remoteAudioPeak;
	}
}
