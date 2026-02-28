
import React from 'react';
import { HesapSonuclari, ArsaBilgileri } from '../types';

interface ResultsPanelProps {
  results: HesapSonuclari;
  data: ArsaBilgileri;
}

const ResultsPanel: React.FC<ResultsPanelProps> = ({ results, data }) => {
  const StatBox = ({ label, value, unit = "", sub = "", color = "text-blue-600", icon = "" }: any) => (
    <div className="bg-white dark:bg-slate-800/50 p-6 rounded-[2.5rem] border border-gray-100 dark:border-slate-700/50 flex flex-col items-center text-center shadow-lg transition-transform hover:scale-105 group">
      <div className={`mb-3 w-10 h-10 rounded-xl flex items-center justify-center ${color.replace('text', 'bg')}/10 ${color}`}>
        <i className={icon}></i>
      </div>
      <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mb-2">{label}</p>
      <div className="flex items-baseline gap-1">
        <span className={`text-2xl font-black tracking-tighter ${color}`}>{value}</span>
        <span className="text-sm font-bold text-slate-400">{unit}</span>
      </div>
      {sub && <p className="text-[10px] font-bold text-slate-500 mt-2 uppercase tracking-tighter">{sub}</p>}
    </div>
  );

  const formatCurrency = (val: number) => {
    if (val >= 1000000) return (val / 1000000).toFixed(2) + " M TL";
    return val.toLocaleString('tr-TR') + " TL";
  };

  return (
    <div className="space-y-8 pb-12">
      {/* Finansal Özet Kartı */}
      <div id="finansal-ozet" className="bg-gradient-to-br from-slate-900 to-slate-800 dark:from-slate-800 dark:to-slate-950 p-8 rounded-[3rem] shadow-2xl border border-slate-700 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-12 opacity-5">
           <i className="fa-solid fa-sack-dollar text-[15rem] text-white"></i>
        </div>
        <div className="relative z-10 grid grid-cols-1 md:grid-cols-3 gap-8 text-center">
          <div className="p-6 border-r border-slate-700/50 last:border-0 flex flex-col justify-center">
             <p className="text-slate-400 text-[10px] font-black uppercase tracking-widest mb-4">Tahmini Toplam Maliyet</p>
             <div className="flex flex-row justify-center items-center gap-4">
               <div className="text-center">
                 <p className="text-2xl font-black text-rose-500 tracking-tighter">{formatCurrency(results.toplamMaliyet)}</p>
                 <p className="text-slate-500 text-[7px] font-black uppercase">İnşaat Maliyeti</p>
               </div>
               {results.toplamMaliyetArsaDahil && (
                 <>
                   <div className="h-8 w-px bg-slate-700/50"></div>
                   <div className="text-center">
                     <p className="text-2xl font-black text-rose-400 tracking-tighter">{formatCurrency(results.toplamMaliyetArsaDahil)}</p>
                     <p className="text-slate-500 text-[7px] font-black uppercase text-nowrap">Arsa Dahil Toplam</p>
                   </div>
                 </>
               )}
             </div>
          </div>
          <div className="p-6 border-r border-slate-700/50 last:border-0 flex flex-col justify-center">
             <p className="text-slate-400 text-[10px] font-black uppercase tracking-widest mb-2">Tahmini Toplam Satış</p>
             <p className="text-4xl font-black text-emerald-500 tracking-tighter">{formatCurrency(results.toplamGelir)}</p>
             <p className="text-emerald-500/50 text-[9px] mt-2 font-bold uppercase">Sahibinden/Hürriyet Emlak Bazlı</p>
          </div>
          <div className="p-6 bg-white/5 rounded-[2rem] border border-white/10 shadow-inner">
             <p className="text-blue-400 text-[10px] font-black uppercase tracking-widest mb-2">Tahmini Net Kar</p>
             <p className="text-5xl font-black text-white tracking-tighter">{formatCurrency(results.toplamKar)}</p>
             <div className="mt-3 inline-block px-4 py-1 bg-emerald-500/20 text-emerald-400 text-[10px] font-black rounded-full uppercase tracking-widest">
                ROI: %{((results.toplamKar / (results.toplamMaliyetArsaDahil || results.toplamMaliyet)) * 100).toFixed(1)}
             </div>
          </div>
        </div>
      </div>

      {/* Teknik Metrikler */}
      <div id="teknik-metrikler" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        <StatBox label="İNŞAAT ALANI" value={results.toplamInsaatAlani.toFixed(0)} unit="m²" sub="Tüm Katlar Brüt" icon="fa-solid fa-maximize" />
        <StatBox label="BETON" value={results.malzeme.beton.toFixed(0)} unit="m³" color="text-amber-500" sub="C30/37 Standart" icon="fa-solid fa-truck-ramp-box" />
        <StatBox label="DEMİR" value={results.malzeme.demir.toFixed(1)} unit="TON" color="text-slate-500" sub="B420C Çelik" icon="fa-solid fa-bars-staggered" />
        <StatBox label="TOPLAM DAİRE" value={results.toplamDaire} unit="ADET" color="text-indigo-500" sub="Üretilecek Bölüm" icon="fa-solid fa-door-open" />
      </div>

      {/* Daire Tipoloji ve Satış Analizi */}
      <div id="birim-analiz" className="bg-white dark:bg-slate-800 rounded-[3rem] shadow-2xl p-10 border border-gray-100 dark:border-slate-700/50">
        <div className="flex flex-col md:flex-row justify-between items-center mb-10 gap-4">
          <div>
            <h3 className="text-2xl font-black uppercase tracking-tighter flex items-center gap-4">
              <div className="w-12 h-12 bg-emerald-500/10 text-emerald-500 rounded-2xl flex items-center justify-center">
                <i className="fa-solid fa-building-circle-arrow-right"></i>
              </div>
              Birim Analiz ve Tipoloji
            </h3>
            <p className="text-slate-400 text-sm font-medium mt-1">Kat başına düşen potansiyel daire değerleri.</p>
          </div>
          <div id="m2-satis-birim" className="flex items-center gap-3">
             <div className="text-right">
                <p className="text-[9px] font-black text-slate-400 uppercase">m² Satış Birim</p>
                <p className="text-lg font-black text-emerald-600">{data.birimSatisFiyati.toLocaleString()} TL</p>
             </div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {results.birimDaireAnalizi.map((analiz, idx) => {
            const daireSatis = analiz.m2 * data.birimSatisFiyati;
            return (
              <div key={idx} className={`p-8 rounded-[2.5rem] border-2 transition-all group relative overflow-hidden ${analiz.adet === data.daireSayisiTercihi ? 'border-blue-500 bg-blue-50/50 dark:bg-blue-900/10' : 'border-gray-50 dark:border-slate-800/50 hover:border-blue-100 dark:hover:border-slate-700'}`}>
                <div className="flex justify-between items-start mb-4">
                  <span className="text-4xl font-black tracking-tighter">{analiz.adet}</span>
                  <span className="text-[10px] font-black uppercase text-slate-400">DAİRE/KAT</span>
                </div>
                <div className="space-y-1 mb-6">
                  <p className="text-2xl font-black tracking-tight">{analiz.m2.toFixed(1)} m²</p>
                  <p className="text-[10px] font-black uppercase text-blue-600 dark:text-blue-400">{analiz.tip}</p>
                </div>
                <div className="pt-4 border-t border-gray-100 dark:border-slate-700 daire-satis-fiyati">
                  <p className="text-[9px] font-black text-slate-400 uppercase mb-1">Daire Satış Fiyatı</p>
                  <p className="text-xl font-black text-emerald-600 group-hover:scale-110 transition-transform origin-left">{formatCurrency(daireSatis)}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default ResultsPanel;
