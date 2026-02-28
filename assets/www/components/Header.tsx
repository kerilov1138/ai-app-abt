
import React from 'react';

interface HeaderProps {
  darkMode: boolean;
  setDarkMode: (val: boolean) => void;
}

const Header: React.FC<HeaderProps> = ({ darkMode, setDarkMode }) => {
  return (
    <header className="sticky top-0 z-50 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md border-b border-gray-200 dark:border-slate-800 shadow-sm">
      <div className="container mx-auto px-4 h-20 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-blue-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-500/30">
            <i className="fa-solid fa-helmet-safety text-white text-2xl"></i>
          </div>
          <div>
            <h1 className="font-black text-xl leading-tight uppercase tracking-tighter">Müteahhit <span className="text-blue-600">Asistanı</span></h1>
            <p className="text-[10px] uppercase tracking-[0.3em] text-gray-500 font-bold">Profesyonel İmar Analiz Sistemi</p>
            <p className="text-[9px] text-blue-600/60 font-medium italic mt-0.5">Design by Civil Engineer Kerem Akşahin</p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <button
            onClick={() => setDarkMode(!darkMode)}
            className="p-2 w-10 h-10 rounded-full bg-gray-100 dark:bg-slate-800 hover:bg-gray-200 dark:hover:bg-slate-700 transition-all flex items-center justify-center border border-gray-200 dark:border-slate-700"
          >
            {darkMode ? (
              <i className="fa-solid fa-sun text-yellow-400"></i>
            ) : (
              <i className="fa-solid fa-moon text-indigo-600"></i>
            )}
          </button>
        </div>
      </div>
    </header>
  );
};

export default Header;
