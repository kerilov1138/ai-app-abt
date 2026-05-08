import React, { useState, useRef } from 'react';
import { motion } from 'motion/react';
import { Play, Maximize2, Settings, Volume2, User } from 'lucide-react';

interface VideoModuleProps {
  lang: 'tr' | 'en';
}

export default function VideoModule({ lang }: VideoModuleProps) {
  const [isPlaying, setIsPlaying] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);

  const togglePlay = () => {
    if (videoRef.current) {
      if (isPlaying) {
        videoRef.current.pause();
      } else {
        videoRef.current.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-8 py-8">
      <div className="text-center mb-8">
        <h2 className="text-3xl font-black">{lang === 'tr' ? 'Videolu ATC Özeti' : 'Video ATC Summary'}</h2>
        <p className="opacity-60">{lang === 'tr' ? 'Renkli animasyonlarla sınıflandırmayı izle' : 'Watch the classification with colorful animations'}</p>
      </div>

      <div 
        className="relative aspect-video bg-black rounded-[3rem] overflow-hidden shadow-[0_50px_100px_-20px_rgba(0,0,0,0.5)] group cursor-pointer"
        onClick={togglePlay}
      >
        <video 
          ref={videoRef}
          className="w-full h-full object-cover"
          controls={isPlaying}
          poster="https://images.unsplash.com/photo-1587854692152-cbe660dbbb88?auto=format&fit=crop&q=80&w=1000"
          onPlay={() => setIsPlaying(true)}
          onPause={() => setIsPlaying(false)}
        >
          <source src="/atc-video.mp4" type="video/mp4" />
          {lang === 'tr' ? 'Tarayıcınız video etiketini desteklemiyor.' : 'Your browser does not support the video tag.'}
        </video>
        
        {!isPlaying && (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-900/60 transition-opacity group-hover:bg-slate-900/40">
            <motion.div 
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
              className="w-24 h-24 bg-white text-black rounded-full flex items-center justify-center shadow-2xl"
            >
              <Play size={40} className="ml-1" fill="black" />
            </motion.div>
          </div>
        )}
      </div>

    </div>
  );
}
