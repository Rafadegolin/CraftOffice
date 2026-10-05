package dev.rafadegolin.craftoffice.client.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.media.MediaDevices;
import dev.onvoid.webrtc.media.audio.AudioDevice;
import dev.onvoid.webrtc.media.audio.AudioOptions;
import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.audio.AudioTrackSource;
import dev.onvoid.webrtc.media.video.VideoCaptureCapability;
import dev.onvoid.webrtc.media.video.VideoDevice;
import dev.onvoid.webrtc.media.video.VideoDeviceSource;
import dev.onvoid.webrtc.media.video.VideoTrack;
import dev.onvoid.webrtc.media.video.VideoTrackSource;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Dono de toda a mídia do cliente. Todas as chamadas à webrtc-java passam pela
 * thread {@code craftoffice-media}, nunca pela thread do jogo.
 */
public final class MediaEngine {
	private static MediaEngine instance;

	/** Liga o padrão de teste no lugar da webcam (segundo cliente na mesma máquina). */
	public static final boolean TEST_PATTERN = Boolean.getBoolean("craftoffice.testPattern");

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "craftoffice-media");
		t.setDaemon(true);
		return t;
	});

	private PeerConnectionFactory factory;
	private volatile String loadError;
	private volatile long loadMillis = -1;

	private AudioTrackSource audioSource;
	private AudioTrack audioTrack;

	private VideoTrackSource videoSource;
	private VideoTrack videoTrack;
	private TestPatternSource pattern;
	private final FrameSlot selfSlot = new FrameSlot();
	private volatile boolean videoOn;

	private final Map<UUID, PeerSession> sessions = new ConcurrentHashMap<>();

	public static synchronized MediaEngine get() {
		if (instance == null) {
			instance = new MediaEngine();
			instance.run(instance::load);
		}
		return instance;
	}

	public static synchronized MediaEngine getIfLoaded() {
		return instance;
	}

	public void run(Runnable task) {
		executor.execute(() -> {
			try {
				task.run();
			}
			catch (Throwable t) {
				CraftOffice.LOGGER.error("Erro na thread de mídia", t);
			}
		});
	}

	private void load() {
		long start = System.nanoTime();
		try {
			factory = new PeerConnectionFactory();

			AudioOptions options = new AudioOptions();
			options.echoCancellation = true;
			options.noiseSuppression = true;
			options.autoGainControl = true;
			options.highpassFilter = true;
			audioSource = factory.createAudioSource(options);
			audioTrack = factory.createAudioTrack("mic", audioSource);

			loadMillis = (System.nanoTime() - start) / 1_000_000;
			CraftOffice.LOGGER.info("webrtc-java carregada em {} ms", loadMillis);
			describeDevices().forEach(line -> CraftOffice.LOGGER.info("  {}", line));
		}
		catch (Throwable t) {
			loadError = t.toString();
			CraftOffice.LOGGER.error("webrtc-java não carregou", t);
		}
	}

	public boolean ready() {
		return factory != null;
	}

	public String loadError() {
		return loadError;
	}

	public long loadMillis() {
		return loadMillis;
	}

	PeerConnectionFactory factory() {
		return factory;
	}

	AudioTrack audioTrack() {
		return audioTrack;
	}

	VideoTrack videoTrack() {
		return videoTrack;
	}

	public FrameSlot selfSlot() {
		return selfSlot;
	}

	public boolean videoOn() {
		return videoOn;
	}

	/** Lista de dispositivos, para o comando {@code /office devices}. Roda na thread de mídia. */
	public List<String> describeDevices() {
		List<String> lines = new ArrayList<>();
		for (VideoDevice device : MediaDevices.getVideoCaptureDevices()) {
			List<VideoCaptureCapability> caps = MediaDevices.getVideoCaptureCapabilities(device);
			lines.add("Câmera: " + device.getName() + " (" + caps.size() + " formatos)");
		}
		for (AudioDevice device : MediaDevices.getAudioCaptureDevices()) {
			lines.add("Microfone: " + device.getName());
		}
		for (AudioDevice device : MediaDevices.getAudioRenderDevices()) {
			lines.add("Saída: " + device.getName());
		}
		return lines;
	}

	/** Liga ou desliga o vídeo local. Roda na thread de mídia. */
	public void setVideo(boolean on) {
		if (on == videoOn || !ready()) {
			return;
		}

		if (on) {
			if (TEST_PATTERN) {
				pattern = new TestPatternSource();
				videoSource = pattern.source();
				pattern.start();
			}
			else {
				List<VideoDevice> cameras = MediaDevices.getVideoCaptureDevices();
				if (cameras.isEmpty()) {
					CraftOffice.LOGGER.warn("Nenhuma câmera encontrada");
					return;
				}
				VideoDevice camera = cameras.getFirst();
				VideoDeviceSource source = new VideoDeviceSource();
				source.setVideoCaptureDevice(camera);
				source.setVideoCaptureCapability(pickCapability(camera));
				source.start();
				videoSource = source;
			}

			videoTrack = factory.createVideoTrack("cam", videoSource);
			videoTrack.addSink(selfSlot);
			videoOn = true;
			sessions.values().forEach(PeerSession::attachVideo);
		}
		else {
			videoOn = false;
			sessions.values().forEach(PeerSession::detachVideo);
			videoTrack.removeSink(selfSlot);
			videoTrack.dispose();
			videoTrack = null;
			if (pattern != null) {
				pattern.stop();
				pattern.source().dispose();
				pattern = null;
			}
			if (videoSource instanceof VideoDeviceSource device) {
				device.stop();
				device.dispose();
			}
			videoSource = null;
		}
	}

	/** O menor formato que cubra 320x240, com quadros por segundo mais próximos de 20. */
	private static VideoCaptureCapability pickCapability(VideoDevice camera) {
		VideoCaptureCapability best = null;
		for (VideoCaptureCapability cap : MediaDevices.getVideoCaptureCapabilities(camera)) {
			if (cap.width < FrameSlot.WIDTH || cap.height < FrameSlot.HEIGHT || cap.frameRate < 15) {
				continue;
			}
			if (best == null
					|| cap.width * cap.height < best.width * best.height
					|| (cap.width * cap.height == best.width * best.height
							&& Math.abs(cap.frameRate - 20) < Math.abs(best.frameRate - 20))) {
				best = cap;
			}
		}
		VideoCaptureCapability chosen = best != null ? best : new VideoCaptureCapability(640, 480, 30);
		CraftOffice.LOGGER.info("Câmera {} em {}", camera.getName(), chosen);
		return chosen;
	}

	public Map<UUID, PeerSession> sessions() {
		return sessions;
	}

	/** Abre ou reaproveita a sessão com um player. Roda na thread de mídia. */
	public PeerSession session(UUID peer, boolean initiator) {
		return sessions.computeIfAbsent(peer, id -> new PeerSession(this, id, initiator));
	}

	public void closeSession(UUID peer) {
		PeerSession session = sessions.remove(peer);
		if (session != null) {
			session.close();
		}
	}

	public void closeAll() {
		for (UUID peer : List.copyOf(sessions.keySet())) {
			closeSession(peer);
		}
	}
}
