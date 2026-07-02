import React, { useState } from "react";
import { Baby, Shield, Wifi, KeyRound, HelpCircle, Cpu, FileText } from "lucide-react";
import { DeviceRole } from "../types";

interface RoleSelectionProps {
  onSelect: (role: DeviceRole, roomCode: string) => void;
}

export default function RoleSelection({ onSelect }: RoleSelectionProps) {
  const [role, setRole] = useState<DeviceRole | null>(null);
  const [roomCode, setRoomCode] = useState<string>("");
  const [error, setError] = useState<string>("");
  const [showGuide, setShowGuide] = useState<boolean>(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!role) {
      setError("Lütfen bir cihaz rolü seçin.");
      return;
    }
    const cleanRoom = roomCode.trim().toUpperCase();
    if (!cleanRoom) {
      setError("Lütfen bir oda kodu girin.");
      return;
    }
    if (cleanRoom.length < 3) {
      setError("Oda kodu en az 3 karakterden oluşmalıdır.");
      return;
    }
    setError("");
    onSelect(role, cleanRoom);
  };

  return (
    <div className="w-full max-w-xl mx-auto bg-white dark:bg-zinc-900 rounded-3xl shadow-xl border border-slate-200/80 dark:border-zinc-800/80 overflow-hidden p-6 sm:p-10">
      <div className="text-center mb-8">
        <div className="inline-flex p-3 bg-indigo-50 dark:bg-indigo-950/40 rounded-2xl text-indigo-600 dark:text-indigo-400 mb-4 shadow-sm">
          <Baby className="w-8 h-8" />
        </div>
        <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 dark:text-zinc-50">
          CryGuard <span className="text-indigo-600 font-medium">Kurulum</span>
        </h2>
        <p className="text-sm text-slate-500 dark:text-zinc-400 mt-2 max-w-md mx-auto">
          Arka planda çalışan yüksek duyarlılıklı ses iletim ve ağlama tespit modüllerini başlatmak için bir rol seçin.
        </p>

        {/* Integration Spec Badges directly from the Sleek Interface specs */}
        <div className="flex items-center justify-center gap-2 mt-4 flex-wrap">
          <span className="px-2.5 py-1 bg-indigo-50 dark:bg-indigo-950/60 text-indigo-700 dark:text-indigo-400 text-xs font-bold rounded-lg border border-indigo-100 dark:border-indigo-900/40 flex items-center gap-1">
            <Cpu className="w-3.5 h-3.5" /> AUDIO ENGINE: PYTHON 3.11
          </span>
          <span className="px-2.5 py-1 bg-blue-50 dark:bg-blue-950/60 text-blue-700 dark:text-blue-400 text-xs font-bold rounded-lg border border-blue-100 dark:border-blue-900/40 flex items-center gap-1">
            <FileText className="w-3.5 h-3.5" /> BUILD: FLUTTER & DART APK
          </span>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Role Selection Cards */}
        <div className="space-y-3">
          <label className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-widest block">
            CİHAZ İSTASYONU TÜRÜ
          </label>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <button
              type="button"
              id="role-bebek-btn"
              onClick={() => {
                setRole("bebek");
                setError("");
              }}
              className={`flex flex-col items-center justify-center p-6 rounded-2xl border text-center transition-all cursor-pointer ${
                role === "bebek"
                  ? "border-indigo-500 bg-indigo-50/20 dark:bg-indigo-950/30 text-indigo-600 dark:text-indigo-400 shadow-md ring-2 ring-indigo-500/20"
                  : "border-slate-200 dark:border-zinc-800 hover:border-slate-300 dark:hover:border-zinc-750 text-slate-600 dark:text-zinc-400"
              }`}
            >
              <div className="p-3 bg-amber-50 dark:bg-amber-950/30 text-amber-500 dark:text-amber-400 rounded-xl mb-3 shadow-inner">
                <Baby className="w-8 h-8" />
              </div>
              <span className="font-bold text-sm">Bebek İstasyonu</span>
              <span className="text-[11px] text-slate-400 dark:text-zinc-500 mt-1.5 leading-relaxed">
                Ses verilerini kaydeder, anlık gürültü analizlerini yapar ve yayını başlatır
              </span>
            </button>

            <button
              type="button"
              id="role-ebeveyn-btn"
              onClick={() => {
                setRole("ebeveyn");
                setError("");
              }}
              className={`flex flex-col items-center justify-center p-6 rounded-2xl border text-center transition-all cursor-pointer ${
                role === "ebeveyn"
                  ? "border-indigo-500 bg-indigo-50/20 dark:bg-indigo-950/30 text-indigo-600 dark:text-indigo-400 shadow-md ring-2 ring-indigo-500/20"
                  : "border-slate-200 dark:border-zinc-800 hover:border-slate-300 dark:hover:border-zinc-750 text-slate-600 dark:text-zinc-400"
              }`}
            >
              <div className="p-3 bg-emerald-50 dark:bg-emerald-950/30 text-emerald-500 dark:text-emerald-400 rounded-xl mb-3 shadow-inner">
                <Shield className="w-8 h-8" />
              </div>
              <span className="font-bold text-sm">Ebeveyn Alıcısı</span>
              <span className="text-[11px] text-slate-400 dark:text-zinc-500 mt-1.5 leading-relaxed">
                Canlı ses akışını dinler, bebek ağladığında siren çalar ve bildirim gönderir
              </span>
            </button>
          </div>
        </div>

        {/* Room Code Input */}
        <div className="space-y-2">
          <label 
            htmlFor="room-code-input"
            className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-widest block"
          >
            ODA EŞLEŞTİRME ANAHTARI (KODU)
          </label>
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 dark:text-zinc-500">
              <KeyRound className="w-5 h-5" />
            </div>
            <input
              id="room-code-input"
              type="text"
              value={roomCode}
              onChange={(e) => {
                setRoomCode(e.target.value);
                setError("");
              }}
              placeholder="Örn: BEBEK100"
              className="w-full pl-11 pr-4 py-3 rounded-2xl border border-slate-200 dark:border-zinc-800 bg-slate-50 dark:bg-zinc-950 text-slate-900 dark:text-zinc-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-mono uppercase tracking-wider text-center text-sm font-bold"
              maxLength={12}
            />
          </div>
          <p className="text-[11px] text-slate-400 dark:text-zinc-500 mt-1 leading-normal">
            Cihazların birbirini bulması için her iki telefonda da <b>aynı oda kodunu</b> girin.
          </p>
        </div>

        {error && (
          <div className="p-3 bg-rose-50 dark:bg-rose-950/20 text-rose-600 dark:text-rose-400 rounded-xl text-xs font-semibold text-center border border-rose-100 dark:border-rose-900/30">
            {error}
          </div>
        )}

        {/* Action Button */}
        <button
          type="submit"
          id="join-stream-btn"
          className="w-full py-4 px-4 bg-slate-900 hover:bg-slate-800 dark:bg-zinc-100 dark:hover:bg-zinc-200 text-white dark:text-zinc-950 rounded-2xl font-bold shadow-md hover:shadow-lg transition-all transform active:scale-[0.99] cursor-pointer text-sm tracking-wide"
        >
          {role === "bebek" ? "BEBEK İSTASYONUNU BAŞLAT" : role === "ebeveyn" ? "ALICI İSTASYONUNU BAŞLAT" : "İSTASYONU BAŞLAT"}
        </button>
      </form>

      {/* Inline User Guide Toggle */}
      <div className="mt-8 pt-6 border-t border-slate-100 dark:border-zinc-800/80 flex flex-col items-center">
        <button
          type="button"
          onClick={() => setShowGuide(!showGuide)}
          className="inline-flex items-center text-xs text-indigo-500 hover:text-indigo-600 transition-colors font-bold gap-1 cursor-pointer"
        >
          <HelpCircle className="w-4 h-4" />
          Nasıl Çalışır? (Hızlı Kurulum Klavuzu)
        </button>

        {showGuide && (
          <div className="mt-4 p-5 bg-slate-50 dark:bg-zinc-950/50 rounded-2xl text-xs text-slate-600 dark:text-zinc-400 space-y-3 text-left border border-slate-100 dark:border-zinc-800">
            <p className="font-bold text-slate-800 dark:text-zinc-300">Kurulum ve APK Kullanım Rehberi:</p>
            <ol className="list-decimal list-inside space-y-2 leading-relaxed">
              <li>Uygulamayı telefonunuza yüklediğinizde mikrofon iznine mutlaka izin verin.</li>
              <li>Aynı oda koduna sahip iki cihazdan biri verici (<b className="text-amber-600">Bebek</b>) diğeri ise alıcı (<b className="text-teal-600">Ebeveyn</b>) olarak çalışır.</li>
              <li>Sıkıştırma ve gürültü filtreleme modülleri sayesinde düşük internet hızlarında ve arka planda dahi kesintisiz çalışacak şekilde optimize edilmiştir.</li>
              <li>Ağlama tespiti için ortamdaki gürültü miktarına göre <b>"Hassasiyet"</b> ayarlarını değiştirebilirsiniz.</li>
            </ol>
          </div>
        )}
      </div>
    </div>
  );
}
