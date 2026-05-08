import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  ChevronLeft, 
  ChevronRight, 
  Presentation, 
  TreePine, 
  Map as MapIcon, 
  User, 
  Apple, 
  Heart, 
  ShieldCheck, 
  Brain, 
  Activity, 
  Wind, 
  ShieldHalf, 
  FlaskConical, 
  Droplets, 
  Microscope, 
  Target 
} from 'lucide-react';

interface SlidesModuleProps {
  lang: 'tr' | 'en';
}

export default function SlidesModule({ lang }: SlidesModuleProps) {
  const [currentSlide, setCurrentSlide] = useState(0);

  const slides = {
    tr: [
      {
        title: 'İlaçların Soy Ağacı',
        subtitle: '256 Sihirli Sırrın Keşfi',
        content: 'Merhaba Genç Bilim İnsanları! Doğanın şifalı sırlarının kimya laboratuvarında nasıl ilaca dönüştüğünü keşfetmeye hazır mısınız? Köklerden dallara doğru çıkıyoruz!',
        icon: TreePine,
        color: 'from-emerald-500 to-teal-700'
      },
      {
        title: 'ATC Sistemi Nedir?',
        subtitle: 'İlaçların Koordinat Haritası',
        content: 'Her dal, vücudumuzdaki bir sistemi temsil eder (A: Sindirim, N: Sinir, R: Solunum, vb.). Yaprak ve meyveler ise o sisteme etki eden farmasötik materyallerdir.',
        icon: MapIcon,
        color: 'from-amber-400 to-orange-600'
      },
      {
        title: 'İnsan Bedeni ve İlaç Ağacı',
        subtitle: 'Karşılaştırma Matrisi',
        content: 'İlaçlar vücudumuzda sadece kendi hedeflerini bulacak şekilde tasarlanır! Cilt kremi (D) ve nefes açıcı (R) bu yüzden farklı dallardadır.',
        icon: User,
        color: 'from-blue-400 to-indigo-600'
      },
      {
        title: 'A-Dalı: Sindirim Sistemi',
        subtitle: 'Enerji Santralimiz',
        content: 'Laksatifler (Müshiller), Vitaminler ve Mineraller ile Mide Rahatlatıcılar bu dalda bulunur. C Vitamini bağışıklığımızın şövalyesidir!',
        icon: Apple,
        color: 'from-lime-400 to-green-600'
      },
      {
        title: 'B & C Dalları: Kan ve Kalp',
        subtitle: 'Yaşam Pompası',
        content: 'Demir preparatları kanımıza kırmızı rengini verir. Kardiyak terapi ise kalbimizi güçlendirir. Deniz Soğanı kalbin kasılma gücünü artıran tarihi bir bitkidir.',
        icon: Heart,
        color: 'from-rose-500 to-red-700'
      },
      {
        title: 'D-Dalı: Dermatoloji',
        subtitle: 'Koruyucu Kalkanımız',
        content: 'Yumuşatıcılar, taşıyıcı bazlar ve antiseptik merhemler bu daldadır. Çinko Oksit güneş kremlerinin baş kahramanıdır!',
        icon: ShieldCheck,
        color: 'from-orange-300 to-amber-500'
      },
      {
        title: 'N-Dalı: Sinir Sistemi (1)',
        subtitle: 'Ağrı Yönetimi ve Anestezi',
        content: 'Analjezikler ağrıyı keser, anestezikler ise uyuşturur. Dietil Eter, ameliyatlarda acı hissedilmemesi için kullanılan en eski kimyasallardan biridir.',
        icon: Brain,
        color: 'from-indigo-400 to-violet-600'
      },
      {
        title: 'N-Dalı: Sinir Sistemi (2)',
        subtitle: 'Sakinleştiriciler ve Uyarıcılar',
        content: 'Psikoleptikler (Vitesi Düşürenler) ve Uyarıcılar (Vitesi Yükseltenler) burada yer alır. Kediotu yüzyıllardır uykusuzluk çekenlerin dostudur.',
        icon: Activity,
        color: 'from-purple-400 to-fuchsia-600'
      },
      {
        title: 'R-Dalı: Solunum Sistemi',
        subtitle: 'Nefes Tazeleyici Rüzgarlar',
        content: 'Ekspektoranlar (Balgam Söktürücüler) akciğeri temizleyen süpürgeler gibidir. Aromatik özler ise ferahlık sağlar.',
        icon: Wind,
        color: 'from-cyan-400 to-blue-500'
      },
      {
        title: 'Savunma Birlikleri',
        subtitle: 'P, J, G ve M Dalları',
        content: 'Antiparazitler ve antimikrobiyaller vücudumuzu korur. Terapötik botanikler ise doğanın ham hazineleridir.',
        icon: ShieldHalf,
        color: 'from-emerald-600 to-teal-800'
      },
      {
        title: 'Bir İlaç Nasıl Doğar?',
        subtitle: 'Ekstraksiyon Süreci',
        content: 'Biyoloji (Doğal Kaynak) + Kimya (Çözücü ve İşlem) = Farmakoloji (Son Ürün). Doğadaki maddeleri saflaştırma işlemine "Ekstraksiyon" diyoruz.',
        icon: FlaskConical,
        color: 'from-sky-400 to-blue-600'
      },
      {
        title: 'V-Kategorisi: Kimyasallar',
        subtitle: 'Özütleyiciler ve Kimyasal Bazlar',
        content: 'Asitler, alkoller, çözücüler ve indikallar ağacın besinidir. Reaksiyonları başlatır, aktif maddeleri çözer ve ilaçları stabilize ederler.',
        icon: Droplets,
        color: 'from-slate-400 to-slate-600'
      },
      {
        title: 'Bahçıvanın Alet Çantası',
        subtitle: 'Laboratuvar Ekipmanları',
        content: 'Havan, terazi, mikroskop ve damıtma hunileri eczacılığın kalbidir. Ölçüm ve gözlem ilacın doğruluğunu sağlar.',
        icon: Microscope,
        color: 'from-amber-500 to-brown-700'
      },
      {
        title: 'Büyük Sentez',
        subtitle: 'Ormanın Gizli Formülü',
        content: 'Biyoloji doğanın potansiyelini sunar, Kimya araçları sağlar, Farmakoloji ise bunu insan sağlığı için hedefe yönlendirir. Bilimle kalın!',
        icon: Target,
        color: 'from-indigo-600 to-violet-800'
      }
    ],
    en: [
      {
        title: 'The Family Tree of Drugs',
        subtitle: 'Discovering 256 Magic Secrets',
        content: "Hello Young Scientists! Are you ready to discover how nature's healing secrets turn into medicine in the chemistry lab? We're climbing from roots to branches!",
        icon: TreePine,
        color: 'from-emerald-500 to-teal-700'
      },
      {
        title: 'What is the ATC System?',
        subtitle: 'The Coordinate Map of Drugs',
        content: 'Each branch represents a system in our body (A: Digestive, N: Nervous, R: Respiratory, etc.). Leaves and fruits are the pharmaceutical materials that affect those systems.',
        icon: MapIcon,
        color: 'from-amber-400 to-orange-600'
      },
      {
        title: 'Human Body and the Drug Tree',
        subtitle: 'Comparison Matrix',
        content: 'Drugs are designed to find only their specific targets in our body! That is why skin cream (D) and breath openers (R) are on different branches.',
        icon: User,
        color: 'from-blue-400 to-indigo-600'
      },
      {
        title: 'Branch A: Digestive System',
        subtitle: 'Our Energy Plant',
        content: 'Laxatives, Vitamins, Minerals, and Digestive Aids are found on this branch. Vitamin C is the knight of our immunity!',
        icon: Apple,
        color: 'from-lime-400 to-green-600'
      },
      {
        title: 'Branches B & C: Blood & Heart',
        subtitle: 'The Life Pump',
        content: 'Iron preparations give our blood its red color. Cardiac therapy strengthens our hearts. Squill is a historical plant that increases the contraction power of the heart.',
        icon: Heart,
        color: 'from-rose-500 to-red-700'
      },
      {
        title: 'Branch D: Dermatology',
        subtitle: 'Our Protective Shield',
        content: 'Emollients, carrier bases, and antiseptic ointments are in this branch. Zinc Oxide is the main hero of sunscreens!',
        icon: ShieldCheck,
        color: 'from-orange-300 to-amber-500'
      },
      {
        title: 'Branch N: Nervous System (1)',
        subtitle: 'Pain Management and Anesthesia',
        content: 'Analgesics relieve pain, while anesthetics numb. Diethyl Ether is one of the oldest chemicals used to prevent pain during surgery.',
        icon: Brain,
        color: 'from-indigo-400 to-violet-600'
      },
      {
        title: 'Branch N: Nervous System (2)',
        subtitle: 'Tranquilizers and Stimulants',
        content: 'Psycholeptics (Gears Down) and Stimulants (Gears Up) are located here. Valerian has been the friend of those suffering from insomnia for centuries.',
        icon: Activity,
        color: 'from-purple-400 to-fuchsia-600'
      },
      {
        title: 'Branch R: Respiratory System',
        subtitle: 'Breath-Freshening Winds',
        content: 'Expectorants are like brooms that clean the lungs. Aromatic extracts provide freshness.',
        icon: Wind,
        color: 'from-cyan-400 to-blue-500'
      },
      {
        title: 'The Defense Forces',
        subtitle: 'Branches P, J, G, and M',
        content: 'Antiparasitics and antimicrobials protect our bodies. Therapeutic botanicals are the raw treasures of nature.',
        icon: ShieldHalf,
        color: 'from-emerald-600 to-teal-800'
      },
      {
        title: 'How is a Drug Born?',
        subtitle: 'The Extraction Process',
        content: 'Biology (Natural Source) + Chemistry (Solvent and Process) = Pharmacology (Final Product). We call the process of purifying substances in nature "Extraction".',
        icon: FlaskConical,
        color: 'from-sky-400 to-blue-600'
      },
      {
        title: 'Category V: Chemicals',
        subtitle: 'Extractors and Chemical Bases',
        content: 'Acids, alcohols, solvents, and indicators are the nutrients of the tree. They initiate reactions, dissolve active ingredients, and stabilize drugs.',
        icon: Droplets,
        color: 'from-slate-400 to-slate-600'
      },
      {
        title: 'The Gardener\'s Tool Kit',
        subtitle: 'Laboratory Equipment',
        content: 'Mortars, scales, microscopes, and distillation funnels are the heart of pharmacy. Measurement and observation ensure the accuracy of the medicine.',
        icon: Microscope,
        color: 'from-amber-500 to-brown-700'
      },
      {
        title: 'The Grand Synthesis',
        subtitle: 'The Forest\'s Secret Formula',
        content: 'Biology offers the potential of nature, Chemistry provides the tools, and Pharmacology directs it towards the target for human health. Stay with science!',
        icon: Target,
        color: 'from-indigo-600 to-violet-800'
      }
    ]
  }[lang];

  const nextSlide = () => setCurrentSlide((prev) => (prev + 1) % slides.length);
  const prevSlide = () => setCurrentSlide((prev) => (prev - 1 + slides.length) % slides.length);

  const SlideIcon = slides[currentSlide].icon;

  return (
    <div className="max-w-5xl mx-auto space-y-8 py-4">
      <div className="relative aspect-[16/9] bg-white dark:bg-slate-800 rounded-[3rem] shadow-2xl overflow-hidden flex flex-col items-center justify-end p-0 border-8 border-slate-100 dark:border-slate-700 group">
        <AnimatePresence mode="wait">
          <motion.div
            key={currentSlide}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className={`absolute inset-0 z-0 bg-gradient-to-br ${slides[currentSlide].color} opacity-5 dark:opacity-20`}
          />
        </AnimatePresence>

        <div className="absolute inset-0 flex items-center justify-center pointer-events-none overflow-hidden">
          <AnimatePresence mode="wait">
            <motion.div
              key={currentSlide}
              initial={{ scale: 0.8, opacity: 0, rotate: -10 }}
              animate={{ scale: 1, opacity: 0.1, rotate: 0 }}
              exit={{ scale: 1.2, opacity: 0, rotate: 10 }}
              transition={{ duration: 0.8 }}
              className="absolute"
            >
              <SlideIcon size={400} strokeWidth={1} />
            </motion.div>
          </AnimatePresence>
        </div>

        <div className="relative z-10 w-full h-full flex flex-col items-center justify-center p-12 md:p-20 text-center">
          <AnimatePresence mode="wait">
            <motion.div
              key={currentSlide}
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              exit={{ y: -20, opacity: 0 }}
              transition={{ delay: 0.1 }}
              className="space-y-6"
            >
              <div className="inline-flex items-center gap-3 px-4 py-2 rounded-full bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 text-[10px] font-black tracking-[0.3em] uppercase mb-4 shadow-sm border border-indigo-100 dark:border-indigo-800/50">
                <SlideIcon size={14} />
                {slides[currentSlide].subtitle}
              </div>
              <h2 className="text-4xl md:text-6xl font-black mb-8 leading-[1.1] tracking-tight max-w-4xl mx-auto text-slate-900 dark:text-white drop-shadow-sm">
                {slides[currentSlide].title}
              </h2>
              <div className="w-24 h-1.5 bg-gradient-to-r from-indigo-500 to-violet-500 mx-auto mb-10 rounded-full shadow-lg shadow-indigo-100 dark:shadow-none"></div>
              <p className="text-xl md:text-3xl leading-relaxed text-slate-600 dark:text-slate-300 font-medium max-w-4xl mx-auto italic">
                "{slides[currentSlide].content}"
              </p>
            </motion.div>
          </AnimatePresence>
        </div>

        {/* Controls */}
        <div className="absolute inset-y-0 left-0 right-0 flex items-center justify-between px-6 pointer-events-none z-20">
          <button 
            onClick={prevSlide}
            className="pointer-events-auto p-4 rounded-full bg-white/80 dark:bg-slate-800/80 backdrop-blur-md hover:bg-white dark:hover:bg-slate-700 shadow-xl transition-all hover:scale-110 active:scale-95 border border-slate-200 dark:border-slate-700 group/btn"
          >
            <ChevronLeft size={32} className="text-slate-600 dark:text-slate-300 group-hover/btn:text-indigo-500 transition-colors" />
          </button>
          <button 
            onClick={nextSlide}
            className="pointer-events-auto p-4 rounded-full bg-white/80 dark:bg-slate-800/80 backdrop-blur-md hover:bg-white dark:hover:bg-slate-700 shadow-xl transition-all hover:scale-110 active:scale-95 border border-slate-200 dark:border-slate-700 group/btn"
          >
            <ChevronRight size={32} className="text-slate-600 dark:text-slate-300 group-hover/btn:text-indigo-500 transition-colors" />
          </button>
        </div>

        {/* Progress Bar */}
        <div className="absolute bottom-0 left-0 right-0 h-2 bg-slate-100 dark:bg-slate-700 overflow-hidden">
          <motion.div 
            initial={false}
            animate={{ width: `${((currentSlide + 1) / slides.length) * 100}%` }}
            className="h-full bg-gradient-to-r from-indigo-500 to-violet-600 shadow-[0_0_15px_rgba(99,102,241,0.5)]"
          />
        </div>

        {/* Slide Counter */}
        <div className="absolute top-8 left-1/2 -translate-x-1/2 px-4 py-1 rounded-full bg-slate-900/5 dark:bg-white/5 backdrop-blur-sm text-[10px] font-black text-slate-400 dark:text-slate-500">
          {currentSlide + 1} / {slides.length}
        </div>
      </div>
    </div>
  );
}
