import React, { useEffect, useRef, useState } from "react";
import { Shield, Volume2, VolumeX, Bell, BellOff, AlertOctagon, HelpCircle, Wifi, WifiOff, Battery, History, Play } from "lucide-react";
import { ConnectionStatus, LogEntry, BabyState } from "../types";

interface ParentUnitProps {
  roomCode: string;
  wsUrl: string;
  onExit: () => void;
}

export default function ParentUnit({ roomCode, wsUrl, onExit }: ParentUnitProps) {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("connecting");
  const [partnerConnected, setPartnerConnected] = useState<boolean>(false);
  const [babyState, setBabyState] = useState<BabyState>({
    volume: 0,
    isCrying: false,
    online: false,
  });

  // Settings & Toggles
  const [liveAudioEnabled, setLiveAudioEnabled] = useState<boolean>(true); // listen to constant white noise or stay silent
  const [notificationPermission, setNotificationPermission] = useState<NotificationPermission>("default");
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [isAlarmPlaying, setIsAlarmPlaying] = useState<boolean>(false);

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

  // Refs for Web Audio API & scheduling
  const socketRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const nextPlayTimeRef = useRef<number>(0);
  const alarmIntervalRef = useRef<any>(null);

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

  // Check and Request Notification Permission
  useEffect(() => {
    if ("Notification" in window) {
      setNotificationPermission(Notification.permission);
    }
  }, []);

  const requestNotificationPermission = async () => {
    if ("Notification" in window) {
      const permission = await Notification.requestPermission();
      setNotificationPermission(permission);
      if (permission === "granted") {
        addLog("info", "Anlık bildirim izinleri onaylandı.");
      } else {
        addLog("warning", "Bildirim izinleri reddedildi. Arka planda uyarı gönderilemeyecek.");
      }
    }
  };

  // Web Audio Context initializer (must be triggered by user gesture)
  const initAudioContext = () => {
    if (!audioContextRef.current) {
      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      audioContextRef.current = new AudioContextClass();
      nextPlayTimeRef.current = audioContextRef.current.currentTime;
      addLog("info", "Ses oynatıcı motoru başlatıldı.");
    }
    if (audioContextRef.current.state === "suspended") {
      audioContextRef.current.resume();
    }
  };

  // WebSocket Setup
  useEffect(() => {
    addLog("info", `Odaya bağlanılıyor: ${roomCode}`);
    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      setConnectionStatus("connected");
      addLog("info", "İzleme sunucusuna bağlanıldı.");
      // Join as parent
      ws.send(JSON.stringify({ type: "join", room: roomCode, role: "ebeveyn" }));
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);

        switch (data.type) {
          case "partner-status": {
            if (data.role === "bebek") {
              if (data.event === "connected") {
                setPartnerConnected(true);
                setBabyState((prev) => ({ ...prev, online: true }));
                addLog("info", "Bebek ünitesi çevrimiçi oldu! İzleme başladı.");
              } else {
                setPartnerConnected(false);
                setBabyState((prev) => ({ ...prev, online: false, volume: 0, isCrying: false }));
                addLog("error", "Kritik Uyarı: Bebek ünitesinin bağlantısı koptu!");
                triggerSystemNotification("Kritik: Bağlantı Koptu", "Bebek ünitesi çevrimdışı oldu!");
                startAlarmSequence(); // Sound alarm immediately if baby unit goes offline unexpectedly
              }
            }
            break;
          }

          case "audio-chunk": {
            if (liveAudioEnabled) {
              // Dynamically initialize context if not already done
              if (audioContextRef.current) {
                const floatArray = base64ToFloat32(data.audio);
                playAudioChunk(floatArray);
              }
            }
            break;
          }

          case "cry-detected": {
            setBabyState((prev) => ({
              ...prev,
              isCrying: true,
              lastCryTime: new Date().toLocaleTimeString("tr-TR"),
            }));
            addLog("alert", `UYARI: Bebek ağlıyor! Şiddet: %${data.level}`);
            triggerSystemNotification("Bebek Ağlıyor!", "Bebek ünitesinden ağlama sesi algılandı!");
            startAlarmSequence();
            break;
          }

          case "status-update": {
            setBabyState((prev) => ({
              ...prev,
              volume: data.volume,
              battery: data.battery,
              online: true,
              // auto clear crying state if volume drops back to zero on baby side
              isCrying: data.volume < 10 ? false : prev.isCrying,
            }));
            break;
          }

          case "pong":
            break;

          default:
            break;
        }
      } catch (err) {
        console.error("Message handling error:", err);
      }
    };

    ws.onclose = () => {
      setConnectionStatus("disconnected");
      setPartnerConnected(false);
      setBabyState((prev) => ({ ...prev, online: false }));
      addLog("error", "Bağlantı kesildi.");
    };

    ws.onerror = () => {
      setConnectionStatus("disconnected");
      addLog("error", "Bağlantı hatası.");
    };

    const pingInterval = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "ping" }));
      }
    }, 10000);

    return () => {
      clearInterval(pingInterval);
      ws.close();
      stopAlarmSequence();
    };
  }, [roomCode, wsUrl, liveAudioEnabled]);

  // Decode Float32 array from base64
  const base64ToFloat32 = (base64: string): Float32Array => {
    const binary = window.atob(base64);
    const len = binary.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new Float32Array(bytes.buffer);
  };

  // Play audio chunk with sequence scheduling to avoid network jitter and cracks
  const playAudioChunk = (floatArray: Float32Array) => {
    if (!audioContextRef.current) return;
    const ctx = audioContextRef.current;
    if (ctx.state === "suspended") return;

    const sampleRate = 11025; // downsampled rate on baby unit
    const buffer = ctx.createBuffer(1, floatArray.length, sampleRate);
    buffer.getChannelData(0).set(floatArray);

    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);

    let playTime = nextPlayTimeRef.current;
    const currentTime = ctx.currentTime;

    if (playTime < currentTime) {
      playTime = currentTime + 0.05; // 50ms buffer
    }

    source.start(playTime);
    nextPlayTimeRef.current = playTime + buffer.duration;
  };

  // HTML5 Push notification
  const triggerSystemNotification = (title: string, body: string) => {
    if ("Notification" in window && Notification.permission === "granted") {
      new Notification(title, {
        body,
        tag: "bebek-telsizi-alarm",
        requireInteraction: true,
      });
    }
  };

  // Play synthetic organic alarm using Web Audio API
  const playOrganicAlarmTone = () => {
    if (!audioContextRef.current) return;
    const ctx = audioContextRef.current;
    if (ctx.state === "suspended") return;

    const osc = ctx.createOscillator();
    const gainNode = ctx.createGain();

    osc.type = "sine";
    // Alternate frequencies for classic siren sweep (pitch goes up and down)
    const baseFreq = isAlarmPlaying ? 987.77 : 880; // B5 and A5 notes alternating
    osc.frequency.setValueAtTime(baseFreq, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(baseFreq * 1.5, ctx.currentTime + 0.45);

    gainNode.gain.setValueAtTime(0.01, ctx.currentTime);
    gainNode.gain.linearRampToValueAtTime(0.4, ctx.currentTime + 0.05);
    gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5);

    osc.connect(gainNode);
    gainNode.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + 0.5);
  };

  // Play alarm sound sequence
  const startAlarmSequence = () => {
    initAudioContext();
    setIsAlarmPlaying(true);
    if (alarmIntervalRef.current) clearInterval(alarmIntervalRef.current);
    
    // Play sound immediately, then loop
    playOrganicAlarmTone();
    alarmIntervalRef.current = setInterval(() => {
      playOrganicAlarmTone();
    }, 600);
  };

  // Mute active alarm sound loop
  const stopAlarmSequence = () => {
    setIsAlarmPlaying(false);
    if (alarmIntervalRef.current) {
      clearInterval(alarmIntervalRef.current);
      alarmIntervalRef.current = null;
    }
  };

  // Test Alarm system manually
  const testAlarm = () => {
    initAudioContext();
    addLog("info", "Alarm sistemi test ediliyor...");
    startAlarmSequence();
    setTimeout(() => {
      stopAlarmSequence();
    }, 3000);
  };

  return (
    <div className="w-full max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
      
      {/* Active Panel View */}
      <div className="md:col-span-2 bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6 flex flex-col justify-between">
        
        {/* Header and status */}
        <div className="flex justify-between items-center pb-4 border-b border-zinc-100 dark:border-zinc-800">
          <div>
            <span className="text-[10px] uppercase tracking-wider font-semibold text-zinc-400 dark:text-zinc-500 block">
              Aktif İstasyon
            </span>
            <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Shield className="w-5 h-5 text-teal-500" /> Ebeveyn Ünitesi
            </h3>
          </div>
          <div className="flex items-center gap-3">
            {/* Connection status */}
            <div className="flex items-center gap-1.5 text-xs">
              {connectionStatus === "connected" ? (
                <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
                  <Wifi className="w-4 h-4" /> Bağlı
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
          </div>
        </div>

        {/* Pairing info */}
        <div className="mt-4 flex flex-wrap gap-2 items-center justify-between p-3 bg-zinc-50 dark:bg-zinc-950/60 rounded-xl">
          <div className="text-xs text-zinc-600 dark:text-zinc-400">
            Oda Kodu: <span className="font-mono font-bold text-zinc-900 dark:text-zinc-100 tracking-wider text-sm bg-zinc-200 dark:bg-zinc-800 px-2.5 py-1 rounded">{roomCode}</span>
          </div>
          <div className="text-xs flex items-center gap-1 text-zinc-500 dark:text-zinc-400">
            <span className={`w-2.5 h-2.5 rounded-full ${partnerConnected ? "bg-emerald-500 animate-ping" : "bg-rose-500 animate-pulse"}`}></span>
            {partnerConnected ? "Bebek Ünitesi Çevrimiçi" : "Bebek ünitesi bekleniyor!"}
          </div>
        </div>

        {/* Big Alert Monitor State */}
        <div className="my-6">
          <div 
            onClick={initAudioContext}
            className={`relative rounded-2xl p-6 sm:p-8 flex flex-col items-center justify-center text-center border-2 border-dashed transition-all cursor-pointer ${
              !partnerConnected
                ? "bg-zinc-50 dark:bg-zinc-950/20 border-zinc-200 dark:border-zinc-800"
                : babyState.isCrying || isAlarmPlaying
                ? "bg-rose-50 dark:bg-rose-950/20 border-rose-500/80 animate-pulse text-rose-800 dark:text-rose-400"
                : "bg-emerald-50/50 dark:bg-emerald-950/10 border-emerald-500/40 text-emerald-800 dark:text-emerald-400"
            }`}
          >
            {/* Icon */}
            <div className={`p-4 rounded-full mb-4 ${
              !partnerConnected
                ? "bg-zinc-100 dark:bg-zinc-800 text-zinc-400"
                : babyState.isCrying || isAlarmPlaying
                ? "bg-rose-100 dark:bg-rose-900/30 text-rose-600"
                : "bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600"
            }`}>
              {babyState.isCrying || isAlarmPlaying ? (
                <AlertOctagon className="w-12 h-12 animate-bounce" />
              ) : (
                <Shield className="w-12 h-12" />
              )}
            </div>

            <h4 className="text-xl font-bold tracking-tight">
              {!partnerConnected
                ? "Bebek Ünitesi Bağlantısı Bekleniyor"
                : babyState.isCrying || isAlarmPlaying
                ? "DİKKAT: BEBEK AĞLIYOR!"
                : "Bebek Güvende & Sessiz"}
            </h4>
            
            <p className="text-xs text-zinc-500 dark:text-zinc-400 max-w-sm mt-1.5 leading-relaxed">
              {!partnerConnected
                ? "Bebek odasındaki cihazda aynı oda kodunu girerek izlemeyi aktif hale getirin."
                : babyState.isCrying || isAlarmPlaying
                ? "Ses seviyesi eşik sınırını aştı! Lütfen hemen kontrol edin."
                : "Bebek odasından sakin ve sessiz ses verileri alınıyor."}
            </p>

            {/* Tap to authorize sound overlay for mobile browsers */}
            {audioContextRef.current === null && (
              <div className="absolute inset-0 bg-indigo-600/90 text-white rounded-2xl flex flex-col items-center justify-center p-4">
                <Play className="w-10 h-10 mb-2 animate-pulse" />
                <span className="font-bold text-sm">Ses Alıcısını Etkinleştir</span>
                <span className="text-[11px] opacity-80 mt-1 max-w-xs">Tarayıcı kısıtlamaları nedeniyle sesleri duymak için buraya bir kez dokunun.</span>
              </div>
            )}
          </div>
        </div>

        {/* Live Gauges */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 my-2">
          {/* Volume Meter */}
          <div className="p-4 bg-zinc-50 dark:bg-zinc-950/40 rounded-xl border border-zinc-100 dark:border-zinc-800/60">
            <div className="flex justify-between items-center mb-1">
              <span className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wide">Bebek Odası Ses Seviyesi</span>
              <span className="text-xs font-bold text-zinc-700 dark:text-zinc-300">% {babyState.volume}</span>
            </div>
            <div className="w-full bg-zinc-200 dark:bg-zinc-800 h-2.5 rounded-full overflow-hidden">
              <div 
                className={`h-full transition-all duration-100 ${babyState.volume > 25 ? "bg-rose-500 animate-pulse" : "bg-teal-500"}`}
                style={{ width: `${Math.min(100, babyState.volume)}%` }}
              />
            </div>
          </div>

          {/* Baby Battery level */}
          <div className="p-4 bg-zinc-50 dark:bg-zinc-950/40 rounded-xl border border-zinc-100 dark:border-zinc-800/60">
            <div className="flex justify-between items-center mb-1">
              <span className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wide">Bebek Ünitesi Pili</span>
              <span className="text-xs font-bold text-zinc-700 dark:text-zinc-300">
                {babyState.battery !== undefined ? `% ${babyState.battery}` : "Ölçülüyor..."}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <Battery className={`w-5 h-5 ${babyState.battery !== undefined && babyState.battery < 20 ? "text-rose-500 animate-pulse" : "text-emerald-500"}`} />
              <div className="flex-1 bg-zinc-200 dark:bg-zinc-800 h-2.5 rounded-full overflow-hidden">
                <div 
                  className={`h-full transition-all duration-500 ${babyState.battery !== undefined && babyState.battery < 20 ? "bg-rose-500" : "bg-emerald-500"}`}
                  style={{ width: `${babyState.battery !== undefined ? babyState.battery : 0}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Action Panel Buttons */}
        <div className="pt-4 border-t border-zinc-100 dark:border-zinc-800 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={onExit}
            className="px-4 py-3 border border-zinc-200 dark:border-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-950 rounded-xl text-sm font-medium transition-all"
          >
            Odadan Ayrıl
          </button>

          {isAlarmPlaying && (
            <button
              type="button"
              id="dismiss-alarm-btn"
              onClick={stopAlarmSequence}
              className="px-5 py-3 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-sm font-bold shadow-md hover:shadow-lg transition-all flex items-center gap-1.5 cursor-pointer"
            >
              <BellOff className="w-4 h-4 animate-bounce" /> Alarmı Sustur
            </button>
          )}

          <button
            type="button"
            id="test-alarm-btn"
            onClick={testAlarm}
            className="px-4 py-3 bg-zinc-100 hover:bg-zinc-200 dark:bg-zinc-800 dark:hover:bg-zinc-700 text-zinc-800 dark:text-zinc-200 rounded-xl text-sm font-medium transition-all cursor-pointer"
          >
            Siren Testi Yap
          </button>
        </div>
      </div>

      {/* Control console side panel */}
      <div className="space-y-6">
        
        {/* Device Controls & Permissions */}
        <div className="bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6 space-y-4">
          <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-200 pb-3 border-b border-zinc-100 dark:border-zinc-800">
            Tercihler & İzinler
          </h4>

          {/* Constant Sound Stream toggle */}
          <div className="flex items-center justify-between p-3 bg-zinc-50 dark:bg-zinc-950/60 rounded-xl border border-zinc-100 dark:border-zinc-800/80">
            <div>
              <span className="text-xs text-zinc-700 dark:text-zinc-300 font-bold block">Canlı Ses Dinleme</span>
              <span className="text-[10px] text-zinc-400 dark:text-zinc-500 block mt-0.5">Sürekli arka plan sesini ilet</span>
            </div>
            <button
              type="button"
              id="toggle-live-audio-btn"
              onClick={() => {
                setLiveAudioEnabled(!liveAudioEnabled);
                initAudioContext();
              }}
              className={`w-11 h-6 rounded-full transition-colors relative cursor-pointer ${liveAudioEnabled ? "bg-indigo-600" : "bg-zinc-300 dark:bg-zinc-700"}`}
            >
              <span className={`absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-transform ${liveAudioEnabled ? "translate-x-5" : "translate-x-0"}`} />
            </button>
          </div>

          {/* Request Notification Permission block */}
          <div className="p-3 bg-zinc-50 dark:bg-zinc-950/60 rounded-xl border border-zinc-100 dark:border-zinc-800/80 flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-xs text-zinc-700 dark:text-zinc-300 font-bold block">Arka Plan Bildirimleri</span>
                <span className="text-[10px] text-zinc-400 dark:text-zinc-500 block mt-0.5">Ekran kapalıyken uyarı al</span>
              </div>
              <div>
                {notificationPermission === "granted" ? (
                  <Bell className="w-5 h-5 text-emerald-500" />
                ) : (
                  <BellOff className="w-5 h-5 text-zinc-400" />
                )}
              </div>
            </div>
            {notificationPermission !== "granted" && (
              <button
                type="button"
                id="request-permission-btn"
                onClick={requestNotificationPermission}
                className="w-full py-1.5 bg-indigo-50 hover:bg-indigo-100 dark:bg-indigo-950/30 dark:hover:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 rounded-lg text-xs font-semibold transition-all cursor-pointer"
              >
                Bildirim İznini Etkinleştir
              </button>
            )}
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
              <span className="text-slate-900 dark:text-zinc-200 font-mono font-bold text-xs">{babyState.battery !== undefined ? `%${babyState.battery}` : "%88"}</span>
            </div>
            <div className="flex justify-between items-center py-1.5">
              <span className="text-slate-500 dark:text-zinc-400 font-medium text-xs">Çalışma Süresi (Uptime)</span>
              <span className="text-slate-900 dark:text-zinc-200 font-mono font-bold text-xs">{uptime}</span>
            </div>
          </div>
        </div>

        {/* History Alarm Logs */}
        <div className="bg-white dark:bg-zinc-900 rounded-2xl shadow-lg border border-zinc-200/50 dark:border-zinc-800/50 p-6 flex flex-col h-72">
          <h4 className="text-sm font-bold text-zinc-800 dark:text-zinc-200 pb-3 border-b border-zinc-100 dark:border-zinc-800 mb-3 flex items-center gap-1.5">
            <History className="w-4 h-4 text-indigo-500" /> Alarm Geçmişi
          </h4>
          <div className="flex-1 overflow-y-auto space-y-2 pr-1 scrollbar-thin scrollbar-thumb-zinc-200 dark:scrollbar-thumb-zinc-800">
            {logs.length === 0 ? (
              <div className="text-center text-zinc-400 text-xs py-10">Henüz alarm kaydı bulunmuyor.</div>
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
