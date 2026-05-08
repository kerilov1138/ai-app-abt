import React from 'react';
import { ContentType } from '../App';
import { 
  Volume2, 
  Presentation, 
  PlayCircle, 
  Image as ImageIcon, 
  Layers,
  Home,
  Trophy
} from 'lucide-react';

interface NavigationProps {
  activeTab: ContentType;
  setActiveTab: (tab: ContentType) => void;
  lang: 'tr' | 'en';
}

export default function Navigation({ activeTab, setActiveTab, lang }: NavigationProps) {
  const t = {
    tr: {
      welcome: 'Ana Sayfa',
      audio: 'Sesli Özet',
      slides: 'Slaytlar',
      video: 'Video',
      info: 'Soy Ağacı',
      cards: 'Kartlar',
      quiz: 'Yarışma'
    },
    en: {
      welcome: 'Home',
      audio: 'Audio',
      slides: 'Slides',
      video: 'Video',
      info: 'Family Tree',
      cards: 'Cards',
      quiz: 'Quiz'
    }
  }[lang];

  const items = [
    { id: 'welcome' as const, label: t.welcome, icon: Home },
    { id: 'audio' as const, label: t.audio, icon: Volume2 },
    { id: 'slides' as const, label: t.slides, icon: Presentation },
    { id: 'video' as const, label: t.video, icon: PlayCircle },
    { id: 'info' as const, label: t.info, icon: ImageIcon },
    { id: 'cards' as const, label: t.cards, icon: Layers },
    { id: 'quiz' as const, label: t.quiz, icon: Trophy }
  ];

  return (
    <div className="flex flex-col gap-3">
      {items.map((item) => {
        const isActive = activeTab === item.id;
        return (
          <button
            key={item.id}
            onClick={() => setActiveTab(item.id)}
            className={`
              w-full flex items-center gap-4 p-4 rounded-2xl transition-all duration-300 group
              ${isActive 
                ? 'bg-indigo-600 text-white shadow-xl shadow-indigo-200 dark:shadow-none translate-x-1' 
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700/50'}
            `}
          >
            <div className={`
              w-10 h-10 rounded-xl flex items-center justify-center transition-colors
              ${isActive ? 'bg-white/20' : 'bg-slate-100 dark:bg-slate-700 group-hover:bg-indigo-100 dark:group-hover:bg-indigo-900/30'}
            `}>
              <item.icon size={20} className={isActive ? 'text-white' : 'text-indigo-500'} />
            </div>
            <div className="text-left">
              <span className="block text-sm font-black tracking-tight">{item.label}</span>
              <span className={`block text-[9px] uppercase tracking-widest font-bold opacity-60`}>
                {isActive ? 'Aktif Modül' : 'Modülü Aç'}
              </span>
            </div>
          </button>
        );
      })}
    </div>
  );
}
