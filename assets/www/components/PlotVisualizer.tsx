
import React from 'react';
import { ArsaBilgileri, HesapSonuclari } from '../types';

interface PlotVisualizerProps {
  data: ArsaBilgileri;
  results: HesapSonuclari | null;
}

const PlotVisualizer: React.FC<PlotVisualizerProps> = ({ data, results }) => {
  // SVG Canvas ayarları
  const svgWidth = 800;
  const svgHeight = 500;
  const margin = 80;

  // Arsa boyutlarını basitleştirmek için yaklaşık bir dikdörtgen hesaplıyoruz
  const arsaEn = Math.sqrt(data.alan) * 1.2;
  const arsaBoy = data.alan / arsaEn;

  // Ölçekleme faktörü
  const scale = Math.min((svgWidth - 2 * margin) / arsaEn, (svgHeight - 2 * margin) / arsaBoy);

  const drawArsaW = arsaEn * scale;
  const drawArsaH = arsaBoy * scale;
  
  const startX = (svgWidth - drawArsaW) / 2;
  const startY = (svgHeight - drawArsaH) / 2;

  // Çekme mesafeleri ölçeklendirme
  const sOn = data.cekmeOn * scale;
  const sArka = data.cekmeArka * scale;
  const sYan1 = data.cekmeYan1 * scale;
  const sYan2 = data.cekmeYan2 * scale;

  // Bina oturum alanı (TAKS ile sınırlandırılmış ama çekmelere uyumlu)
  const bMaxW = drawArsaW - (sYan1 + sYan2);
  const bMaxH = drawArsaH - (sOn + sArka);

  // TAKS'a göre olması gereken alan
  const taksAlanDraw = data.taks * data.alan * (scale * scale);
  // Bina görselini biraz daha gerçekçi yapmak için max alana sığdırıyoruz
  const areaRatio = Math.min(1, taksAlanDraw / (bMaxW * bMaxH));
  const finalBW = bMaxW * Math.sqrt(areaRatio);
  const finalBH = bMaxH * Math.sqrt(areaRatio);
  
  const bX = startX + sYan1 + (bMaxW - finalBW) / 2;
  const bY = startY + sOn + (bMaxH - finalBH) / 2;

  return (
    <div id="vaziyet-plani" className="relative bg-white dark:bg-slate-950 rounded-[3rem] shadow-2xl border border-gray-100 dark:border-slate-800/50 overflow-hidden group">
      {/* Üst Bilgi Paneli */}
      <div className="absolute top-8 left-8 z-10">
        <h3 className="text-slate-900 dark:text-white font-black text-3xl tracking-tighter uppercase leading-none">Vaziyet Planı</h3>
        <div className="flex items-center gap-2 mt-3">
          <span className="bg-blue-600 text-white text-[9px] font-black px-3 py-1 rounded-full uppercase tracking-widest">TEKNİK ÇİZİM</span>
          <span className="text-slate-400 text-[10px] font-bold uppercase tracking-widest">{data.alan} m² TOPLAM ARSA</span>
        </div>
      </div>

      <div className="absolute top-8 right-8 z-10 flex gap-3">
        <a 
          href={`https://parselsorgu.tkgm.gov.tr/#il=${data.il}&ilce=${data.ilce}&mahalle=${data.mahalle}&ada=${data.ada}&parsel=${data.parsel}`} 
          target="_blank" 
          rel="noreferrer"
          className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 text-[10px] font-black px-6 py-3 rounded-2xl uppercase tracking-widest shadow-xl flex items-center gap-2 transition-all hover:scale-105 active:scale-95"
        >
          <i className="fa-solid fa-map-location-dot"></i> TKGM SORGULA
        </a>
      </div>

      <div className="flex justify-center items-center min-h-[550px] p-12">
        <svg width="100%" height="100%" viewBox={`0 0 ${svgWidth} ${svgHeight}`} className="drop-shadow-2xl overflow-visible">
          {/* Arsa Dış Sınırları */}
          <rect 
            x={startX} y={startY} width={drawArsaW} height={drawArsaH} 
            fill="transparent" 
            stroke="#94a3b8" 
            strokeWidth="2" 
            strokeDasharray="8,8" 
            rx="4"
          />
          
          {/* Çekme Mesafesi Rehber Çizgileri */}
          <g stroke="#cbd5e1" strokeWidth="1" strokeDasharray="4,4" opacity="0.4">
            <line x1={startX} y1={startY + sOn} x2={startX + drawArsaW} y2={startY + sOn} />
            <line x1={startX} y1={startY + drawArsaH - sArka} x2={startX + drawArsaW} y2={startY + drawArsaH - sArka} />
            <line x1={startX + sYan1} y1={startY} x2={startX + sYan1} y2={startY + drawArsaH} />
            <line x1={startX + drawArsaW - sYan2} y1={startY} x2={startX + drawArsaW - sYan2} y2={startY + drawArsaH} />
          </g>

          {/* Çekme Mesafe Etiketleri */}
          <g fill="#64748b" fontSize="10" fontWeight="800" className="uppercase tracking-widest">
            {/* Ön */}
            <text x={startX + drawArsaW / 2} y={startY + sOn / 2 + 4} textAnchor="middle">Ön Çekme: {data.cekmeOn}m</text>
            {/* Arka */}
            <text x={startX + drawArsaW / 2} y={startY + drawArsaH - sArka / 2 + 4} textAnchor="middle">Arka Çekme: {data.cekmeArka}m</text>
            {/* Yanlar */}
            <text x={startX + sYan1 / 2} y={startY + drawArsaH / 2} textAnchor="middle" transform={`rotate(-90, ${startX + sYan1 / 2}, ${startY + drawArsaH / 2})`}>
              {data.cekmeYan1 > 0 ? `Yan: ${data.cekmeYan1}m` : 'BİTİŞİK'}
            </text>
            <text x={startX + drawArsaW - sYan2 / 2} y={startY + drawArsaH / 2} textAnchor="middle" transform={`rotate(90, ${startX + drawArsaW - sYan2 / 2}, ${startY + drawArsaH / 2})`}>
              {data.cekmeYan2 > 0 ? `Yan: ${data.cekmeYan2}m` : 'BİTİŞİK'}
            </text>
          </g>

          {/* Bina Oturumu (TAKS) */}
          {results && (
            <g className="animate-in fade-in zoom-in duration-1000">
              <rect 
                x={bX} y={bY} width={finalBW} height={finalBH} 
                fill="rgba(37, 99, 235, 0.15)" 
                stroke="#2563eb" 
                strokeWidth="4" 
                rx="8"
              />
              <rect x={bX} y={bY} width={finalBW} height={finalBH} fill="url(#grid)" opacity="0.2" rx="8" />
              
              <g transform={`translate(${bX + finalBW / 2}, ${bY + finalBH / 2})`}>
                <text fill="#2563eb" fontSize="14" fontWeight="900" textAnchor="middle" dy="-5" className="uppercase tracking-tighter">
                  {data.useKaks ? "KAT BRÜT ALANI" : "BİNA OTURUMU"}
                </text>
                <text fill="#1d4ed8" fontSize="18" fontWeight="900" textAnchor="middle" dy="15">{results.tabanAlani.toFixed(1)} m²</text>
              </g>
            </g>
          )}

          <defs>
            <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
              <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#2563eb" strokeWidth="0.5"/>
            </pattern>
          </defs>
        </svg>
      </div>

      {/* Alt Bilgi Barı */}
      <div className="absolute bottom-0 left-0 right-0 p-10 bg-gradient-to-t from-slate-50 dark:from-slate-900 via-transparent flex justify-between items-end pointer-events-none">
        <div className="space-y-1">
          <p className="text-slate-400 text-[9px] font-black uppercase tracking-widest">Tapu Bilgileri</p>
          <p className="text-lg font-black text-slate-900 dark:text-white uppercase tracking-tighter">
            {data.il} / {data.ilce} / {data.ada} ADA {data.parsel} PARSEL
          </p>
        </div>
        <div className="text-right space-y-1">
          <p className="text-slate-400 text-[9px] font-black uppercase tracking-widest">Yapı Nizamı</p>
          <p className="text-lg font-black text-blue-600 uppercase tracking-tighter">{data.nizam}</p>
        </div>
      </div>
    </div>
  );
};

export default PlotVisualizer;
