
import React, { useState } from 'react';
import { NizamType, ArsaBilgileri } from '../types';

interface ConstructionFormProps {
  onCalculate: (data: ArsaBilgileri) => void;
}

const ConstructionForm: React.FC<ConstructionFormProps> = ({ onCalculate }) => {
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState<any>({
    alan: '',
    il: 'Uşak',
    ilce: 'Merkez',
    mahalle: '',
    ada: '',
    parsel: '',
    nizam: NizamType.AYRIK,
    cekmeOn: 5,
    cekmeYan1: 3,
    cekmeYan2: 3,
    cekmeArka: 3,
    taks: 0.35,
    kaks: 1.5,
    useKaks: false,
    manuelTabanAlani: 100,
    katAdedi: 3,
    daireSayisiTercihi: 4,
    birimMaliyet: 8500,
    birimSatisFiyati: 23076,
    arsaBedeli: ''
  });

  const nextStep = () => setStep(prev => prev + 1);
  const prevStep = () => setStep(prev => prev - 1);

  const handleInput = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData((prev: any) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onCalculate({
      ...formData,
      alan: Number(formData.alan),
      cekmeOn: Number(formData.cekmeOn),
      cekmeYan1: Number(formData.cekmeYan1 || 0),
      cekmeYan2: Number(formData.cekmeYan2 || 0),
      cekmeArka: Number(formData.cekmeArka),
      taks: Number(formData.taks),
      kaks: Number(formData.kaks),
      useKaks: formData.useKaks,
      manuelTabanAlani: Number(formData.manuelTabanAlani),
      katAdedi: Number(formData.katAdedi),
      daireSayisiTercihi: Number(formData.daireSayisiTercihi),
      birimMaliyet: Number(formData.birimMaliyet),
      birimSatisFiyati: Number(formData.birimSatisFiyati),
      arsaBedeli: formData.arsaBedeli ? Number(formData.arsaBedeli) : undefined
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-xl font-black uppercase tracking-tighter">Fizibilite Formu</h2>
          <p className="text-[9px] text-slate-500 font-bold uppercase tracking-widest mt-1">İmar, Maliyet ve Kar</p>
        </div>
        <span className="text-[10px] font-black bg-blue-600 text-white px-4 py-2 rounded-xl shadow-lg shadow-blue-500/20">ADIM {step}/4</span>
      </div>

      {step === 1 && (
        <div className="space-y-4 animate-in fade-in slide-in-from-right duration-300">
          <div className="space-y-1">
            <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Arsa Toplam Alanı (m²)</label>
            <input type="number" name="alan" value={formData.alan} onChange={handleInput} placeholder="Örn: 400" className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold focus:ring-2 focus:ring-blue-500 outline-none transition-all" required />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[10px] font-black text-slate-400 uppercase ml-1">İl</label>
              <input type="text" name="il" value={formData.il} readOnly className="w-full bg-gray-100 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold outline-none cursor-not-allowed opacity-70" />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] font-black text-slate-400 uppercase ml-1">İlçe</label>
              <select name="ilce" value={formData.ilce} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold outline-none focus:ring-2 focus:ring-blue-500 appearance-none">
                {['Banaz', 'Eşme', 'Karahallı', 'Merkez', 'Sivaslı', 'Ulubey'].map(ilce => (
                  <option key={ilce} value={ilce}>{ilce}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <input type="text" name="ada" placeholder="Ada" value={formData.ada} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold outline-none" required />
            <input type="text" name="parsel" placeholder="Parsel" value={formData.parsel} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold outline-none" required />
          </div>
          <button type="button" onClick={nextStep} disabled={!formData.alan || !formData.parsel} className="w-full bg-blue-600 hover:bg-blue-500 text-white font-black py-5 rounded-2xl shadow-xl shadow-blue-500/20 active:scale-95 transition-all uppercase tracking-widest text-xs flex items-center justify-center gap-2">
            DEVAM ET <i className="fa-solid fa-arrow-right"></i>
          </button>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-6 animate-in fade-in slide-in-from-right duration-300">
          <p className="text-xs font-bold text-slate-500 text-center uppercase tracking-widest">Yapı Nizamı ve İmar</p>
          <div className="grid grid-cols-2 gap-4">
            <button type="button" onClick={() => { setFormData({...formData, nizam: NizamType.AYRIK}); }} className={`p-6 rounded-[2rem] border-2 transition-all flex flex-col items-center gap-3 ${formData.nizam === NizamType.AYRIK ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20' : 'border-gray-100 dark:border-slate-800 hover:bg-gray-50'}`}>
              <i className="fa-solid fa-arrows-left-right-to-line text-2xl text-blue-600"></i>
              <span className="text-[11px] font-black uppercase">Ayrık</span>
            </button>
            <button type="button" onClick={() => { setFormData({...formData, nizam: NizamType.BITISIK}); }} className={`p-6 rounded-[2rem] border-2 transition-all flex flex-col items-center gap-3 ${formData.nizam === NizamType.BITISIK ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20' : 'border-gray-100 dark:border-slate-800 hover:bg-gray-50'}`}>
              <i className="fa-solid fa-house-chimney text-2xl text-slate-400"></i>
              <span className="text-[11px] font-black uppercase">Bitişik</span>
            </button>
          </div>
          <div className={`flex items-center gap-2 bg-blue-50 dark:bg-blue-900/10 p-3 rounded-2xl border border-blue-100 dark:border-blue-900/20 ${formData.nizam === NizamType.BITISIK ? 'opacity-30 pointer-events-none' : ''}`}>
            <input 
              type="checkbox" 
              id="useKaks" 
              name="useKaks" 
              checked={formData.useKaks} 
              onChange={(e) => setFormData({...formData, useKaks: e.target.checked})}
              className="w-5 h-5 rounded-lg accent-blue-600"
            />
            <label htmlFor="useKaks" className="text-[11px] font-black uppercase text-blue-700 dark:text-blue-400 cursor-pointer">Emsal (KAKS) Üzerinden Hesapla</label>
          </div>

          {formData.nizam === NizamType.BITISIK ? (
            <div className="space-y-1 animate-in fade-in slide-in-from-top duration-300">
              <label className="text-[10px] font-black text-blue-600 uppercase ml-1">Manuel Bina Oturum Alanı (m²)</label>
              <input type="number" name="manuelTabanAlani" value={formData.manuelTabanAlani} onChange={handleInput} className="w-full bg-blue-50 dark:bg-blue-900/20 border-2 border-blue-200 dark:border-blue-800 rounded-2xl py-4 px-4 text-sm font-bold outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              <div className={`space-y-1 transition-opacity ${formData.useKaks ? 'opacity-30 pointer-events-none' : 'opacity-100'}`}>
                <label className="text-[10px] font-black text-slate-400 uppercase ml-1">TAKS</label>
                <input type="number" step="0.01" name="taks" value={formData.taks} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" />
              </div>
              <div className={`space-y-1 transition-all ${formData.useKaks ? 'ring-2 ring-blue-500 rounded-2xl' : 'opacity-100'}`}>
                <label className="text-[10px] font-black text-slate-400 uppercase ml-1">KAKS (Emsal)</label>
                <input type="number" step="0.01" name="kaks" value={formData.kaks} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" />
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Kat Adedi</label>
              <input type="number" name="katAdedi" value={formData.katAdedi} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Ön Çekme (m)</label>
              <input type="number" name="cekmeOn" value={formData.cekmeOn} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" />
            </div>
          </div>

          {formData.nizam === NizamType.BITISIK ? (
            <div className="grid grid-cols-2 gap-3 animate-in fade-in slide-in-from-top duration-300">
              <div className="space-y-1">
                <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Yan 1 Çekme (Boş = Bitişik)</label>
                <input type="number" name="cekmeYan1" value={formData.cekmeYan1} onChange={handleInput} placeholder="Bitişik" className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Yan 2 Çekme (Boş = Bitişik)</label>
                <input type="number" name="cekmeYan2" value={formData.cekmeYan2} onChange={handleInput} placeholder="Bitişik" className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" />
              </div>
            </div>
          ) : (
            <div className="space-y-1">
              <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Yan Çekme (m)</label>
              <input 
                type="number" 
                name="cekmeYan" 
                value={formData.cekmeYan1} 
                onChange={(e) => setFormData({...formData, cekmeYan1: e.target.value, cekmeYan2: e.target.value})} 
                className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-4 text-sm font-bold outline-none" 
              />
            </div>
          )}

          <button type="button" onClick={nextStep} className="w-full bg-blue-600 text-white font-black py-5 rounded-2xl shadow-xl uppercase tracking-widest text-xs">EKONOMİK VERİLER</button>
          <button type="button" onClick={prevStep} className="w-full text-slate-400 font-black py-2 uppercase text-[10px] tracking-widest">Geri Dön</button>
        </div>
      )}

      {step === 3 && (
        <div className="space-y-5 animate-in fade-in slide-in-from-right duration-300">
          <div className="bg-amber-50 dark:bg-amber-900/10 p-4 rounded-2xl border border-amber-100 dark:border-amber-900/20 mb-2">
             <p className="text-[10px] font-bold text-amber-700 dark:text-amber-400 leading-tight">
               <i className="fa-solid fa-info-circle mr-1"></i>
               Satış fiyatını Sahibinden, Hürriyet Emlak veya Emlakjet verilerine göre belirleyiniz.
             </p>
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-black text-slate-400 uppercase ml-1">İnşaat m² Maliyeti (TL)</label>
            <input type="number" name="birimMaliyet" value={formData.birimMaliyet} onChange={handleInput} className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Tahmini m² Satış Fiyatı (TL)</label>
            <input type="number" name="birimSatisFiyati" value={formData.birimSatisFiyati} onChange={handleInput} className="w-full bg-emerald-50 dark:bg-emerald-900/10 border border-emerald-200 dark:border-emerald-800 rounded-2xl py-4 px-5 text-sm font-bold outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-black text-slate-400 uppercase ml-1">Arsa Bedeli (TL - Opsiyonel)</label>
            <input type="number" name="arsaBedeli" value={formData.arsaBedeli} onChange={handleInput} placeholder="Boş bırakılırsa hesaba katılmaz" className="w-full bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-2xl py-4 px-5 text-sm font-bold outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <button type="button" onClick={nextStep} className="w-full bg-blue-600 text-white font-black py-5 rounded-2xl shadow-xl uppercase tracking-widest text-xs">
            DAİRE TERCİHİ
          </button>
          <button type="button" onClick={prevStep} className="w-full text-slate-400 font-black py-2 uppercase text-[10px] tracking-widest">Geri Dön</button>
        </div>
      )}

      {step === 4 && (
        <div className="space-y-6 animate-in fade-in slide-in-from-right duration-300">
          <p className="text-xs font-bold text-slate-500 text-center">Kat Başı Daire Sayısı Planı</p>
          <div className="grid grid-cols-4 gap-2">
            {[1, 2, 3, 4].map(n => (
              <button key={n} type="button" onClick={() => setFormData({...formData, daireSayisiTercihi: n})} className={`py-5 rounded-2xl border-2 font-black text-lg transition-all ${formData.daireSayisiTercihi === n ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20 text-blue-600' : 'border-gray-100 dark:border-slate-800 text-slate-400'}`}>
                {n}
              </button>
            ))}
          </div>
          <button type="submit" className="w-full bg-emerald-600 text-white font-black py-5 rounded-2xl shadow-xl uppercase tracking-widest text-xs flex items-center justify-center gap-2">
            HESAPLA VE ANALİZ ET <i className="fa-solid fa-chart-line"></i>
          </button>
          <button type="button" onClick={prevStep} className="w-full text-slate-400 font-black py-2 uppercase text-[10px] tracking-widest">Geri Dön</button>
        </div>
      )}
    </form>
  );
};

export default ConstructionForm;
