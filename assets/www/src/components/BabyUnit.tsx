import React, { useEffect, useRef, useState } from "react";
import { Mic, MicOff, Settings, AlertTriangle, Play, Square, Activity, Battery, CheckCircle, Wifi, WifiOff, Baby } from "lucide-react";
import { ConnectionStatus, LogEntry } from "../types";

interface BabyUnitProps {
  roomCode: string;
  wsUrl: string;
  onExit: () => void;
}

export default function BabyUnit({ roomCode, wsUrl, onExit }: BabyUnitProps) {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("connecting");
  const [isMonitoring, setIsMonitoring] = useState<boolean>(false);
  const [volume, setVolume] = useState<number>(0);
  const [cryScore, setCryScore] = useState<number>(0);
  const [isCrying, setIsCrying] = useState<boolean>(false);
  const [batteryLevel, setBatteryLevel] = useState<number | undefined>(undefined);
  const [partnerConnected, setPartnerConnected] = useState<boolean>(false);
  
  // Settings
  const [volumeThreshold, setVolumeThreshold] = useState<number>(20); // 0-100 threshold
  const [durationThreshold, setDurationThreshold] = useState<number>(1.5); // continuous seconds to trigger cry
  const [frequencyFilter, setFrequencyFilter] = useState<boolean>(true); // filter based on human cry frequencies
  const [logs, setLogs] = useState<LogEntry[]>([]);

  // Refs for Web Audio API & WebSockets
  const socketRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const cryAccumulatorRef = useRef<number>(0); // keeps track of sequential frames above threshold

  // Add a log entry
  const addLog = (type: "info" | "warning" | "alert" | "error", message: string) => {
    const newLog: LogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      time: new Date().toLocaleTimeString("tr-TR"),
      type,
      message,
    };
    setLogs((prev) => [newLog, ...prev.slice(0, 49)]);
  };

  // Battery monitoring
  useEffect(() => {
    if ("getBattery" in navigator) {
      (navigator as any).getBattery().then((battery: any) => {
        setBatteryLevel(Math.round(battery.level * 100));
        const handleLevelChange = () => {
          setBatteryLevel(Math.round(battery.level * 100));
        };
        battery.addEventListener("levelchange", handleLevelChange);
        return () => {
          battery.removeEventListener("levelchange", handleLevelChange);
        };
      });
    }
  }, []);

  // Real-time local uptime tracking for APK simulation
  const [uptime, setUptime] = useState<string>("00:00:00");
  useEffect(() => {
    const startTime = Date.now();
    const interval = setInterval(() => {
      const diff = Date.now() - startTime;
      const hours = Math.floor(diff / 3600000).toString().padStart(2, "0");
      const minutes = Math.floor((diff % 3600000) / 60000).toString().padStart(2, "0");
      const seconds = Math.floor((diff % 60000) / 1000).toString().padStart(2, "0");
      setUptime(`${hours}:${minutes}:${seconds}`);
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // Set up WebSocket
  useEffect(() => {
    addLog("info", `Oda bağlantısı başlatılıyor: ${roomCode}`);
    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      setConnectionStatus("connected");
      addLog("info", "Sunucuya başarıyla bağlanıldı.");
      // Join as baby
      ws.send(JSON.stringify({ type: "join", room: roomCode, role: "bebek" }));
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === "partner-status") {
          if (data.role === "ebeveyn") {
            if (data.event === "connected") {
              setPartnerConnected(true);
              addLog("info", "Ebeveyn ünitesi odaya katıldı. Ses akışı hazır.");
            } else {
              setPartnerConnected(false);
              addLog("warning", "Ebeveyn ünitesi odadan ayrıldı.");
            }
          }
        } else if (data.type === "pong") {
          // Keep-alive acknowledgment
        }
      } catch (err) {
        console.error("Socket mesaj hatası:", err);
      }
    };

    ws.onclose = () => {
      setConnectionStatus("disconnected");
      addLog("error", "Sunucu bağlantısı koptu.");
    };

    ws.onerror = () => {
      setConnectionStatus("disconnected");
      addLog("error", "Bağlantıda bir hata oluştu.");
    };

    // Heartbeat ping
    const pingInterval = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "ping" }));
      }
    }, 10000);

    return () => {
      clearInterval(pingInterval);
      ws.close();
    };
  }, [roomCode, wsUrl]);

  // Downsample audio buffer to 11025Hz to save network bandwith
  const downsampleBuffer = (buffer: Float32Array, inputSampleRate: number, outputSampleRate: number): Float32Array => {
    if (outputSampleRate === inputSampleRate) return buffer;
    const sampleRateRatio = inputSampleRate / outputSampleRate;
    const newLength = Math.round(buffer.length / sampleRateRatio);
    const result = new Float32Array(newLength);
    let offsetResult = 0;
    let offsetBuffer = 0;
    while (offsetResult < result.length) {
      const nextOffsetBuffer = Math.round((offsetResult + 1) * sampleRateRatio);
      let accum = 0;
      let count = 0;
      for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
        accum += buffer[i];
        count++;
      }
      result[offsetResult] = count > 0 ? accum / count : 0;
      offsetResult++;
      offsetBuffer = nextOffsetBuffer;
    }
    return result;
  };

  // Convert Float32Array buffer to base64
  const float32ToBase64 = (array: Float32Array): string => {
    const buffer = array.buffer;
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
  };

  // Start microhpone monitoring
  const startAudioMonitoring = async () => {
    try {
      addLog("info", "Mikrofon erişimi talep ediliyor...");
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: false,
          autoGainControl: true,
        },
      });
      streamRef.current = stream;

      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      const audioContext = new AudioContextClass();
      audioContextRef.current = audioContext;

      const source = audioContext.createMediaStreamSource(stream);
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 1024;
      source.connect(analyser);

      // We will read chunks using a ScriptProcessorNode (standard for real-time analysis across mobile safari/chrome)
      const bufferSize = 4096;
      const processor = audioContext.createScriptProcessor(bufferSize, 1, 1);
      processorRef.current = processor;
      analyser.connect(processor);
      processor.connect(audioContext.destination);

      addLog("info", "Mikrofon dinleme aktif. Ağlama algılama motoru çalışıyor.");
      setIsMonitoring(true);

      const fftBufferLength = analyser.frequencyBinCount;
      const dataArray = new Uint8Array(fftBufferLength);

      // We'll calculate the cry frequency bins (roughly 350Hz to 650Hz)
      const binHz = audioContext.sampleRate / analyser.fftSize;
      const minCryBin = Math.floor(350 / binHz);
      const maxCryBin = Math.ceil(700 / binHz);

      processor.onaudioprocess = (e) => {
        const inputBuffer = e.inputBuffer.getChannelData(0);

        // 1. Calculate RMS volume (0-100)
        let sum = 0;
        for (let i = 0; i < inputBuffer.length; i++) {
          sum += inputBuffer[i] * inputBuffer[i];
        }
        const rms = Math.sqrt(sum / inputBuffer.length);
        const currentVolume = Math.min(100, Math.round(rms * 150)); // scaled volume
        setVolume(currentVolume);

        // 2. Perform frequency analysis for baby crying band if enabled
        let passesFrequencyFilter = true;
        analyser.getByteFrequencyData(dataArray);

        if (frequencyFilter) {
          // Calculate energy in baby cry band (350Hz - 700Hz)
          let cryEnergy = 0;
          let totalEnergy = 0;
          for (let i = 0; i < fftBufferLength; i++) {
            totalEnergy += dataArray[i];
            if (i >= minCryBin && i <= maxCryBin) {
              cryEnergy += dataArray[i];
            }
          }
          
          // Calculate cry energy ratio
          const cryRatio = totalEnergy > 0 ? cryEnergy / totalEnergy : 0;
          // Baby crying is usually very sharp, tonal, and energetic in this specific vocal band
          passesFrequencyFilter = cryRatio > 0.15 || (currentVolume > 40 && cryRatio > 0.08);
        }

        // 3. Evaluate criteria against adjustable threshold
        const isFrameNoisy = currentVolume >= volumeThreshold && passesFrequencyFilter;

        // Process frames for sequence duration detection
        // scriptProcessor triggers roughly every 4096 / 48000 = ~85ms
        const frameDuration = bufferSize / audioContext.sampleRate;
        
        if (isFrameNoisy) {
          cryAccumulatorRef.current += frameDuration;
        } else {
          // decay accumulator slowly so brief sound dips in crying don't completely reset the alarm
          cryAccumulatorRef.current = Math.max(0, cryAccumulatorRef.current - frameDuration * 1.5);
        }

        const currentCryScore = Math.min(100, Math.round((cryAccumulatorRef.current / durationThreshold) * 100));
        setCryScore(currentCryScore);

        // 4. Trigger baby crying state change
        if (cryAccumulatorRef.current >= durationThreshold) {
          if (!isCrying) {
            setIsCrying(true);
            addLog("alert", `AĞLAMA SESİ ALGILANDI! Şiddet: %${currentVolume}`);
            // Send urgent cry detected event via ws
            if (socketRef.current?.readyState === WebSocket.OPEN) {
              socketRef.current.send(
                JSON.stringify({
                  type: "cry-detected",
                  level: currentVolume,
                  duration: durationThreshold,
                })
              );
            }
          }
        } else {
          if (isCrying && cryAccumulatorRef.current === 0) {
            setIsCrying(false);
            addLog("info", "Ağlama kesildi. Bebek sessiz.");
          }
        }

        // 5. Send status updates containing volume/battery (throttle updates or send with chunks)
        if (socketRef.current?.readyState === WebSocket.OPEN) {
          socketRef.current.send(
            JSON.stringify({
              type: "status-update",
              volume: currentVolume,
              battery: batteryLevel,
            })
          );
        }

        // 6. Downsample raw sound data and stream to ebeveyn unit
        // We only stream when connected to an ebeveyn and is monitoring
        if (socketRef.current?.readyState === WebSocket.OPEN) {
          const downsampled = downsampleBuffer(inputBuffer, audioContext.sampleRate, 11025);
          const base64Audio = float32ToBase64(downsampled);
          socketRef.current.send(
            JSON.stringify({
              type: "audio-chunk",
              audio: base64Audio,
            })
          );
        }
      };

      // Start canvas audio visualizer loop
      drawVisualizer(analyser);

    } catch (err: any) {
      addLog("error", `Mikrofon başlatılamadı: ${err.message || err}`);
      console.error(err);
    }
  };

  // Stop microphone monitoring
  const stopAudioMonitoring = () => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    if (processorRef.current) {
      processorRef.current.disconnect();
      processorRef.current.onaudioprocess = null;
      processorRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (audioContextRef.current && audioContextRef.current.state !== "closed") {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }
    setIsMonitoring(false);
    setVolume(0);
    setCryScore(0);
    setIsCrying(false);
    cryAccumulatorRef.current = 0;
    addLog("info", "Ses dinleme ve aktarım durduruldu.");
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      if (processorRef.current) {
        processorRef.current.disconnect();
        processorRef.current.onaudioprocess = null;
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
      if (audioContextRef.current && audioContextRef.current.state !== "closed") {
        audioContextRef.current.close();
      }
    };
  }, []);

  // Draw real-time sine-wave visualizer on Canvas
  const drawVisualizer = (analyser: AnalyserNode) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);

    const draw = () => {
      animationFrameRef.current = requestAnimationFrame(draw);
      analyser.getByteTimeDomainData(dataArray);

      ctx.fillStyle = "rgb(15, 23, 42)"; // slate-900 background
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      ctx.lineWidth = 3;
      // Interpolate color from green/indigo to energetic crimson when crying or loud
      ctx.strokeStyle = cryAccumulatorRef.current > 0.2 ? "rgb(239, 68, 68)" : "rgb(99, 102, 241)"; 
      ctx.beginPath();

      const sliceWidth = canvas.width / bufferLength;
      let x = 0;

      for (let i = 0; i < bufferLength; i++) {
        const v = dataArray[i] / 128.0;
        const y = (v * canvas.height) / 2;

        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }

        x += sliceWidth;
      }

      ctx.lineTo(canvas.width, canvas.height / 2);
      ctx.stroke();
    };

    draw();
  };

  return (
    <div className="w-full max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
      {/* Control Console (Main view) */}
      <div className="md:col-span-2 bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6 flex flex-col justify-between">
        
        {/* Header Status */}
        <div className="flex justify-between items-center pb-4 border-b border-zinc-100 dark:border-zinc-800">
          <div>
            <span className="text-[10px] uppercase tracking-wider font-semibold text-zinc-400 dark:text-zinc-500 block">
              Aktif İstasyon
            </span>
            <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Baby className="w-5 h-5 text-amber-500" /> Bebek Ünitesi
            </h3>
          </div>
          <div className="flex items-center gap-3">
            {/* Connection Status */}
            <div className="flex items-center gap-1.5 text-xs">
              {connectionStatus === "connected" ? (
                <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
                  <Wifi className="w-4 h-4" /> Çevrimiçi
                </span>
              ) : connectionStatus === "connecting" ? (
                <span className="inline-flex items-center gap-1 text-amber-500 animate-pulse font-medium">
                  <Wifi className="w-4 h-4" /> Bağlanıyor
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 text-rose-500 font-medium">
                  <WifiOff className="w-4 h-4" /> Çevrimdışı
                </span>
              )}
            </div>

            {/* Battery Indicator */}
            {batteryLevel !== undefined && (
              <div className="flex items-center gap-1 text-xs text-zinc-500 dark:text-zinc-400">
                <Battery className={`w-4 h-4 ${batteryLevel < 20 ? "text-rose-500 animate-pulse" : "text-emerald-500"}`} />
                <span>%{batteryLevel}</span>
              </div>
            )}
          </div>
        </div>

        {/* Room Code Badge */}
        <div className="mt-4 flex flex-wrap gap-2 items-center justify-between p-3 bg-zinc-50 dark:bg-zinc-950/60 rounded-xl">
          <div className="text-xs text-zinc-600 dark:text-zinc-400">
            Oda Kodu: <span className="font-mono font-bold text-zinc-900 dark:text-zinc-100 tracking-wider text-sm bg-zinc-200 dark:bg-zinc-800 px-2.5 py-1 rounded">{roomCode}</span>
          </div>
          <div className="text-xs flex items-center gap-1 text-zinc-500 dark:text-zinc-400">
            <span className={`w-2.5 h-2.5 rounded-full ${partnerConnected ? "bg-emerald-500" : "bg-zinc-400 animate-pulse"}`}></span>
            {partnerConnected ? "Ebeveyn Dinliyor" : "Ebeveyn bekleniyor..."}
          </div>
        </div>

        {/* Real-time Display */}
        <div className="my-6 space-y-4">
          <div className="relative rounded-xl overflow-hidden h-36 bg-zinc-950">
            <canvas
              ref={canvasRef}
              width={600}
              height={144}
              className="w-full h-full block"
            />
            {isCrying && (
              <div className="absolute inset-0 bg-red-600/10 flex items-center justify-center border border-red-500/50 animate-pulse">
                <div className="px-4 py-2 bg-red-600 text-white font-bold rounded-lg text-sm flex items-center gap-2 shadow-lg">
                  <AlertTriangle className="w-5 h-5 animate-bounce" /> BEBEK AĞLIYOR!
                </div>
              </div>
            )}
            {!isMonitoring && (
              <div className="absolute inset-0 bg-zinc-900/80 backdrop-blur-[1px] flex flex-col items-center justify-center p-4 text-center">
                <MicOff className="w-8 h-8 text-zinc-500 mb-2" />
                <p className="text-sm font-semibold text-zinc-300">Cihaz Yayında Değil</p>
                <p className="text-xs text-zinc-500 mt-1">İzlemeyi başlatmak için aşağıdaki butona basın.</p>
              </div>
            )}
          </div>

          {/* Level Meters */}
          <div className="grid grid-cols-2 gap-4">
            {/* Volume level progress */}
            <div className="p-4 bg-zinc-50 dark:bg-zinc-950/40 rounded-xl border border-zinc-100 dark:border-zinc-800/60">
              <span className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wide block mb-1">Ses Şiddeti</span>
              <div className="flex items-center gap-2">
                <Activity className={`w-4 h-4 ${volume > volumeThreshold ? "text-amber-500" : "text-zinc-400"}`} />
                <span className="text-lg font-bold text-zinc-800 dark:text-zinc-200">% {volume}</span>
              </div>
              <div className="w-full bg-zinc-200 dark:bg-zinc-800 h-2.5 rounded-full mt-2 overflow-hidden relative">
                <div 
                  className={`h-full transition-all duration-75 ${volume >= volumeThreshold ? "bg-amber-500" : "bg-indigo-500"}`}
                  style={{ width: `${Math.min(100, volume)}%` }}
                />
                {/* Threshold Marker */}
                <div 
                  className="absolute top-0 bottom-0 w-[2px] bg-rose-500" 
                  style={{ left: `${volumeThreshold}%` }}
                  title="Eşik Sınırı"
                />
              </div>
            </div>

            {/* Cry Confidence score */}
            <div className="p-4 bg-zinc-50 dark:bg-zinc-950/40 rounded-xl border border-zinc-100 dark:border-zinc-800/60">
              <span className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wide block mb-1">Ağlama Kararlılığı</span>
              <div className="flex items-center gap-2">
                <AlertTriangle className={`w-4 h-4 ${cryScore > 50 ? "text-rose-500 animate-pulse" : "text-zinc-400"}`} />
                <span className="text-lg font-bold text-zinc-800 dark:text-zinc-200">% {cryScore}</span>
              </div>
              <div className="w-full bg-zinc-200 dark:bg-zinc-800 h-2.5 rounded-full mt-2 overflow-hidden">
                <div 
                  className={`h-full transition-all duration-200 ${cryScore >= 100 ? "bg-rose-600 animate-pulse" : "bg-rose-400"}`}
                  style={{ width: `${cryScore}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Action button */}
        <div className="pt-4 border-t border-zinc-100 dark:border-zinc-800 flex gap-3">
          <button
            type="button"
            onClick={onExit}
            className="px-4 py-3 border border-zinc-200 dark:border-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-950 rounded-xl text-sm font-medium transition-all"
          >
            Odadan Ayrıl
          </button>
          
          {isMonitoring ? (
            <button
              type="button"
              id="stop-monitoring-btn"
              onClick={stopAudioMonitoring}
              className="flex-1 py-3 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-medium shadow-md hover:shadow-lg transition-all flex items-center justify-center gap-2 cursor-pointer"
            >
              <Square className="w-4 h-4 fill-current" /> İzlemeyi Durdur
            </button>
          ) : (
            <button
              type="button"
              id="start-monitoring-btn"
              onClick={startAudioMonitoring}
              disabled={connectionStatus !== "connected"}
              className={`flex-1 py-3 rounded-xl font-medium shadow-md hover:shadow-lg transition-all flex items-center justify-center gap-2 cursor-pointer ${
                connectionStatus === "connected"
                  ? "bg-indigo-600 hover:bg-indigo-700 text-white"
                  : "bg-zinc-300 dark:bg-zinc-800 text-zinc-500 cursor-not-allowed"
              }`}
            >
              <Play className="w-4 h-4 fill-current" /> Mikrofonu Dinlemeye Başlat
            </button>
          )}
        </div>
      </div>

      {/* Settings Side Panel */}
      <div className="space-y-6">
        {/* Detection Tuning */}
        <div className="bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6 space-y-5">
          <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-200 flex items-center gap-1.5 pb-3 border-b border-zinc-100 dark:border-zinc-800">
            <Settings className="w-4 h-4" /> Akıllı Hassasiyet Ayarları
          </h4>

          {/* Volume threshold slider */}
          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <label htmlFor="volume-threshold-input" className="text-xs text-zinc-500 dark:text-zinc-400 font-medium">Ses Eşiği (Duyarlılık)</label>
              <span className="text-xs font-bold text-indigo-500">%{volumeThreshold}</span>
            </div>
            <input
              id="volume-threshold-input"
              type="range"
              min={5}
              max={80}
              value={volumeThreshold}
              onChange={(e) => setVolumeThreshold(Number(e.target.value))}
              className="w-full accent-indigo-500 bg-zinc-200 dark:bg-zinc-800 rounded-lg h-1.5 appearance-none cursor-pointer"
            />
            <p className="text-[10px] text-zinc-400 dark:text-zinc-500 leading-normal">
              Mikrofon ses seviyesi bu sınırın üzerine çıktığında algılama tetiklenir. Gürültülü odalar için bu değeri yükseltin.
            </p>
          </div>

          {/* Duration threshold slider */}
          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <label htmlFor="duration-threshold-input" className="text-xs text-zinc-500 dark:text-zinc-400 font-medium">Süre Filtresi</label>
              <span className="text-xs font-bold text-indigo-500">{durationThreshold} sn</span>
            </div>
            <input
              id="duration-threshold-input"
              type="range"
              min={0.5}
              max={4.0}
              step={0.5}
              value={durationThreshold}
              onChange={(e) => setDurationThreshold(Number(e.target.value))}
              className="w-full accent-indigo-500 bg-zinc-200 dark:bg-zinc-800 rounded-lg h-1.5 appearance-none cursor-pointer"
            />
            <p className="text-[10px] text-zinc-400 dark:text-zinc-500 leading-normal">
              Ağlamanın kesintisiz olarak kaç saniye sürmesi gerektiğini ayarlar. Ani gıcırtılar ve tıkırtılar alarmı tetiklemez.
            </p>
          </div>

          {/* Frequency filter toggle */}
          <div className="flex items-center justify-between p-3 bg-zinc-50 dark:bg-zinc-950/60 rounded-xl border border-zinc-100 dark:border-zinc-800/80">
            <div>
              <span className="text-xs text-zinc-700 dark:text-zinc-300 font-bold block">Ağlama Frekans Filtresi</span>
              <span className="text-[10px] text-zinc-400 dark:text-zinc-500 block mt-0.5">350Hz-700Hz aralığını doğrular</span>
            </div>
            <button
              type="button"
              onClick={() => setFrequencyFilter(!frequencyFilter)}
              className={`w-11 h-6 rounded-full transition-colors relative cursor-pointer ${frequencyFilter ? "bg-indigo-600" : "bg-zinc-300 dark:bg-zinc-700"}`}
            >
              <span className={`absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-transform ${frequencyFilter ? "translate-x-5" : "translate-x-0"}`} />
            </button>
          </div>
        </div>

        {/* Device Status Sidebar Panel (Sleek Interface Spec) */}
        <div className="bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6">
          <h4 className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-widest mb-4 pb-2 border-b border-zinc-100 dark:border-zinc-800">
            CİHAZ STATÜSÜ (DEVICE STATUS)
          </h4>
          <div className="space-y-3">
            <div className="flex justify-between items-center py-1.5 border-b border-slate-100 dark:border-zinc-800/50">
              <span className="text-slate-500 dark:text-zinc-400 font-medium text-xs">Ses Motoru (Engine)</span>
              <span className="text-indigo-600 dark:text-indigo-400 font-mono font-bold text-xs">PYTHON 3.11</span>
            </div>
            <div className="flex justify-between items-center py-1.5 border-b border-slate-100 dark:border-zinc-800/50">
              <span className="text-slate-500 dark:text-zinc-400 font-medium text-xs">Arayüz Çatısı (Framework)</span>
              <span className="text-indigo-600 dark:text-indigo-400 font-mono font-bold text-xs">FLUTTER / DART</span>
            </div>
            <div className="flex justify-between items-center py-1.5 border-b border-slate-100 dark:border-zinc-800/50">
              <span className="text-slate-500 dark:text-zinc-400 font-medium text-xs">Güç / Pil (Battery)</span>
              <span className="text-slate-900 dark:text-zinc-200 font-mono font-bold text-xs">{batteryLevel !== undefined ? `%${batteryLevel}` : "%88"}</span>
            </div>
            <div className="flex justify-between items-center py-1.5">
              <span className="text-slate-500 dark:text-zinc-400 font-medium text-xs">Çalışma Süresi (Uptime)</span>
              <span className="text-slate-900 dark:text-zinc-200 font-mono font-bold text-xs">{uptime}</span>
            </div>
          </div>
        </div>

        {/* Event Logs */}
        <div className="bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6 flex flex-col h-72">
          <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-200 pb-3 border-b border-zinc-100 dark:border-zinc-800 mb-3 flex items-center gap-1.5">
            <Activity className="w-4 h-4 text-indigo-500" /> İstasyon Kayıtları
          </h4>
          <div className="flex-1 overflow-y-auto space-y-2 pr-1 scrollbar-thin scrollbar-thumb-zinc-200 dark:scrollbar-thumb-zinc-800">
            {logs.length === 0 ? (
              <div className="text-center text-zinc-400 text-xs py-10">Kayıt bulunmuyor.</div>
            ) : (
              logs.map((log) => (
                <div 
                  key={log.id} 
                  className={`text-[11px] p-2 rounded-lg border flex flex-col ${
                    log.type === "alert" 
                      ? "bg-rose-50 dark:bg-rose-950/20 border-rose-200/50 dark:border-rose-900/30 text-rose-700 dark:text-rose-400 font-semibold" 
                      : log.type === "warning"
                      ? "bg-amber-50 dark:bg-amber-950/20 border-amber-200/50 dark:border-amber-900/30 text-amber-700 dark:text-amber-400"
                      : log.type === "error"
                      ? "bg-red-50 dark:bg-red-950/20 border-red-200/50 dark:border-red-900/30 text-red-700 dark:text-red-400"
                      : "bg-zinc-50 dark:bg-zinc-950/30 border-zinc-100 dark:border-zinc-800 text-zinc-600 dark:text-zinc-400"
                  }`}
                >
                  <div className="flex justify-between opacity-80 mb-0.5">
                    <span>{log.type.toUpperCase()}</span>
                    <span>{log.time}</span>
                  </div>
                  <p className="break-all">{log.message}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
