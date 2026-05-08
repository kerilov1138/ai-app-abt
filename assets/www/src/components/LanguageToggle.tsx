import React from 'react';
import { Languages } from 'lucide-react';

interface LanguageToggleProps {
  lang: 'tr' | 'en';
  toggleLang: () => void;
}

export default function LanguageToggle({ lang, toggleLang }: LanguageToggleProps) {
  return (
    <button
      onClick={toggleLang}
      className="flex items-center gap-2 px-4 py-2 rounded-full bg-white dark:bg-slate-800 shadow-md hover:shadow-lg transition-all active:scale-95 font-bold text-sm"
    >
      <Languages size={18} className="text-indigo-600" />
      <span>{lang === 'tr' ? 'Türkçe' : 'English'}</span>
    </button>
  );
}
