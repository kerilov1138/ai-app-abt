
import React, { useState } from 'react';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';
import { ArsaBilgileri } from '../types';

interface PDFExportButtonProps {
  formData: ArsaBilgileri;
}

const PDFExportButton: React.FC<PDFExportButtonProps> = ({ formData }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [selectedSections, setSelectedSections] = useState({
    vaziyet: true,
    finansal: true,
    teknik: true,
    birim: true,
    satisBirim: true,
    daireFiyat: true
  });

  const sections = [
    { id: 'vaziyet-plani', key: 'vaziyet', label: 'Vaziyet Planı' },
    { id: 'finansal-ozet', key: 'finansal', label: 'Finansal Özet' },
    { id: 'teknik-metrikler', key: 'teknik', label: 'Teknik Metrikler' },
    { id: 'birim-analiz', key: 'birim', label: 'Birim Analiz ve Tipoloji' }
  ];

  const handleExport = async () => {
    setIsExporting(true);
    
    // Temporarily hide sub-elements if not selected
    const satisBirimEl = document.getElementById('m2-satis-birim');
    const daireFiyatEls = document.querySelectorAll('.daire-satis-fiyati');
    
    const originalSatisBirimDisplay = satisBirimEl?.style.display;
    if (!selectedSections.satisBirim && satisBirimEl) {
      satisBirimEl.style.display = 'none';
    }
    
    const originalDaireFiyatDisplays: string[] = [];
    daireFiyatEls.forEach((el) => {
      const htmlEl = el as HTMLElement;
      originalDaireFiyatDisplays.push(htmlEl.style.display);
      if (!selectedSections.daireFiyat) {
        htmlEl.style.display = 'none';
      }
    });

    try {
      const pdf = new jsPDF('p', 'mm', 'a4');
      const pdfWidth = pdf.internal.pageSize.getWidth();
      const pdfHeight = pdf.internal.pageSize.getHeight();
      const margin = 10;
      const contentWidth = pdfWidth - 2 * margin;

      let currentY = margin;
      let firstPage = true;

      for (const section of sections) {
        if (selectedSections[section.key as keyof typeof selectedSections]) {
          const element = document.getElementById(section.id);
          if (element) {
            const canvas = await html2canvas(element, {
              scale: 2,
              useCORS: true,
              backgroundColor: document.documentElement.classList.contains('dark') ? '#0f172a' : '#ffffff',
              logging: false
            });

            const imgData = canvas.toDataURL('image/png');
            const imgProps = pdf.getImageProperties(imgData);
            const imgHeight = (imgProps.height * contentWidth) / imgProps.width;

            // Check if we need a new page
            if (!firstPage && currentY + imgHeight > pdfHeight - margin) {
              pdf.addPage();
              currentY = margin;
            }

            if (firstPage) {
              firstPage = false;
            }

            pdf.addImage(imgData, 'PNG', margin, currentY, contentWidth, imgHeight);
            currentY += imgHeight + 5; // Reduced gap for "yekpare" look
          }
        }
      }

      const fileName = `${formData.ada}_${formData.parsel}_${formData.il}_${formData.ilce}.pdf`.replace(/\s+/g, '_');
      pdf.save(fileName);
    } catch (error) {
      console.error('PDF Export Error:', error);
    } finally {
      // Restore visibility
      if (satisBirimEl) satisBirimEl.style.display = originalSatisBirimDisplay || '';
      daireFiyatEls.forEach((el, i) => {
        (el as HTMLElement).style.display = originalDaireFiyatDisplays[i] || '';
      });
      
      setIsExporting(false);
      setIsOpen(false);
    }
  };

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-6 py-3 rounded-2xl font-black text-[10px] uppercase tracking-widest shadow-xl hover:scale-105 active:scale-95 transition-all flex items-center gap-2"
      >
        <i className="fa-solid fa-file-pdf"></i> PDF İNDİR
      </button>

      {isOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white dark:bg-slate-900 w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-slate-800 p-8 animate-in zoom-in-95 duration-200 max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-xl font-black uppercase tracking-tighter">PDF Dışa Aktar</h3>
              <button onClick={() => setIsOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200">
                <i className="fa-solid fa-xmark text-xl"></i>
              </button>
            </div>

            <p className="text-xs font-bold text-slate-500 mb-4 uppercase tracking-widest">Ana Bölümler:</p>
            <div className="space-y-2 mb-6">
              {sections.map((section) => (
                <label
                  key={section.key}
                  className="flex items-center gap-3 p-3 rounded-xl border-2 border-slate-100 dark:border-slate-800 hover:border-blue-500 dark:hover:border-blue-500 cursor-pointer transition-all group"
                >
                  <input
                    type="checkbox"
                    checked={selectedSections[section.key as keyof typeof selectedSections]}
                    onChange={(e) => setSelectedSections({ ...selectedSections, [section.key]: e.target.checked })}
                    className="w-4 h-4 rounded accent-blue-600"
                  />
                  <span className="text-xs font-bold uppercase tracking-tight group-hover:text-blue-600 transition-colors">{section.label}</span>
                </label>
              ))}
            </div>

            <p className="text-xs font-bold text-slate-500 mb-4 uppercase tracking-widest">Detay Seçenekleri:</p>
            <div className="space-y-2 mb-8">
              <label className="flex items-center gap-3 p-3 rounded-xl border-2 border-slate-100 dark:border-slate-800 hover:border-emerald-500 dark:hover:border-emerald-500 cursor-pointer transition-all group">
                <input
                  type="checkbox"
                  checked={selectedSections.satisBirim}
                  onChange={(e) => setSelectedSections({ ...selectedSections, satisBirim: e.target.checked })}
                  className="w-4 h-4 rounded accent-emerald-600"
                />
                <span className="text-xs font-bold uppercase tracking-tight group-hover:text-emerald-600 transition-colors">m² Satış Birim Fiyatı</span>
              </label>
              <label className="flex items-center gap-3 p-3 rounded-xl border-2 border-slate-100 dark:border-slate-800 hover:border-emerald-500 dark:hover:border-emerald-500 cursor-pointer transition-all group">
                <input
                  type="checkbox"
                  checked={selectedSections.daireFiyat}
                  onChange={(e) => setSelectedSections({ ...selectedSections, daireFiyat: e.target.checked })}
                  className="w-4 h-4 rounded accent-emerald-600"
                />
                <span className="text-xs font-bold uppercase tracking-tight group-hover:text-emerald-600 transition-colors">Daire Satış Fiyatları</span>
              </label>
            </div>

            <button
              onClick={handleExport}
              disabled={isExporting || !Object.values(selectedSections).some(v => v)}
              className={`w-full py-5 rounded-2xl font-black text-xs uppercase tracking-widest shadow-xl flex items-center justify-center gap-3 transition-all ${
                isExporting 
                ? 'bg-slate-100 text-slate-400 cursor-not-allowed' 
                : 'bg-blue-600 hover:bg-blue-500 text-white active:scale-95'
              }`}
            >
              {isExporting ? (
                <>
                  <i className="fa-solid fa-circle-notch animate-spin"></i> HAZIRLANIYOR...
                </>
              ) : (
                <>
                  <i className="fa-solid fa-download"></i> PDF OLARAK KAYDET
                </>
              )}
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default PDFExportButton;
