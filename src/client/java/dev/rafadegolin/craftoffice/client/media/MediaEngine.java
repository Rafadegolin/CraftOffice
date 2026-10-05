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
import dev.onvoid.webrtc.media.audio.AudioDeviceModule;
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

	private AudioDeviceModule audioModule;
	private volatile String micName = "-";
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
			// Sem escolher, o módulo abre o primeiro microfone da lista, que pode
			// ser uma entrada vazia e mandar silêncio. Usa os padrões do Windows.
			audioModule = new AudioDeviceModule();
			AudioDevice mic = MediaDevices.getDefaultAudioCaptureDevice();
			if (mic != null) {
				audioModule.setRecordingDevice(mic);
				micName = mic.getName();
			}
			AudioDevice speaker = MediaDevices.getDefaultAudioRenderDevice();
			if (speaker != null) {
				audioModule.setPlayoutDevice(speaker);
			}
			CraftOffice.LOGGER.info("Microfone: {}. Saída: {}", micName, speaker != null ? speaker.getName() : "-");
			factory = new PeerConnectionFactory(audioModule);

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

	public String micName() {
		return micName;
	}

	/** Troca o microfone pelo número da lista de {@code /office devices}. Roda na thread de mídia. */
	public String setMicrophone(int index) {
		List<AudioDevice> mics = MediaDevices.getAudioCaptureDevices();
		if (!ready() || index < 1 || index > mics.size()) {
			return "Microfone inválido. Veja os números em /office devices";
		}
		AudioDevice mic = mics.get(index - 1);
		audioModule.stopRecording();
		audioModule.setRecordingDevice(mic);
		audioModule.initRecording();
		audioModule.startRecording();
		micName = mic.getName();
		CraftOffice.LOGGER.info("Microfone trocado para {}", micName);
		return "Microfone: " + micName;
	}

	/** Lista de dispositivos, para o comando {@code /office devices}. Roda na thread de mídia. */
	public List<String> describeDevices() {
		List<String> lines = new ArrayList<>();
		for (VideoDevice device : MediaDevices.getVideoCaptureDevices()) {
			List<VideoCaptureCapability> caps = MediaDevices.getVideoCaptureCapabilities(device);
			lines.add("Câmera: " + device.getName() + " (" + caps.size() + " formatos)");
		}
		int n = 1;
		for (AudioDevice device : MediaDevices.getAudioCaptureDevices()) {
			lines.add("Microfone " + n++ + ": " + device.getName() + (device.getName().equals(micName) ? " (em uso)" : ""));
		}
		for (AudioDevice device : MediaDevices.getAudioRenderDevices()) {
			lines.add("Saída: " + device.getName());
		}
		return lines;
	}

	/**
	 * Liga ou desliga o vídeo local. Roda na thread de mídia.
	 * <p>
	 * A faixa é criada uma vez e reaproveitada: as conexões seguram referência
	 * a ela mesmo depois de fechadas, e destruí-la antes da fábrica falha.
	 * Desligar para a captura (a luz da câmera apaga) e desativa a faixa.
	 */
	public void setVideo(boolean on) {
		if (on == videoOn || !ready()) {
			return;
		}

		if (on) {
			if (videoTrack == null && !createVideoTrack()) {
				return;
			}
			if (pattern != null) {
				pattern.start();
			}
			else if (videoSource instanceof VideoDeviceSource device) {
				device.start();
			}
			videoTrack.setEnabled(true);
			videoOn = true;
			sessions.values().forEach(PeerSession::attachVideo);
		}
		else {
			videoOn = false;
			sessions.values().forEach(PeerSession::detachVideo);
			videoTrack.setEnabled(false);
			if (pattern != null) {
				pattern.stop();
			}
			else if (videoSource instanceof VideoDeviceSource device) {
				device.stop();
			}
		}
	}

	private boolean createVideoTrack() {
		if (TEST_PATTERN) {
			pattern = new TestPatternSource();
			videoSource = pattern.source();
		}
		else {
			// A primeira câmera com formatos. Câmeras virtuais sem formato (OBS) ficam de fora.
			VideoDevice camera = MediaDevices.getVideoCaptureDevices().stream()
					.filter(device -> !MediaDevices.getVideoCaptureCapabilities(device).isEmpty())
					.findFirst()
					.orElse(null);
			if (camera == null) {
				CraftOffice.LOGGER.warn("Nenhuma câmera utilizável encontrada");
				return false;
			}
			VideoDeviceSource source = new VideoDeviceSource();
			source.setVideoCaptureDevice(camera);
			source.setVideoCaptureCapability(pickCapability(camera));
			videoSource = source;
		}

		videoTrack = factory.createVideoTrack("cam", videoSource);
		videoTrack.addSink(selfSlot);
		return true;
	}

	/** Fecha conexões, para a captura e libera a parte nativa, para o jogo conseguir sair. */
	public void shutdown() {
		closeAll();
		setVideo(false);
		if (videoTrack != null) {
			videoTrack.removeSink(selfSlot);
			release("faixa de vídeo", videoTrack::dispose);
		}
		if (pattern != null) {
			release("padrão de teste", pattern.source()::dispose);
		}
		else if (videoSource instanceof VideoDeviceSource device) {
			release("câmera", device::dispose);
		}
		if (audioTrack != null) {
			release("faixa de áudio", audioTrack::dispose);
		}
		if (audioSource != null) {
			release("microfone", audioSource::dispose);
		}
		if (factory != null) {
			release("fábrica", factory::dispose);
		}
		if (audioModule != null) {
			release("módulo de áudio", audioModule::dispose);
		}
		audioModule = null;
		videoTrack = null;
		videoSource = null;
		pattern = null;
		audioTrack = null;
		audioSource = null;
		factory = null;
	}

	private static void release(String what, Runnable dispose) {
		try {
			dispose.run();
		}
		catch (Throwable t) {
			CraftOffice.LOGGER.warn("Falha ao liberar {}: {}", what, t.toString());
		}
	}

	/** {@link #shutdown()} esperando no máximo {@code timeoutMs}. Chamado ao fechar o jogo. */
	public void shutdownAndWait(long timeoutMs) {
		var done = new java.util.concurrent.CountDownLatch(1);
		run(() -> {
			try {
				shutdown();
			}
			finally {
				done.countDown();
			}
		});
		try {
			if (!done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				CraftOffice.LOGGER.warn("A mídia não encerrou em {} ms", timeoutMs);
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		executor.shutdown();
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
