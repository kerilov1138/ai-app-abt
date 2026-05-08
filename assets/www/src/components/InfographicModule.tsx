import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Apple, 
  Droplet, 
  Heart, 
  Thermometer, 
  Users, 
  Activity, 
  ShieldAlert, 
  Microscope, 
  Bone, 
  Brain, 
  Bug, 
  Wind, 
  Eye, 
  FlaskConical,
  ChevronRight,
  ChevronDown,
  Search,
  BookOpen
} from 'lucide-react';
import { ATC_CATEGORIES, DRUGS, ATCCategory } from '../data/atcData';

const ICON_MAP: { [key: string]: any } = {
  Apple,
  Droplet,
  Heart,
  Thermometer,
  Users,
  Activity,
  ShieldAlert,
  Microscope,
  Bone,
  Brain,
  Bug,
  Wind,
  Eye,
  FlaskConical
};

interface InfographicModuleProps {
  lang: 'tr' | 'en';
}

export default function InfographicModule({ lang }: InfographicModuleProps) {
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const isNumericSearch = !isNaN(Number(searchQuery)) && searchQuery.trim() !== '';

  const matchedDrugs = DRUGS.filter(drug => {
    if (isNumericSearch) {
      return drug.sn.toString() === searchQuery.trim();
    }
    return drug.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
           drug.atc.toLowerCase().includes(searchQuery.toLowerCase());
  });

  const filteredCategories = ATC_CATEGORIES.filter(cat => {
    // If text search, check category names too
    const categoryMatches = !isNumericSearch && (
      cat.nameTr.toLowerCase().includes(searchQuery.toLowerCase()) ||
      cat.nameEn.toLowerCase().includes(searchQuery.toLowerCase()) ||
      cat.id.toLowerCase().includes(searchQuery.toLowerCase())
    );

    // Check if any matched drugs belong to this category
    const hasMatchedDrug = matchedDrugs.some(drug => drug.atc === cat.id);

    return categoryMatches || hasMatchedDrug;
  });

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-8 pb-12"
    >
      <div className="text-center space-y-4 max-w-2xl mx-auto">
        <h2 className="text-4xl font-black tracking-tight">{lang === 'tr' ? 'İlaçların Soy Ağacı' : 'Family Tree of Drugs'}</h2>
        <p className="text-lg opacity-60 font-medium">
          {lang === 'tr' 
            ? 'ATC (Anatomik Terapötik Kimyasal) sistemi ile ilaçların dünyasını hiyerarşik olarak keşfedin.' 
            : 'Explore the world of drugs hierarchically with the ATC (Anatomical Therapeutic Chemical) system.'}
        </p>
      </div>

      {/* Search Bar */}
      <div className="relative max-w-md mx-auto">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" size={20} />
        <input 
          type="text"
          placeholder={lang === 'tr' ? 'SN, isim veya kod ara...' : 'Search SN, name or code...'}
          className="w-full pl-12 pr-4 py-4 bg-white dark:bg-slate-800 rounded-2xl border-2 border-slate-100 dark:border-slate-700 focus:border-indigo-500 outline-none transition-all shadow-lg shadow-slate-200/50 dark:shadow-none"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredCategories.map((cat) => {
          const Icon = ICON_MAP[cat.icon] || BookOpen;
          // Only show matched drugs if searching
          const drugsInCategory = searchQuery.trim() !== '' 
            ? matchedDrugs.filter(d => d.atc === cat.id)
            : DRUGS.filter(d => d.atc === cat.id);
            
          const isOpen = selectedCategory === cat.id || searchQuery.trim() !== '';

          return (
            <motion.div
              layout
              key={cat.id}
              className={`rounded-[2rem] border-2 transition-all duration-500 overflow-hidden ${
                isOpen 
                ? 'col-span-full bg-white dark:bg-slate-800 border-indigo-500 shadow-2xl' 
                : 'bg-white/50 dark:bg-slate-800/50 border-slate-100 dark:border-slate-700 hover:border-indigo-300 dark:hover:border-indigo-900 shadow-sm'
              }`}
            >
              <button
                onClick={() => setSelectedCategory(isOpen ? null : cat.id)}
                className="w-full p-6 flex items-center justify-between group"
              >
                <div className="flex items-center gap-6">
                  <div className={`w-14 h-14 ${cat.color} rounded-2xl flex items-center justify-center text-white shadow-lg group-hover:scale-110 transition-transform`}>
                    <Icon size={28} />
                  </div>
                  <div className="text-left">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-black px-2 py-1 bg-slate-100 dark:bg-slate-700 rounded-lg text-slate-500">{cat.id}</span>
                      <span className="text-[10px] font-black text-indigo-500 uppercase tracking-widest leading-none">
                        {drugsInCategory.length} {lang === 'tr' ? 'MATERYAL' : 'MATERIALS'}
                      </span>
                    </div>
                    <h3 className="text-xl font-black mt-1 leading-tight">{lang === 'tr' ? cat.nameTr : cat.nameEn}</h3>
                  </div>
                </div>
                {isOpen ? <ChevronDown className="text-slate-400" /> : <ChevronRight className="text-slate-400" />}
              </button>

              <AnimatePresence>
                {isOpen && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="border-t border-slate-100 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/30"
                  >
                    <div className="p-8 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                      {drugsInCategory.length > 0 ? (
                        drugsInCategory.map((drug) => (
                          <div 
                            key={drug.sn}
                            className="bg-white dark:bg-slate-800 p-5 rounded-2xl border border-slate-100 dark:border-slate-700 shadow-sm hover:shadow-md transition-shadow"
                          >
                            <div className="flex items-center justify-between mb-2">
                              <span className="text-[10px] font-black text-indigo-500">S.N. {drug.sn}</span>
                            </div>
                            <h4 className="font-black text-slate-800 dark:text-slate-100 mb-2">{drug.name}</h4>
                            <p className="text-xs text-slate-500 leading-relaxed italic">
                              "{lang === 'tr' ? drug.descriptionTr : drug.descriptionEn}"
                            </p>
                          </div>
                        ))
                      ) : (
                        <div className="col-span-full py-12 text-center opacity-40">
                          <BookOpen size={48} className="mx-auto mb-4" />
                          <p className="font-bold">{lang === 'tr' ? 'Henüz bu kategoride ilaç kaydı bulunmuyor.' : 'No drugs registered in this category yet.'}</p>
                        </div>
                      )}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          );
        })}
      </div>
    </motion.div>
  );
}
