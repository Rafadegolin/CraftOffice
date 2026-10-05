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
import dev.rafadegolin.craftoffice.net.SignalPayload;

/**
 * Uma conexão WebRTC com outro player. Quem chama manda a oferta, o outro
 * responde. A negociação viaja em {@link SignalPayload} pelo servidor.
 * Todos os métodos rodam na thread de mídia.
 */
public final class PeerSession implements PeerConnectionObserver {
	private static final String STUN = "stun:stun.l.google.com:19302";

	private final MediaEngine engine;
	private final UUID peer;
	private final boolean initiator;
	private final RTCPeerConnection connection;
	private RTCRtpSender videoSender;

	private final List<RTCIceCandidate> pendingCandidates = new ArrayList<>();
	private boolean remoteDescriptionSet;

	private final FrameSlot remoteSlot = new FrameSlot();
	private volatile RTCPeerConnectionState state = RTCPeerConnectionState.NEW;
	private volatile String selectedPair = "-";

	/** Prova de que dá para ler o PCM de cada pessoa, base de um mixer com volume próprio. */
	private final RateCounter remoteAudioCallbacks = new RateCounter();
	private volatile String remoteAudioFormat = "-";
	private volatile int remoteAudioPeak;

	PeerSession(MediaEngine engine, UUID peer, boolean initiator) {
		this.engine = engine;
		this.peer = peer;
		this.initiator = initiator;

		RTCIceServer stun = new RTCIceServer();
		stun.urls.add(STUN);
		RTCConfiguration config = new RTCConfiguration();
		config.iceServers.add(stun);
		connection = engine.factory().createPeerConnection(config, this);

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
		}

		if (initiator) {
			connection.createOffer(new RTCOfferOptions(), describe(RTCSdpType.OFFER));
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
				engine.run(() -> connection.setLocalDescription(description, observer("local " + type, () ->
						send(type == RTCSdpType.OFFER ? "offer" : "answer", description.sdp))));
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

	void attachVideo() {
		if (videoSender != null) {
			videoSender.replaceTrack(engine.videoTrack());
		}
		else {
			CraftOffice.LOGGER.info("Sessão sem faixa de vídeo. Desligue e chame de novo para mandar vídeo");
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
			audio.addSink((AudioTrackSink) this::onRemoteAudio);
		}
		CraftOffice.LOGGER.info("Faixa remota de {}: {}", peer, track.getKind());
	}

	private void onRemoteAudio(byte[] data, int bitsPerSample, int sampleRate, int channels, int frames) {
		remoteAudioCallbacks.tick();
		remoteAudioFormat = sampleRate + " Hz, " + channels + " canal(is), " + bitsPerSample + " bits";
		if (bitsPerSample == 16) {
			int peak = 0;
			for (int i = 0; i + 1 < data.length; i += 2) {
				int sample = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
				peak = Math.max(peak, Math.abs(sample));
			}
			remoteAudioPeak = peak;
		}
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
