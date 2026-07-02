import React, { useState, useEffect } from "react";
import { Baby, Info, ShieldAlert, Cpu, Wifi } from "lucide-react";
import RoleSelection from "./components/RoleSelection";
import BabyUnit from "./components/BabyUnit";
import ParentUnit from "./components/ParentUnit";
import { DeviceRole } from "./types";

export default function App() {
  const [role, setRole] = useState<DeviceRole | null>(null);
  const [roomCode, setRoomCode] = useState<string>("");
  const [currentTime, setCurrentTime] = useState<string>("");

  // Update clock on screen
  useEffect(() => {
    const updateClock = () => {
      const now = new Date();
      setCurrentTime(now.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit", second: "2-digit" }));
    };
    updateClock();
    const interval = setInterval(updateClock, 1000);
    return () => clearInterval(interval);
  }, []);

  // Compute WebSocket URL dynamically based on browser location
  const getWebSocketUrl = () => {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}`;
  };

  const wsUrl = getWebSocketUrl();

  return (
    <div className="min-h-screen bg-slate-100 dark:bg-zinc-950 text-slate-800 dark:text-zinc-100 flex flex-col font-sans overflow-hidden selection:bg-indigo-500 selection:text-white transition-colors duration-300">
      {/* Decorative background subtle glow */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-indigo-500/5 via-transparent to-transparent pointer-events-none" />

      {/* Main Sleek Navigation Header */}
      <nav className="h-20 bg-white dark:bg-zinc-900 border-b border-slate-200 dark:border-zinc-800 px-6 sm:px-8 flex items-center justify-between shadow-sm flex-shrink-0 z-10">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-indigo-600 rounded-xl flex items-center justify-center text-white shadow-md shadow-indigo-600/20">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"></path>
            </svg>
          </div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900 dark:text-zinc-50">
            CryGuard <span className="text-indigo-600 font-medium text-base sm:text-lg">Monitor</span>
          </h1>
        </div>

        {/* Network & Uptime Status */}
        <div className="flex items-center gap-4 sm:gap-6">
          <div className="flex flex-col items-end text-right">
            <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">Yerel Ağ</span>
            <span className="text-xs sm:text-sm font-medium flex items-center gap-1.5 text-slate-700 dark:text-zinc-300">
              <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></span>
              {roomCode ? `Oda: ${roomCode}` : "Wi-Fi Aktif"}
            </span>
          </div>
          <div className="h-8 w-px bg-slate-200 dark:bg-zinc-800"></div>
          
          <div className="px-3 py-1 bg-slate-950 dark:bg-zinc-850 text-white rounded-lg text-xs font-mono font-bold">
            {currentTime || "00:00:00"}
          </div>
        </div>
      </nav>

      {/* Main Content Stage */}
      <main className="flex-grow p-4 sm:p-8 flex flex-col justify-center overflow-auto relative z-10">
        {!role ? (
          <RoleSelection onSelect={(selectedRole, code) => {
            setRole(selectedRole);
            setRoomCode(code);
          }} />
        ) : role === "bebek" ? (
          <BabyUnit roomCode={roomCode} wsUrl={wsUrl} onExit={() => setRole(null)} />
        ) : (
          <ParentUnit roomCode={roomCode} wsUrl={wsUrl} onExit={() => setRole(null)} />
        )}
      </main>

      {/* Micro-Disclaimer Footer */}
      <footer className="border-t border-slate-200 dark:border-zinc-800/60 bg-white/50 dark:bg-zinc-900/10 backdrop-blur-sm py-3.5 px-6 sm:px-8 text-center text-[11px] text-slate-400 dark:text-zinc-500 flex flex-col sm:flex-row justify-between items-center gap-2 z-10 flex-shrink-0">
        <p>© {new Date().getFullYear()} CryGuard Bebek Telsizi (Flutter & Python Hybrid APK).</p>
        <div className="flex items-center gap-1.5">
          <Info className="w-3.5 h-3.5 text-slate-400" />
          <span>Bu sistem akıllı ses eşik tespiti içerir ve çocuk güvenliği için yardımcı bir araçtır.</span>
        </div>
      </footer>
    </div>
  );
}

