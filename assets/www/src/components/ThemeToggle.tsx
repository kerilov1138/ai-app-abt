import React from 'react';
import { Sun, Moon } from 'lucide-react';
import { motion } from 'motion/react';

interface ThemeToggleProps {
  theme: 'light' | 'dark';
  toggleTheme: () => void;
}

export default function ThemeToggle({ theme, toggleTheme }: ThemeToggleProps) {
  return (
    <button
      onClick={toggleTheme}
      className="p-3 rounded-full bg-white dark:bg-slate-800 shadow-md hover:scale-110 transition-transform active:scale-95"
      aria-label="Toggle theme"
    >
      {theme === 'light' ? (
        <Moon className="text-slate-600" size={20} />
      ) : (
        <Sun className="text-amber-400" size={20} />
      )}
    </button>
  );
}
