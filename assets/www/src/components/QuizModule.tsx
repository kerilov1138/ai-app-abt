import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Trophy, RefreshCcw, CheckCircle2, XCircle, Brain } from 'lucide-react';
import { DRUGS, ATC_CATEGORIES, ATCCategory, Drug } from '../data/atcData';
import confetti from 'canvas-confetti';

interface QuizModuleProps {
  lang: 'tr' | 'en';
}

interface Question {
  drug: Drug;
  type: 'multiple' | 'binary';
  options: ATCCategory[];
  correctId: string;
  textTr: string;
  textEn: string;
}

export default function QuizModule({ lang }: QuizModuleProps) {
  const [score, setScore] = useState(0);
  const [totalQuestions, setTotalQuestions] = useState(0);
  const [question, setQuestion] = useState<Question | null>(null);
  const [selectedOption, setSelectedOption] = useState<string | null>(null);
  const [isCorrect, setIsCorrect] = useState<boolean | null>(null);

  const generateQuestion = () => {
    const randomDrug = DRUGS[Math.floor(Math.random() * DRUGS.length)];
    const correctCat = ATC_CATEGORIES.find(c => c.id === randomDrug.atc)!;
    
    // Choose between "Pick category" (4 options) and "Is it in this category?" (Yes/No)
    const quizType = Math.random() > 0.5 ? 'multiple' : 'binary';

    if (quizType === 'multiple') {
      const otherCats = ATC_CATEGORIES.filter(c => c.id !== randomDrug.atc)
        .sort(() => 0.5 - Math.random())
        .slice(0, 3);
      const options = [correctCat, ...otherCats].sort(() => 0.5 - Math.random());
      
      setQuestion({
        drug: randomDrug,
        type: 'multiple',
        options,
        correctId: correctCat.id,
        textTr: `${randomDrug.name} hangi ATC kategorisine aittir?`,
        textEn: `Which ATC category does ${randomDrug.name} belong to?`
      });
    } else {
      // Pick a category (sometimes the correct one, sometimes not)
      const isTrue = Math.random() > 0.5;
      const displayCat = isTrue 
        ? correctCat 
        : ATC_CATEGORIES.filter(c => c.id !== randomDrug.atc)[Math.floor(Math.random() * (ATC_CATEGORIES.length - 1))];

      setQuestion({
        drug: randomDrug,
        type: 'binary',
        options: [
          { id: 'true', nameTr: 'Evet, Doğru', nameEn: 'Yes, Correct' } as any,
          { id: 'false', nameTr: 'Hayır, Yanlış', nameEn: 'No, Wrong' } as any
        ],
        correctId: isTrue ? 'true' : 'false',
        textTr: `${randomDrug.name} materyali "${lang === 'tr' ? displayCat.nameTr : displayCat.nameEn}" (${displayCat.id}) kategorisine mi aittir?`,
        textEn: `Does ${randomDrug.name} belong to "${lang === 'tr' ? displayCat.nameTr : displayCat.nameEn}" (${displayCat.id})?`
      });
    }

    setSelectedOption(null);
    setIsCorrect(null);
  };

  useEffect(() => {
    generateQuestion();
  }, []);

  const handleAnswer = (optionId: string) => {
    if (selectedOption) return;
    
    setSelectedOption(optionId);
    const correct = optionId === question?.correctId;
    setIsCorrect(correct);
    setTotalQuestions(prev => prev + 1);
    
    if (correct) {
      setScore(prev => prev + 100);
      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 },
        colors: ['#4f46e5', '#10b981', '#f59e0b']
      });
    }
  };

  const nextQuestion = () => {
    generateQuestion();
  };

  if (!question) return null;

  return (
    <div className="max-w-3xl mx-auto py-8">
      <div className="flex flex-col md:flex-row items-center justify-between gap-8 mb-12">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl">
            <Trophy size={32} />
          </div>
          <div>
            <h2 className="text-3xl font-black tracking-tight">{lang === 'tr' ? 'Farmakoloji Yarışması' : 'Pharmacology Quiz'}</h2>
            <p className="opacity-60 font-bold">{lang === 'tr' ? 'ATC Bilgini Kanıtla!' : 'Prove Your ATC Knowledge!'}</p>
          </div>
        </div>
        
        <div className="bg-white dark:bg-slate-800 px-8 py-4 rounded-3xl shadow-xl flex flex-col items-center">
            <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest leading-none mb-1">{lang === 'tr' ? 'TOPLAM PUAN' : 'TOTAL SCORE'}</span>
            <span className="text-3xl font-black text-indigo-600">{score}</span>
        </div>
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={question.drug.sn}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          className="bg-white dark:bg-slate-800 rounded-[3rem] p-10 shadow-2xl border border-slate-100 dark:border-slate-700 relative overflow-hidden"
        >
          <div className="absolute top-0 right-0 p-8 opacity-5">
            <Brain size={120} />
          </div>

          <div className="text-center mb-10">
            <div className="inline-block px-4 py-1 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 rounded-full text-xs font-black mb-4">
              {lang === 'tr' ? 'SORU' : 'QUESTION'} #{totalQuestions + 1}
            </div>
            <h3 className="text-2xl font-bold mb-2">
              {lang === 'tr' ? question.textTr : question.textEn}
            </h3>
            <div className="text-5xl font-black text-indigo-600 mt-6 tracking-tight">
               {question.drug.name}
            </div>
            <div className="text-sm font-mono mt-2 opacity-40">S.N: {question.drug.sn}</div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {question.options.map((opt) => {
              const isSelected = selectedOption === opt.id;
              const isOptionCorrect = opt.id === question.correctId;
              
              let classes = "p-5 rounded-2xl border-2 font-bold text-left transition-all flex items-center justify-between ";
              
              if (!selectedOption) {
                classes += "bg-slate-50 dark:bg-slate-700/50 border-transparent hover:border-indigo-500 hover:bg-white dark:hover:bg-slate-700 shadow-sm";
              } else {
                if (isOptionCorrect) {
                  classes += "bg-emerald-500 border-emerald-500 text-white shadow-lg shadow-emerald-200 dark:shadow-none";
                } else if (isSelected) {
                  classes += "bg-rose-500 border-rose-500 text-white shadow-lg shadow-rose-200 dark:shadow-none";
                } else {
                  classes += "bg-slate-100 dark:bg-slate-800 border-transparent opacity-40";
                }
              }

              return (
                <button
                  key={opt.id}
                  disabled={!!selectedOption}
                  onClick={() => handleAnswer(opt.id)}
                  className={classes}
                >
                  <div className="flex items-center gap-3">
                    <span className="w-8 h-8 flex items-center justify-center rounded-lg bg-black/10 text-xs font-black">{opt.id}</span>
                    <span>{lang === 'tr' ? opt.nameTr : opt.nameEn}</span>
                  </div>
                  {selectedOption && isOptionCorrect && <CheckCircle2 size={24} />}
                  {selectedOption && isSelected && !isOptionCorrect && <XCircle size={24} />}
                </button>
              );
            })}
          </div>

          {selectedOption && (
            <motion.div 
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mt-10 flex flex-col items-center gap-4 animate-in fade-in slide-in-from-bottom-4"
            >
              <div className={`text-xl font-black ${isCorrect ? 'text-emerald-500' : 'text-rose-500'}`}>
                {isCorrect 
                  ? (lang === 'tr' ? 'Harika! Doğru Cevap. 🎉' : 'Awesome! Correct Answer. 🎉')
                  : (lang === 'tr' ? 'Maalesef Yanlış. Tekrar Dene!' : 'Unfortunately Wrong. Try Again!')}
              </div>
              <button
                onClick={nextQuestion}
                className="flex items-center gap-2 px-8 py-4 bg-indigo-600 text-white rounded-2xl font-black shadow-xl hover:bg-indigo-700 active:scale-95 transition-all"
              >
                <RefreshCcw size={20} />
                {lang === 'tr' ? 'SIRADAKİ SORU' : 'NEXT QUESTION'}
              </button>
            </motion.div>
          )}
        </motion.div>
      </AnimatePresence>

      <div className="mt-8 grid grid-cols-2 gap-4">
         <div className="bg-white dark:bg-slate-800 p-6 rounded-3xl flex items-center gap-4 border border-slate-100 dark:border-slate-700">
            <div className="w-12 h-12 bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 rounded-xl flex items-center justify-center">
              <CheckCircle2 size={24} />
            </div>
            <div>
              <div className="text-2xl font-black">{Math.round((score / (totalQuestions * 100 || 1)) * 100)}%</div>
              <div className="text-[10px] font-black opacity-40 uppercase tracking-widest">{lang === 'tr' ? 'BAŞARI ORANI' : 'SUCCESS RATE'}</div>
            </div>
         </div>
         <div className="bg-white dark:bg-slate-800 p-6 rounded-3xl flex items-center gap-4 border border-slate-100 dark:border-slate-700">
            <div className="w-12 h-12 bg-blue-100 dark:bg-blue-900/30 text-blue-600 rounded-xl flex items-center justify-center">
              <Brain size={24} />
            </div>
            <div>
              <div className="text-2xl font-black">{totalQuestions}</div>
              <div className="text-[10px] font-black opacity-40 uppercase tracking-widest">{lang === 'tr' ? 'ÇÖZÜLEN SORU' : 'SOLVED QUESTIONS'}</div>
            </div>
         </div>
      </div>
    </div>
  );
}
