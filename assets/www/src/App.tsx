/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Volume2, 
  Presentation, 
  PlayCircle, 
  Image as ImageIcon, 
  Layers, 
  Sun, 
  Moon, 
  Languages,
  BookOpen,
  Stethoscope,
  Trophy
} from 'lucide-react';
import Navigation from './components/Navigation';
import ThemeToggle from './components/ThemeToggle';
import LanguageToggle from './components/LanguageToggle';
import AudioModule from './components/AudioModule';
import SlidesModule from './components/SlidesModule';
import VideoModule from './components/VideoModule';
import InfographicModule from './components/InfographicModule';
import FlashcardsModule from './components/FlashcardsModule';
import QuizModule from './components/QuizModule';

export type ContentType = 'audio' | 'slides' | 'video' | 'info' | 'cards' | 'welcome' | 'quiz';

export default function App() {
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const [lang, setLang] = useState<'tr' | 'en'>('tr');
  const [activeTab, setActiveTab] = useState<ContentType>('welcome');

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  const toggleTheme = () => setTheme(prev => prev === 'light' ? 'dark' : 'light');
  const toggleLang = () => setLang(prev => prev === 'tr' ? 'en' : 'tr');

  const t = {
    tr: {
      title: 'Şafak Öncü Koleji Farmakoloji Portalı',
      subtitle: 'ATC İlaç Sınıflandırma ve Eğitim Materyalleri',
      welcome: 'Hoş Geldiniz, Genç Eczacı!',
      welcomeDesc: 'Bu uygulama ile ilaçların dünyasını ve onların nasıl sınıflandırıldığını eğlenceli materyallerle keşfedebilirsin.',
      audio: 'Sesli Özet',
      slides: 'Slayt Sunusu',
      video: 'Videolu Özet',
      info: 'İnfografik (Soy Ağacı)',
      cards: 'Bilgi Kartları',
      quiz: 'Bilgi Yarışması',
      getStarted: 'Başlamak İçin Modül Seçin'
    },
    en: {
      title: 'Şafak Öncü College Pharmacology Portal',
      subtitle: 'ATC Drug Classification & Educational Materials',
      welcome: 'Welcome, Young Pharmacist!',
      welcomeDesc: 'With this application, you can explore the world of medicines and how they are classified through fun materials.',
      audio: 'Audio Summary',
      slides: 'Slide Presentation',
      video: 'Video Summary',
      info: 'Infographic (Family Tree)',
      cards: 'Flashcards',
      quiz: 'Knowledge Quiz',
      getStarted: 'Select a Module to Start'
    }
  }[lang];

  return (
    <div className={`min-h-screen flex flex-col transition-colors duration-300 font-sans ${theme === 'dark' ? 'bg-[#0f172a] text-slate-100' : 'bg-[#f8fafc] text-slate-900'}`}>
      {/* Header Navigation */}
      <header className="h-20 bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between px-8 shadow-md z-50 sticky top-0">
        <div className="flex items-center w-64">
          <h1 className="text-xl font-black tracking-tight text-indigo-900 dark:text-indigo-400">ŞAFAK ÖNCÜ KOLEJİ</h1>
        </div>

        <div className="flex-1 flex justify-center items-center">
          <img 
            src="/signature.png" 
            alt="M. Kemal Atatürk" 
            className="h-16 w-auto object-contain dark:invert transition-all grayscale brightness-0 dark:grayscale-0 dark:brightness-100"
          />
        </div>

        <div className="flex items-center gap-4 w-64 justify-end">
          <LanguageToggle lang={lang} toggleLang={toggleLang} />
        </div>
      </header>

      {/* Hero Section if Welcome */}
      <AnimatePresence mode="wait">
        {activeTab === 'welcome' ? (
          <main className="flex-1 flex flex-col items-center justify-center p-8 text-center overflow-hidden">
            <motion.div
              key="welcome"
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="max-w-4xl relative"
            >
              <div className="relative mb-12 flex justify-center">
                <motion.div 
                  animate={{ rotate: 360 }}
                  transition={{ duration: 40, repeat: Infinity, ease: "linear" }}
                  className="absolute inset-0 bg-indigo-500/10 rounded-full blur-[80px]"
                />
                <div className="w-32 h-32 bg-white dark:bg-slate-800 rounded-[2.5rem] shadow-2xl flex items-center justify-center relative z-10 border border-slate-100 dark:border-slate-700">
                  <Stethoscope size={64} className="text-indigo-600" strokeWidth={1.5} />
                </div>
              </div>

              <h2 className="text-5xl font-black mb-6 tracking-tight opacity-70">{t.welcome}</h2>
              <p className="text-xl max-w-2xl mx-auto opacity-70 leading-relaxed mb-12 font-medium">
                {t.welcomeDesc}
              </p>
              
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 w-full">
                {[
                  { id: 'audio' as const, label: t.audio, icon: Volume2, color: 'bg-blue-500', sub: lang === 'tr' ? 'Dinleyerek Öğren' : 'Learn by Listening' },
                  { id: 'slides' as const, label: t.slides, icon: Presentation, color: 'bg-rose-500', sub: lang === 'tr' ? 'Görsel Anlatım' : 'Visual Story' },
                  { id: 'video' as const, label: t.video, icon: PlayCircle, color: 'bg-amber-500', sub: lang === 'tr' ? 'Uzmanından İzle' : 'Watch Experts' },
                  { id: 'info' as const, label: t.info, icon: ImageIcon, color: 'bg-emerald-500', sub: lang === 'tr' ? 'Şematik Bakış' : 'Schematic View' },
                  { id: 'cards' as const, label: t.cards, icon: Layers, color: 'bg-purple-500', sub: lang === 'tr' ? 'Hafıza Teknikleri' : 'Memory Tech' },
                  { id: 'quiz' as const, label: t.quiz, icon: Trophy, color: 'bg-indigo-600', sub: lang === 'tr' ? 'Kendini Test Et' : 'Test Yourself' }
                ].map((item) => (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className="group flex flex-col items-center justify-center gap-4 bg-white dark:bg-slate-800 p-4 rounded-[2rem] shadow-xl shadow-slate-200/50 dark:shadow-none hover:scale-105 active:scale-95 transition-all relative overflow-hidden border border-slate-50 dark:border-slate-700"
                  >
                    <div className={`w-12 h-12 ${item.color} rounded-2xl flex items-center justify-center text-white shadow-lg`}>
                      <item.icon size={24} />
                    </div>
                    <div className="text-center">
                      <span className="block font-black text-[12px] text-slate-800 dark:text-slate-100">{item.label}</span>
                      <span className="block text-[8px] font-bold text-slate-400 uppercase tracking-tighter">{item.sub}</span>
                    </div>
                  </button>
                ))}
              </div>
            </motion.div>
          </main>
        ) : (
          <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
            {/* Sidebar-style Nav */}
            <nav className="w-full md:w-72 bg-white dark:bg-slate-800 border-r border-slate-200 dark:border-slate-700 p-6 flex flex-col overflow-y-auto">
              <div className="flex items-center justify-between mb-8 md:hidden">
                <LanguageToggle lang={lang} toggleLang={toggleLang} />
              </div>
              
              <h2 className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-8">{lang === 'tr' ? 'İLERLEME ARAÇLARI' : 'PROGRESS TOOLS'}</h2>
              <div className="space-y-4">
                <Navigation activeTab={activeTab} setActiveTab={setActiveTab} lang={lang} />
              </div>

              <div className="mt-auto">
                {/* Removed Tip of the Day section */}
              </div>
            </nav>

            {/* Sub-module Content */}
            <main className="flex-1 p-8 overflow-y-auto bg-[#f8fafc] dark:bg-[#0f172a]">
              <div className="max-w-6xl mx-auto">
                {activeTab === 'audio' && <AudioModule lang={lang} />}
                {activeTab === 'slides' && <SlidesModule lang={lang} />}
                {activeTab === 'video' && <VideoModule lang={lang} />}
                {activeTab === 'info' && <InfographicModule lang={lang} />}
                {activeTab === 'cards' && <FlashcardsModule lang={lang} />}
                {activeTab === 'quiz' && <QuizModule lang={lang} />}
              </div>
            </main>
          </div>
        ) }
      </AnimatePresence>

      {/* Footer Status Bar */}
      <footer className="h-10 bg-indigo-900 text-white flex items-center justify-between px-8 text-[10px] font-bold tracking-wide">
        <div className="flex items-center gap-6">
        </div>
        <div className="flex items-center gap-6">
          <span className="flex items-center gap-2"><span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span> {lang === 'tr' ? 'SİSTEM HAZIR' : 'SYSTEM READY'}</span>
          <span className="hidden sm:inline opacity-60">© 2026 ŞAFAK ÖNCÜ KOLEJİ</span>
        </div>
      </footer>
    </div>
  );
}

