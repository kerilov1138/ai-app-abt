import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'motion/react';
import { Play, Pause, RotateCcw, RotateCw, Volume2 } from 'lucide-react';

interface AudioModuleProps {
  lang: 'tr' | 'en';
}

export default function AudioModule({ lang }: AudioModuleProps) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [playbackRate, setPlaybackRate] = useState(1);
  
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const AUDIO_URL = "/audio-summary.mp3";
  const speeds = [1.0, 1.2, 1.5, 1.8, 2.0];

  useEffect(() => {
    const audio = new Audio(AUDIO_URL);
    audioRef.current = audio;

    const updateProgress = () => {
      setCurrentTime(audio.currentTime);
      setProgress((audio.currentTime / (audio.duration || 1)) * 100);
    };

    const handleLoadedMetadata = () => {
      setDuration(audio.duration);
    };

    const handleEnded = () => {
      setIsPlaying(false);
      setProgress(0);
      setCurrentTime(0);
    };

    audio.addEventListener('timeupdate', updateProgress);
    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('ended', handleEnded);

    return () => {
      audio.removeEventListener('timeupdate', updateProgress);
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('ended', handleEnded);
      audio.pause();
    };
  }, []);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate]);

  const togglePlay = () => {
    if (audioRef.current) {
      if (isPlaying) {
        audioRef.current.pause();
        setIsPlaying(false);
      } else {
        audioRef.current.play().then(() => {
          setIsPlaying(true);
        }).catch(err => {
          console.error("Oynatma hatası:", err);
          alert(lang === 'tr' 
            ? "Ses dosyası bulunamadı! Lütfen sol taraftaki 'public' klasörüne 'audio-summary.mp3' isimli dosyanızı yüklediğinizden emin olun." 
            : "Audio file not found! Please ensure you uploaded 'audio-summary.mp3' to the 'public' folder.");
        });
      }
    }
  };

  const skip = (amount: number) => {
    if (audioRef.current) {
      audioRef.current.currentTime = Math.max(0, Math.min(audioRef.current.duration, audioRef.current.currentTime + amount));
    }
  };

  const formatTime = (time: number) => {
    if (isNaN(time)) return "00:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <div className="max-w-2xl mx-auto space-y-8 py-8">
      <div className="bg-white dark:bg-slate-800 rounded-[3rem] shadow-2xl p-10 border border-slate-100 dark:border-slate-700">
        <div className="flex flex-col items-center text-center">
          <div className="flex justify-center w-full mb-8 relative">
            <div className={`w-24 h-24 bg-indigo-500 rounded-3xl flex items-center justify-center shadow-2xl shadow-indigo-200 dark:shadow-none ${isPlaying ? 'animate-pulse' : ''}`}>
              <Volume2 size={48} className="text-white" />
            </div>
            
            {/* Speed Selector */}
            <div className="absolute right-0 top-0 flex flex-col gap-2">
              <span className="text-[10px] font-black text-slate-400 mb-1">{lang === 'tr' ? 'HIZ' : 'SPEED'}</span>
              {speeds.map(s => (
                <button
                  key={s}
                  onClick={() => setPlaybackRate(s)}
                  className={`text-[10px] font-bold px-2 py-1.5 rounded-lg border transition-all ${playbackRate === s ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-50 dark:bg-slate-700 text-slate-400 dark:text-slate-400 border-slate-200 dark:border-slate-600 hover:border-indigo-300 dark:hover:border-indigo-500'}`}
                >
                  {s.toFixed(1)}x
                </button>
              ))}
            </div>
          </div>
          
          <h2 className="text-3xl font-black mb-2">{lang === 'tr' ? 'Sesli Özet Dinle' : 'Listen to Audio Summary'}</h2>
          <p className="opacity-60 mb-10">{lang === 'tr' ? 'Eczacı Uzmanımız Anlatıyor' : 'Our Pharmacist Expert Explains'}</p>

          <div className="w-full h-2 bg-slate-100 dark:bg-slate-700 rounded-full mb-4 overflow-hidden">
            <motion.div 
              className="h-full bg-indigo-600"
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.1 }}
            />
          </div>

          <div className="w-full flex justify-between text-xs font-mono opacity-40 mb-8">
            <span>{formatTime(currentTime)}</span>
            <span>{formatTime(duration)}</span>
          </div>

          <div className="flex items-center gap-6">
            <button 
              onClick={() => skip(-10)}
              className="relative flex items-center justify-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-all p-2 group"
              title="-10s"
            >
              <RotateCcw size={32} />
              <span className="absolute text-[9px] font-black mt-1 group-hover:text-indigo-500 transition-colors">10</span>
            </button>
            
            <button 
              onClick={togglePlay}
              className="w-20 h-20 bg-indigo-600 text-white rounded-full flex items-center justify-center hover:scale-110 active:scale-95 transition-all shadow-xl shadow-indigo-200 dark:shadow-none"
            >
              {isPlaying ? <Pause size={36} fill="white" /> : <Play size={36} className="ml-1" fill="white" />}
            </button>

            <button 
              onClick={() => skip(10)}
              className="relative flex items-center justify-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-all p-2 group"
              title="+10s"
            >
              <RotateCw size={32} />
              <span className="absolute text-[9px] font-black mt-1 group-hover:text-indigo-500 transition-colors">10</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
