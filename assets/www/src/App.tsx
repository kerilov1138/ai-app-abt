import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Search, 
  Youtube, 
  Loader2, 
  Share2, 
  Copy, 
  ExternalLink,
  ChevronRight,
  ChevronDown,
  ListRestart,
  Type,
  Monitor
} from 'lucide-react';
import { cn } from './lib/utils';

export interface SummaryResult {
  shortSummary: string;
  longSummary: string;
}

interface TranscriptItem {
  text: string;
  start: number;
  duration: number;
}

export default function App() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<{message: string, suggestion?: string} | null>(null);
  const [transcript, setTranscript] = useState<TranscriptItem[]>([]);
  const [summary, setSummary] = useState<SummaryResult | null>(null);
  const [keyword, setKeyword] = useState('');
  const [summaryType, setSummaryType] = useState<'short' | 'long'>('short');
  const [activeTab, setActiveTab] = useState<'keywords' | 'chat'>('keywords');
  const [chatMessages, setChatMessages] = useState<{role: 'user' | 'model', text: string}[]>([]);
  const [userMessage, setUserMessage] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [videoId, setVideoId] = useState<string | null>(null);
  const [audioFallback, setAudioFallback] = useState(false);
  const [useDeepAnalysis, setUseDeepAnalysis] = useState(true);
  const [apiKey, setApiKey] = useState(() => localStorage.getItem('GEMINI_API_KEY') || '');

  const saveApiKey = () => {
    localStorage.setItem('GEMINI_API_KEY', apiKey);
    alert('API Key başarıyla kaydedildi.');
  };

  const analyzeVideo = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!url) return;

    setLoading(true);
    setError(null);
    setTranscript([]);
    setSummary(null);
    setChatMessages([]);
    setVideoId(null);
    setAudioFallback(false);

    try {
      const response = await fetch(`/api/transcript?url=${encodeURIComponent(url)}`);
      
      const contentType = response.headers.get("content-type");
      if (!contentType || !contentType.includes("application/json")) {
        const text = await response.text();
        console.error("Non-JSON response from transcript API:", text);
        throw new Error("Sunucudan geçersiz bir yanıt alındı. Lütfen videonun linkini kontrol edip tekrar deneyin.");
      }

      const data = await response.json();

      if (data.error) {
        setError({ message: data.error, suggestion: data.suggestion });
        setLoading(false);
        return;
      }

      setVideoId(data.videoId);
      setAudioFallback(data.audioFallback);
      setTranscript(data.transcript || []);
      
      const fullText = (data.transcript || []).map((t: TranscriptItem) => t.text).join(' ');
      
      const summaryResponse = await fetch('/api/summarize', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'x-gemini-key': apiKey
        },
        body: JSON.stringify({ 
          transcript: fullText, 
          videoId: data.videoId, 
          audioFallback: data.audioFallback || useDeepAnalysis,
          deepAnalysis: useDeepAnalysis
        })
      });

      const summaryContentType = summaryResponse.headers.get("content-type");
      if (!summaryContentType || !summaryContentType.includes("application/json")) {
        const text = await summaryResponse.text();
        console.error("Non-JSON response from summarize API:", text);
        throw new Error("Özet oluşturulurken sunucudan geçersiz bir yanıt alındı.");
      }

      const summaryResult = await summaryResponse.json();
      
      if (summaryResult.error) {
        throw { message: summaryResult.error, suggestion: summaryResult.suggestion };
      }

      setSummary(summaryResult);
    } catch (err: any) {
      if (err.message) {
        setError({ message: err.message, suggestion: err.suggestion });
      } else {
        setError({ message: err instanceof Error ? err.message : 'Bir hata oluştu.' });
      }
    } finally {
      setLoading(false);
    }
  };

  const sendChatMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userMessage.trim() || !summary || chatLoading) return;

    const newMessage: {role: 'user', text: string} = { role: 'user', text: userMessage };
    setChatMessages(prev => [...prev, newMessage]);
    setUserMessage('');
    setChatLoading(true);

    try {
      const fullText = (transcript || []).map((t: TranscriptItem) => t.text).join(' ');
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'x-gemini-key': apiKey
        },
        body: JSON.stringify({
          message: userMessage,
          history: chatMessages,
          transcript: fullText,
          videoId,
          audioFallback: audioFallback || useDeepAnalysis
        })
      });

      const contentType = response.headers.get("content-type");
      if (!contentType || !contentType.includes("application/json")) {
        const text = await response.text();
        console.error("Non-JSON response from chat API:", text);
        throw new Error("Sunucudan geçersiz bir yanıt alındı.");
      }

      const data = await response.json();
      if (data.error) {
        throw { message: data.error, suggestion: data.suggestion };
      }
      setChatMessages(prev => [...prev, { role: 'model', text: data.text }]);
    } catch (err: any) {
      const msg = err.message || 'Hata: Soru cevaplanamadı.';
      setChatMessages(prev => [...prev, { role: 'model', text: msg }]);
    } finally {
      setChatLoading(false);
    }
  };

  const filteredSentences = useMemo(() => {
    if (!keyword || transcript.length === 0) return [];
    return transcript.filter(item => 
      item.text.toLowerCase().includes(keyword.toLowerCase())
    );
  }, [keyword, transcript]);

  const highlightText = (text: string, highlight: string) => {
    if (!highlight.trim()) return text;
    const escapedHighlight = highlight.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const parts = text.split(new RegExp(`(${escapedHighlight})`, 'gi'));
    return (
      <>
        {parts.map((part, i) => (
          part.toLowerCase() === highlight.toLowerCase() ? (
            <mark key={i} className="bg-orange-200 text-orange-950 px-0.5 rounded shadow-sm">
              {part}
            </mark>
          ) : (
            part
          )
        ))}
      </>
    );
  };

  const handleShare = async () => {
    if (!summary) return;
    const shareText = `Video Özeti:\n\n${summaryType === 'short' ? summary.shortSummary : summary.longSummary}\n\nVideo: ${url}`;
    
    if (navigator.share) {
      try {
        await navigator.share({
          title: 'Video Özet Paylaşımı',
          text: shareText,
          url: url
        });
      } catch (err) {
        console.error('Paylaşım hatası:', err);
      }
    } else {
      await navigator.clipboard.writeText(shareText);
      alert('Özet panoya kopyalandı!');
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans p-2 sm:p-4 lg:p-12 selection:bg-indigo-500/30 flex items-center justify-center">
      <div className="w-full max-w-6xl bg-[#1e293b] rounded-xl shadow-2xl border border-slate-700/50 overflow-hidden flex flex-col h-[95vh] sm:h-[90vh]">
        {/* Desktop Title Bar Styling */}
        <div className="h-10 bg-slate-800/80 border-b border-slate-700/50 flex items-center justify-between px-4 shrink-0 font-sans">
          <div className="flex gap-1.5 items-center">
            <div className="w-3 h-3 rounded-full bg-red-500/80 shadow-inner"></div>
            <div className="w-3 h-3 rounded-full bg-amber-500/80 shadow-inner"></div>
            <div className="w-3 h-3 rounded-full bg-green-500/80 shadow-inner"></div>
            <span className="ml-4 text-[10px] font-bold tracking-widest text-slate-400/80 uppercase">YouTube Deep Analyzer Desktop v2.5</span>
          </div>
          <div className="flex items-center gap-4">
             <div className="flex items-center gap-2">
               <input 
                 type="password" 
                 placeholder="Gemini API Key..."
                 className="text-[9px] bg-slate-900 border border-slate-700 rounded px-2 py-0.5 w-32 focus:outline-none focus:border-indigo-500 text-indigo-300 placeholder:text-slate-600"
                 value={apiKey}
                 onChange={(e) => setApiKey(e.target.value)}
               />
               <button 
                onClick={saveApiKey}
                className="text-[9px] bg-indigo-600 hover:bg-indigo-500 text-white px-1.5 py-0.5 rounded font-bold transition-colors"
               >
                SAVE
               </button>
             </div>
             <div className="w-4 h-4 text-slate-500"><Monitor size={14} /></div>
          </div>
        </div>

        <div className="flex-1 overflow-auto bg-slate-50 text-slate-800 flex flex-col min-h-0">
          {/* Original Header Content inside our "window" */}
          <header className="bg-white border-b border-slate-200 px-8 py-4 flex justify-between items-center shadow-sm z-30 shrink-0">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-red-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-red-200">
                <Youtube size={24} strokeWidth={2.5} />
              </div>
              <h1 className="text-xl font-bold tracking-tight text-slate-900">VideoÖzet Analiz</h1>
            </div>
            <div className="flex gap-4">
              <button 
                onClick={handleShare}
                disabled={!summary}
                className="flex items-center gap-2 px-4 py-2 border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed text-slate-600"
              >
                <Share2 size={18} />
                Paylaş
              </button>
              <button 
                onClick={() => window.location.reload()}
                className="px-4 py-2 bg-slate-900 text-white rounded-lg text-sm font-semibold shadow-lg shadow-slate-200 hover:bg-slate-800 transition-all"
              >
                Yeni Analiz
              </button>
            </div>
          </header>

          <main className="flex-1 grid grid-cols-12 gap-6 p-8 overflow-hidden min-h-0">
            {/* Left Column: Input and Controls */}
            <div className="col-span-12 lg:col-span-4 flex flex-col gap-6 overflow-y-auto pr-2 custom-scrollbar">
              <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
                <form onSubmit={analyzeVideo}>
                  <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">YouTube Video Linki</label>
                  <div className="relative">
                    <input 
                      type="text" 
                      placeholder="https://www.youtube.com/watch?v=..." 
                      className={cn(
                        "w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-red-500/20 focus:border-red-500 transition-all",
                        error && "border-red-200 bg-red-50"
                      )}
                      value={url}
                      onChange={(e) => setUrl(e.target.value)}
                    />
                  </div>
                  
                  <div className="mt-6">
                    <div className="flex items-center justify-between mb-3">
                      <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Mod & Analiz</label>
                    </div>
                    <div className="space-y-3">
                      <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-xl">
                        <button 
                          type="button"
                          onClick={() => setSummaryType('short')}
                          className={cn(
                            "py-2 text-xs font-semibold rounded-lg transition-all",
                            summaryType === 'short' ? "bg-white shadow-sm text-slate-900" : "text-slate-500 hover:text-slate-700"
                          )}
                        >
                          Kısa Özet
                        </button>
                        <button 
                          type="button"
                          onClick={() => setSummaryType('long')}
                          className={cn(
                            "py-2 text-xs font-semibold rounded-lg transition-all",
                            summaryType === 'long' ? "bg-white shadow-sm text-slate-900" : "text-slate-500 hover:text-slate-700"
                          )}
                        >
                          Detaylı Analiz
                        </button>
                      </div>
                      
                      <button
                        type="button"
                        onClick={() => setUseDeepAnalysis(!useDeepAnalysis)}
                        className={cn(
                          "w-full flex items-center justify-between p-3 rounded-xl border transition-all",
                          useDeepAnalysis 
                            ? "bg-red-50 border-red-200 text-red-700 shadow-inner" 
                            : "bg-white border-slate-200 text-slate-600 hover:border-slate-300"
                        )}
                      >
                        <div className="flex flex-col items-start text-left">
                          <span className="text-[10px] font-bold uppercase tracking-widest leading-none">Yapay Zeka Dinlesin (Önerilen)</span>
                          <span className="text-[9px] opacity-70 mt-1">Ses analizi ile %100 analiz (Altyazı bağımsız)</span>
                        </div>
                        <div className={cn(
                          "w-8 h-4 rounded-full relative transition-colors bg-slate-200 shrink-0",
                          useDeepAnalysis && "bg-red-500"
                        )}>
                          <div className={cn(
                            "absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm",
                            useDeepAnalysis && "translate-x-4"
                          )} />
                        </div>
                      </button>
                    </div>
                  </div>

                  <button 
                    type="submit"
                    disabled={loading}
                    className="w-full mt-6 bg-red-600 hover:bg-red-700 disabled:bg-slate-400 text-white font-bold py-3 rounded-xl transition-all shadow-md shadow-red-200 flex items-center justify-center gap-2"
                  >
                    {loading ? <Loader2 size={20} className="animate-spin" /> : <Search size={20} />}
                    {loading ? "Analiz Ediliyor..." : "Analiz Et ve Özetle"}
                  </button>
                </form>
              </div>

              {/* Video Metadata / Status Card */}
              <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex-1">
                <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">Video Bilgileri</label>
                {loading ? (
                  <div className="flex flex-col items-center justify-center h-32 space-y-3">
                    <div className="w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin" />
                    <p className="text-xs text-slate-400 animate-pulse">Veriler çekiliyor...</p>
                  </div>
                ) : summary ? (
                  <div className="space-y-4">
                    <div className="flex items-start gap-4">
                      <div className="w-24 h-16 bg-slate-200 rounded-lg shrink-0 overflow-hidden relative group">
                        <div className="absolute inset-0 bg-red-100 flex items-center justify-center">
                           <Youtube className="text-red-400 opacity-40" />
                        </div>
                        <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent"></div>
                        <span className="absolute bottom-1 right-1 text-[10px] text-white font-bold bg-black/40 px-1 rounded">HD</span>
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold leading-tight line-clamp-2">YouTube Video Analizi</h3>
                        <p className="text-xs text-slate-400 mt-1">Sistem tarafından işlendi</p>
                      </div>
                    </div>
                    <div className="space-y-3 pt-4 border-t border-slate-100">
                      <div className="flex justify-between text-xs">
                        <span className="text-slate-500">Transkript:</span>
                        <span className="text-green-600 font-medium">{audioFallback ? "Pasif (Ses Analizi Aktif)" : `Aktif (${transcript.length} Satır)`}</span>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span className="text-slate-500">Mod:</span>
                        <span className="text-slate-800 font-medium">Masaüstü/Desktop</span>
                      </div>
                    </div>
                  </div>
                ) : error ? (
                  <div className="p-4 bg-red-50 border border-red-100 rounded-xl text-red-600 text-xs">
                    <p className="font-bold mb-1">{error.message}</p>
                    {error.suggestion && <p className="opacity-80 italic">{error.suggestion}</p>}
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center h-32 opacity-30">
                    <Youtube size={32} className="text-slate-400 mb-2" />
                    <p className="text-xs text-slate-400">Analiz bekliyor</p>
                  </div>
                )}
              </div>
            </div>

            {/* Right Column: Results and Keyword Finder */}
            <div className="col-span-12 lg:col-span-8 flex flex-col gap-6 overflow-hidden">
              <div className="bg-white rounded-2xl shadow-sm border border-slate-100 flex flex-col h-[45%] overflow-hidden transition-all">
                <div className="px-6 py-4 border-b border-slate-50 flex justify-between items-center shrink-0">
                  <h2 className="font-bold text-slate-800">Video Özeti</h2>
                  <div className="flex items-center gap-2">
                    {audioFallback && <span className="text-[10px] bg-amber-50 text-amber-600 px-2 py-1 rounded-full font-bold uppercase tracking-tighter ring-1 ring-amber-200">Sesli Analiz</span>}
                    <span className="text-[10px] bg-red-50 text-red-600 px-2 py-1 rounded-full font-bold uppercase tracking-tighter">AI Çıktı</span>
                  </div>
                </div>
                <div className="p-6 overflow-y-auto text-slate-600 leading-relaxed text-sm scrollbar-thin scrollbar-thumb-slate-200">
                  <AnimatePresence mode="wait">
                    {summary ? (
                      <motion.div key={summaryType} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}>
                        <p className="whitespace-pre-wrap">{summaryType === 'short' ? summary.shortSummary : summary.longSummary}</p>
                      </motion.div>
                    ) : (
                      <div className="flex items-center justify-center h-full text-slate-400 italic">
                        {loading ? (
                          <div className="flex flex-col items-center gap-2">
                            <Loader2 size={24} className="animate-spin text-red-500" />
                            <p>{audioFallback ? "Ses verisi analiz ediliyor..." : "Özet oluşturuluyor..."}</p>
                          </div>
                        ) : "Analiz edildiğinde sonuç burada görünecek."}
                      </div>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              <div className="bg-slate-900 text-white rounded-2xl shadow-xl flex-1 flex flex-col overflow-hidden transition-all">
                <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between shrink-0">
                  <div className="flex gap-4">
                    <button onClick={() => setActiveTab('keywords')} className={cn("font-bold text-sm transition-colors relative pb-4 -mb-4", activeTab === 'keywords' ? "text-white" : "text-slate-500 hover:text-slate-300")}>
                      Kelime Analizi
                      {activeTab === 'keywords' && <motion.div layoutId="tab" className="absolute bottom-0 left-0 right-0 h-1 bg-red-500 rounded-full" />}
                    </button>
                    <button onClick={() => setActiveTab('chat')} className={cn("font-bold text-sm transition-colors relative pb-4 -mb-4", activeTab === 'chat' ? "text-white" : "text-slate-500 hover:text-slate-300")}>
                      Asistan
                      {activeTab === 'chat' && <motion.div layoutId="tab" className="absolute bottom-0 left-0 right-0 h-1 bg-red-500 rounded-full" />}
                    </button>
                  </div>
                </div>

                <div className="flex-1 overflow-hidden flex flex-col">
                  {activeTab === 'keywords' ? (
                    <div className="p-6 flex-1 overflow-y-auto custom-scrollbar">
                      <div className="sticky top-0 bg-slate-900 pb-4 mb-4 z-10">
                        <div className="flex items-center bg-slate-800 rounded-lg px-3 py-2 border border-slate-700">
                          <Search size={16} className="text-slate-500 mr-2" />
                          <input type="text" placeholder="Kelime ara..." className="bg-transparent border-none text-sm focus:outline-none w-full text-white placeholder:text-slate-500" value={keyword} onChange={(e) => setKeyword(e.target.value)} disabled={audioFallback} />
                        </div>
                      </div>
                      {audioFallback ? (
                        <div className="flex flex-col items-center justify-center h-48 text-slate-500 text-center px-8">
                           <Youtube size={32} className="mb-3 opacity-20" />
                           <p className="text-sm italic">Altyazı yok, ses asistanına sorun.</p>
                        </div>
                      ) : keyword && filteredSentences.length > 0 ? (
                        <div className="space-y-4">
                          {filteredSentences.map((item, idx) => (
                            <motion.div key={idx} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} className="p-4 bg-slate-800/50 rounded-xl border-l-4 border-red-500">
                              <p className="text-sm leading-relaxed text-slate-200">{highlightText(item.text, keyword)}</p>
                              <div className="mt-2 text-[10px] text-slate-400">Time: {Math.floor(item.start / 60)}:{(item.start % 60).toFixed(0).padStart(2, '0')}</div>
                            </motion.div>
                          ))}
                        </div>
                      ) : (
                        <div className="flex flex-col items-center justify-center h-full text-slate-600 text-sm">Arama yapmak için bir kelime girin.</div>
                      )}
                    </div>
                  ) : (
                    <div className="flex-1 flex flex-col overflow-hidden">
                      <div className="flex-1 p-6 overflow-y-auto space-y-4 scrollbar-thin scrollbar-thumb-slate-700">
                        {chatMessages.length === 0 ? (
                          <div className="flex flex-col items-center justify-center h-full text-slate-500 text-center px-8">
                            <p className="text-sm font-medium text-slate-300">Video hakkında her şeyi sorun!</p>
                          </div>
                        ) : (
                          chatMessages.map((msg, idx) => (
                            <motion.div key={idx} className={cn("max-w-[85%] p-3 rounded-2xl text-sm", msg.role === 'user' ? "bg-red-600 text-white ml-auto rounded-tr-none" : "bg-slate-800 text-slate-200 mr-auto rounded-tl-none border border-slate-700")}>{msg.text}</motion.div>
                          ))
                        )}
                        {chatLoading && <div className="text-slate-500 text-xs italic">Düşünüyor...</div>}
                      </div>
                      <form onSubmit={sendChatMessage} className="p-4 border-t border-slate-800 flex gap-2">
                        <input type="text" placeholder="Soru yazın..." className="flex-1 bg-slate-800 border border-slate-700 rounded-xl px-4 py-2 text-sm text-white focus:outline-none" value={userMessage} onChange={(e) => setUserMessage(e.target.value)} disabled={!summary || chatLoading} />
                        <button type="submit" disabled={!summary || chatLoading || !userMessage.trim()} className="p-2 bg-red-600 hover:bg-red-700 rounded-xl disabled:bg-slate-700 transition-colors"><ChevronRight size={20} /></button>
                      </form>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </main>

          <footer className="bg-slate-800/80 text-slate-400 border-t border-slate-700/50 px-8 py-2 flex justify-between items-center shrink-0 text-[10px] font-sans">
             <div className="flex items-center gap-4">
                <span className="flex items-center gap-1.5">
                   <div className={cn("w-1.5 h-1.5 rounded-full", loading ? "bg-amber-400 animate-pulse" : "bg-green-500")}></div>
                   {loading ? "Analiz Ediliyor..." : "Sistem Aktif"}
                </span>
                <span>Masaüstü Sürümü v2.5</span>
             </div>
             <div className="italic">Emulated Environment • Google AI Studio Build</div>
          </footer>
        </div>
      </div>
    </div>
  );
}
