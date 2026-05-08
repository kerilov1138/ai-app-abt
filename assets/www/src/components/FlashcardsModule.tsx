import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { RefreshCcw, ThumbsUp, ChevronLeft, ChevronRight } from 'lucide-react';
import { DRUGS } from '../data/atcData';

interface FlashcardsModuleProps {
  lang: 'tr' | 'en';
}

export default function FlashcardsModule({ lang }: FlashcardsModuleProps) {
  const [index, setIndex] = useState(0);
  const [isFlipped, setIsFlipped] = useState(false);

  const currentDrug = DRUGS[index % DRUGS.length];

  const nextCard = () => {
    setIsFlipped(false);
    setTimeout(() => setIndex(prev => (prev + 1) % DRUGS.length), 150);
  };

  const prevCard = () => {
    setIsFlipped(false);
    setTimeout(() => setIndex(prev => (prev - 1 + DRUGS.length) % DRUGS.length), 150);
  };

  return (
    <div className="max-w-xl mx-auto space-y-8 py-8 flex flex-col items-center">
      <div className="text-center">
        <h2 className="text-3xl font-black mb-2">{lang === 'tr' ? 'İlaç Bilgi Kartları' : 'Drug Flashcards'}</h2>
        <p className="opacity-60">{lang === 'tr' ? 'Kartı çevirerek ilacın kullanım amacını ve ATC sınıfını gör' : 'Flip the card to see the purpose and ATC class of the drug'}</p>
      </div>

      <div 
        className="relative w-full aspect-[3/4] sm:aspect-square cursor-pointer perspective-1000"
        onClick={() => setIsFlipped(!isFlipped)}
      >
        <motion.div
          animate={{ rotateY: isFlipped ? 180 : 0 }}
          transition={{ duration: 0.6, type: "spring", stiffness: 260, damping: 20 }}
          style={{ transformStyle: 'preserve-3d' }}
          className="relative w-full h-full shadow-2xl rounded-[3rem]"
        >
          {/* Front */}
          <div 
            className="absolute inset-0 bg-white dark:bg-slate-800 rounded-[3rem] flex flex-col items-center justify-center p-12 text-center backface-hidden border-4 border-slate-100 dark:border-slate-700"
          >
            <div className="text-6xl font-black text-indigo-500 mb-6 font-mono opacity-20">S.N {currentDrug.sn}</div>
            <h3 className="text-3xl font-black mb-4">{currentDrug.name}</h3>
            <div className="flex items-center gap-2 text-indigo-500 font-bold uppercase tracking-widest text-sm">
              <RefreshCcw size={16} />
              {lang === 'tr' ? 'Çevirmek İçin Tıkla' : 'Click to Flip'}
            </div>
          </div>

          {/* Back */}
          <div 
            className="absolute inset-0 bg-indigo-600 text-white rounded-[3rem] flex flex-col items-center justify-center p-12 text-center backface-hidden border-4 border-white/20 rotate-y-180"
          >
            <div className="text-sm font-bold uppercase tracking-[0.2em] mb-4 opacity-70">
              {lang === 'tr' ? 'ATC SINIFI' : 'ATC CLASS'}: {currentDrug.atc}
            </div>
            <p className="text-2xl font-medium leading-relaxed mb-8">
              {lang === 'tr' ? currentDrug.descriptionTr : currentDrug.descriptionEn}
            </p>
            <div className="p-4 bg-white/20 rounded-2xl">
              <ThumbsUp size={40} />
            </div>
          </div>
        </motion.div>
      </div>

      <div className="flex items-center gap-8">
        <button 
          onClick={(e) => { e.stopPropagation(); prevCard(); }}
          className="p-4 rounded-full bg-slate-200 dark:bg-slate-700 hover:scale-110 active:scale-95 transition-all"
        >
          <ChevronLeft size={28} />
        </button>
        <div className="font-black text-xl opacity-40">
          {index + 1} / {DRUGS.length}
        </div>
        <button 
          onClick={(e) => { e.stopPropagation(); nextCard(); }}
          className="p-4 rounded-full bg-slate-200 dark:bg-slate-700 hover:scale-110 active:scale-95 transition-all"
        >
          <ChevronRight size={28} />
        </button>
      </div>
    </div>
  );
}
