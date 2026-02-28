
import React, { useState, useEffect } from 'react';
import { NizamType, ArsaBilgileri, HesapSonuclari, AnalizDetay } from './types';
import ConstructionForm from './components/ConstructionForm';
import PlotVisualizer from './components/PlotVisualizer';
import ResultsPanel from './components/ResultsPanel';
import Header from './components/Header';
import PDFExportButton from './components/PDFExportButton';

const App: React.FC = () => {
  const [darkMode, setDarkMode] = useState(true);
  const [formData, setFormData] = useState<ArsaBilgileri | null>(null);
  const [results, setResults] = useState<HesapSonuclari | null>(null);

  useEffect(() => {
    if (darkMode) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [darkMode]);

  const calculateData = (data: ArsaBilgileri) => {
    let kaksVal, tabanAlani, zeminBrut, normalKatBrut, toplamInsaatAlani;

    if (data.nizam === NizamType.BITISIK) {
      tabanAlani = data.manuelTabanAlani;
      zeminBrut = tabanAlani * 1.1;
      normalKatBrut = zeminBrut * 1.15;
      toplamInsaatAlani = (normalKatBrut * data.katAdedi) + zeminBrut;
      kaksVal = toplamInsaatAlani / data.alan;
    } else if (data.useKaks) {
      kaksVal = data.kaks;
      toplamInsaatAlani = data.kaks * data.alan * 1.3;
      normalKatBrut = toplamInsaatAlani / data.katAdedi;
      zeminBrut = normalKatBrut;
      tabanAlani = normalKatBrut;
    } else {
      kaksVal = data.taks * data.katAdedi;
      tabanAlani = data.alan * data.taks;
      zeminBrut = tabanAlani * 1.1;
      normalKatBrut = zeminBrut * 1.15;
      toplamInsaatAlani = (normalKatBrut * data.katAdedi) + zeminBrut;
    }

    // Arkadan çekme hesabı: (kat sayısı * 3) / 2
    const hesaplanmisArkaCekme = (data.katAdedi * 3) / 2;
    const updatedData = { ...data, cekmeArka: hesaplanmisArkaCekme };
    
    // Satılabilir Alan (Genelde toplam inşaat alanının %80-85'i net süpürülebilir alandır, 
    // ancak biz brüt satış üzerinden hesaplıyoruz)
    const satilabilirAlan = toplamInsaatAlani * 0.95; // Ortak alanlar çıktıktan sonraki yaklaşık brüt

    const toplamMaliyet = toplamInsaatAlani * data.birimMaliyet;
    const toplamMaliyetArsaDahil = data.arsaBedeli ? toplamMaliyet + data.arsaBedeli : undefined;
    const toplamGelir = satilabilirAlan * data.birimSatisFiyati;
    const toplamKar = data.arsaBedeli ? toplamGelir - (toplamMaliyet + data.arsaBedeli) : toplamGelir - toplamMaliyet;

    // Malzeme Tahmini
    const tahminiBeton = toplamInsaatAlani * 0.38;
    const tahminiDemir = tahminiBeton * 0.090;

    const analizler: AnalizDetay[] = [1, 2, 3, 4].map(adet => {
      const m2 = normalKatBrut / adet;
      let tip = "";
      if (m2 < 105) tip = "1+1 APART";
      else if (m2 >= 105 && m2 < 115) tip = "2+1 DAİRE";
      else if (m2 >= 115 && m2 <= 150) tip = "3+1 DAİRE";
      else tip = "4+1 DAİRE";
      return { adet, tip, m2 };
    });

    setResults({
      kaks: kaksVal,
      tabanAlani,
      zeminBrut,
      normalKatBrut,
      toplamInsaatAlani,
      toplamKat: data.katAdedi,
      toplamDaire: data.katAdedi * data.daireSayisiTercihi,
      toplamMaliyet,
      toplamMaliyetArsaDahil,
      toplamGelir,
      toplamKar,
      birimDaireAnalizi: analizler,
      malzeme: {
        beton: tahminiBeton,
        demir: tahminiDemir,
        tugla: toplamInsaatAlani * 45
      }
    });
    setFormData(updatedData);
  };

  return (
    <div className="min-h-screen pb-12 transition-colors duration-300 bg-gray-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-sans selection:bg-blue-500 selection:text-white">
      <Header darkMode={darkMode} setDarkMode={setDarkMode} />
      
      <main className="container mx-auto px-4 mt-8 max-w-7xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          <div className="lg:col-span-4 space-y-6">
            <section className="bg-white dark:bg-slate-800/80 backdrop-blur-xl p-6 rounded-[2rem] shadow-2xl border border-gray-100 dark:border-slate-700/50 sticky top-24">
              <ConstructionForm onCalculate={calculateData} />
            </section>
          </div>

          <div className="lg:col-span-8 space-y-8">
            {formData && results ? (
              <div className="space-y-8 animate-in fade-in zoom-in duration-500">
                <div className="flex justify-end">
                  <PDFExportButton formData={formData} />
                </div>
                <PlotVisualizer data={formData} results={results} />
                <ResultsPanel results={results} data={formData} />
              </div>
            ) : (
              <div className="h-[600px] flex flex-col items-center justify-center bg-white dark:bg-slate-800/30 rounded-[3rem] border-4 border-dashed border-gray-200 dark:border-slate-800 text-center p-12">
                <div className="w-24 h-24 bg-blue-100 dark:bg-blue-900/20 rounded-3xl flex items-center justify-center mb-8 rotate-12">
                  <i className="fa-solid fa-coins text-5xl text-blue-600/50"></i>
                </div>
                <h3 className="text-3xl font-black text-slate-400 dark:text-slate-600 uppercase tracking-tighter">Finansal Analize Başlayın</h3>
                <p className="text-slate-400 mt-4 text-base max-w-md mx-auto font-medium leading-relaxed">
                  Arsa verilerini ve bölgedeki m² satış fiyatlarını (Sahibinden, Hürriyet Emlak vb.) girerek kar-zarar tablosunu hemen görün.
                </p>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};

export default App;
