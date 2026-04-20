import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Timer, Trophy, HelpCircle, Play, RotateCcw, X, Info, Users } from 'lucide-react';
import { Question, GameState } from '../types';
import { QUESTIONS_POOL } from '../constants';
import { generateHostResponse, speakText, playRevealSound } from '../services/aiService';

/**
 * ANDROID / JAVA COMPATIBILITY NOTE:
 * This application is built as a Progressive Web App (PWA).
 * It is fully compatible with Android WebViews (Android 5.0+).
 * For native Android integration (Java 20 / Kotlin), this app can be wrapped 
 * using tools like Capacitor, Cordova, or a custom WebView Activity.
 * The UI is designed using Flutter-inspired responsive principles.
 * 
 * BUILD SETTINGS:
 * - minifyEnabled: false (to prevent R8/ProGuard from stripping essential classes)
 * - targetSdk: 34 (Android 14)
 * - minSdk: 21 (Android 5.0)
 */

const INITIAL_TIME = 240; // 4 minutes
const ANSWER_TIME = 5; // 5 seconds to answer after stopping

export default function GameBoard() {
  const [gameQuestions, setGameQuestions] = useState<Question[]>([]);
  const [showInstructions, setShowInstructions] = useState(false);
  const [showCredits, setShowCredits] = useState(false);
  const [manualAnswer, setManualAnswer] = useState('');
  const [isQuestionIntroActive, setIsQuestionIntroActive] = useState(false);
  const manualInputRef = useRef<HTMLInputElement>(null);
  
  const [gameState, setGameState] = useState<GameState>({
    currentQuestionIndex: 0,
    score: 0,
    timeLeft: INITIAL_TIME,
    revealedLetters: [],
    isTimerRunning: false,
    isGameOver: false,
    isAnswering: false,
    isAnswerTimerRunning: false,
    answerTimeLeft: ANSWER_TIME,
    totalScore: 0,
  });

  const [highScore, setHighScore] = useState<number>(() => {
    const saved = localStorage.getItem('kelime_oyunu_high_score');
    return saved ? parseInt(saved, 10) : 0;
  });

  const gameStateRef = useRef(gameState);
  useEffect(() => {
    gameStateRef.current = gameState;
  }, [gameState]);

  const [gameStarted, setGameStarted] = useState(false);
  const gameStartedRef = useRef(gameStarted);
  useEffect(() => {
    gameStartedRef.current = gameStarted;
  }, [gameStarted]);

  const [feedback, setFeedback] = useState<string | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const answerTimerRef = useRef<NodeJS.Timeout | null>(null);

  const currentQuestion = gameQuestions[gameState.currentQuestionIndex];

  const [isHostSpeaking, setIsHostSpeaking] = useState(false);
  const isHostSpeakingRef = useRef(isHostSpeaking);
  useEffect(() => {
    isHostSpeakingRef.current = isHostSpeaking;
  }, [isHostSpeaking]);

  const speak = async (text: string, pauseTimer: boolean = false) => {
    const wasTimerRunning = gameStateRef.current.isTimerRunning;
    
    if (pauseTimer && wasTimerRunning) {
      setGameState(prev => ({ ...prev, isTimerRunning: false }));
    }
    
    setIsHostSpeaking(true);
    await speakText(text);
    setIsHostSpeaking(false);
    
    // Check if we should resume timer - but ONLY if we aren't in a special intro phase
    if (pauseTimer && wasTimerRunning && !gameStateRef.current.isGameOver && !gameStateRef.current.isAnswering && !isQuestionIntroActive) {
      setGameState(prev => ({ ...prev, isTimerRunning: true }));
    }
  };

  useEffect(() => {
    const triggerQuestion = async () => {
      if (gameStarted && !gameState.isGameOver && !gameState.isAnswering && currentQuestion) {
        // 1. Ensure timer is paused during intro
        setGameState(prev => ({ ...prev, isTimerRunning: false }));
        setIsQuestionIntroActive(true);
        
        // 2. Host speaks the definition
        await speak(currentQuestion.definition, false);
        
        // 3. Intro finished: show UI and start timer
        setIsQuestionIntroActive(false);
        setGameState(prev => ({ ...prev, isTimerRunning: true }));
      }
    };
    triggerQuestion();
  }, [gameState.currentQuestionIndex, gameStarted, !!currentQuestion]);

  const handleGameOver = useCallback(async () => {
    setGameState(prev => ({ ...prev, isGameOver: true, isTimerRunning: false }));
    const response = await generateHostResponse('welcome'); // Or a generic game over response
    await speak("Yarışma sona erdi! Toplam puanınız: " + gameStateRef.current.totalScore + ". Tebrikler!");
    confetti({
      particleCount: 150,
      spread: 70,
      origin: { y: 0.6 }
    });
  }, []);

  useEffect(() => {
    if (gameState.isTimerRunning && gameState.timeLeft > 0) {
      timerRef.current = setInterval(() => {
        setGameState(prev => {
          if (prev.timeLeft <= 1) {
            handleGameOver();
            return { ...prev, timeLeft: 0, isTimerRunning: false };
          }
          return { ...prev, timeLeft: prev.timeLeft - 1 };
        });
      }, 1000);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [gameState.isTimerRunning, gameState.timeLeft, handleGameOver]);

  useEffect(() => {
    if (gameState.isAnswering && gameState.isAnswerTimerRunning && gameState.answerTimeLeft > 0) {
      answerTimerRef.current = setInterval(() => {
        setGameState(prev => {
          if (prev.answerTimeLeft <= 1) {
            handleAnswer(""); 
            return { ...prev, answerTimeLeft: 0, isAnswering: false, isAnswerTimerRunning: false };
          }
          return { ...prev, answerTimeLeft: prev.answerTimeLeft - 1 };
        });
      }, 1000);
    } else {
      if (answerTimerRef.current) clearInterval(answerTimerRef.current);
    }
    return () => {
      if (answerTimerRef.current) clearInterval(answerTimerRef.current);
    };
  }, [gameState.isAnswering, gameState.isAnswerTimerRunning, gameState.answerTimeLeft]);

  const handleHarfLutfen = useCallback(async () => {
    const state = gameStateRef.current;
    if (state.isGameOver || state.isAnswering || !state.isTimerRunning) return;

    const question = gameQuestions[state.currentQuestionIndex];
    const word = question.word;
    const availableIndices = Array.from({ length: word.length }, (_, i) => i)
      .filter(i => !state.revealedLetters.includes(i));

    if (availableIndices.length > 1) {
      const randomIndex = availableIndices[Math.floor(Math.random() * availableIndices.length)];
      playRevealSound();
      setGameState(prev => ({
        ...prev,
        revealedLetters: [...prev.revealedLetters, randomIndex]
      }));
      const response = await generateHostResponse('letter');
      await speak(response);
    } else {
      await speak("Son harf kaldı, artık cevaplamanız gerekiyor.");
    }
  }, [gameQuestions]);

  const handleStop = useCallback(async () => {
    const state = gameStateRef.current;
    if (state.isGameOver || state.isAnswering || !state.isTimerRunning) return;
    
    setGameState(prev => ({ 
      ...prev, 
      isTimerRunning: false, 
      isAnswering: true, 
      isAnswerTimerRunning: false,
      answerTimeLeft: ANSWER_TIME 
    }));
    
    await speak("Bekliyoruz", false);
    setGameState(prev => ({ ...prev, isAnswerTimerRunning: true }));
  }, []);

  const handleAnswer = useCallback(async (answer: string) => {
    const state = gameStateRef.current;
    if (!state.isAnswering) return;

    setGameState(prev => ({ 
      ...prev, 
      isAnswering: false, 
      isAnswerTimerRunning: false 
    }));

    const question = gameQuestions[state.currentQuestionIndex];
    if (!question) return;
    
    const normalizeForComparison = (str: string) => {
      // Turkish phonetic mapping: Spoken Turkish vs Written Turkish
      // We convert everything to base letters to handle STT variations.
      return str.toUpperCase()
        .replace(/I/g, 'I')
        .replace(/İ/g, 'I')
        .replace(/Ğ/g, 'G')
        .replace(/Ü/g, 'U')
        .replace(/Ş/g, 'S')
        .replace(/Ö/g, 'O')
        .replace(/Ç/g, 'C')
        .replace(/[.,/#!$%^&*;:{}=\-_`~()]/g, "") // Remove punctuation
        .replace(/\s+/g, "") // Remove all spaces for exact word matching
        .trim();
    };

    const normalizedAnswer = normalizeForComparison(answer);
    const normalizedWord = normalizeForComparison(question.word);

    const isCorrect = normalizedAnswer === normalizedWord;
    const wordPoints = (question.word.length - state.revealedLetters.length) * 100;

    if (isCorrect) {
      setFeedback("Doğru!");
      const allIndices = Array.from({ length: question.word.length }, (_, i) => i);
      for (const index of allIndices) {
        if (!gameStateRef.current.revealedLetters.includes(index)) {
          playRevealSound();
          setGameState(prev => ({
            ...prev,
            revealedLetters: [...prev.revealedLetters, index]
          }));
          await new Promise(resolve => setTimeout(resolve, 150));
        }
      }

      const response = await generateHostResponse('correct');
      await speak(response);
       
      setGameState(prev => {
        const newScore = prev.totalScore + wordPoints;
        if (newScore > highScore) {
          setHighScore(newScore);
          localStorage.setItem('kelime_oyunu_high_score', newScore.toString());
        }
        return {
          ...prev,
          totalScore: newScore,
          isTimerRunning: true,
          currentQuestionIndex: prev.currentQuestionIndex + 1,
          revealedLetters: [],
        };
      });
      
      if (state.currentQuestionIndex + 1 >= gameQuestions.length) {
        handleGameOver();
      }
    } else {
      setFeedback("Yanlış!");
      const allIndices = Array.from({ length: question.word.length }, (_, i) => i);
      setGameState(prev => ({ ...prev, revealedLetters: allIndices }));
      
      if (answer === "") {
        await speak(`Süreniz doldu. Doğru cevap ${question.word} olacaktı.`);
      } else {
        const response = await generateHostResponse('wrong');
        await speak(`${response} Doğru cevap ${question.word} olacaktı.`);
      }
      
      setGameState(prev => ({
        ...prev,
        totalScore: prev.totalScore - wordPoints,
        isTimerRunning: true,
        currentQuestionIndex: prev.currentQuestionIndex + 1,
        revealedLetters: [],
      }));
      
      if (state.currentQuestionIndex + 1 >= gameQuestions.length) {
        handleGameOver();
      }
    }

    setTimeout(() => setFeedback(null), 2000);
  }, [handleGameOver, gameQuestions]);

  const startGame = async () => {
    const fullPool = [...QUESTIONS_POOL];
    const selected: Question[] = [];
    for (let len = 4; len <= 10; len++) {
      const pool = fullPool.filter(q => q.length === len);
      const shuffled = [...pool].sort(() => Math.random() - 0.5);
      selected.push(...shuffled.slice(0, 2));
    }
    setGameQuestions(selected);
    setGameStarted(true);
    setManualAnswer('');
    
    setGameState(prev => ({
      ...prev,
      currentQuestionIndex: 0,
      timeLeft: INITIAL_TIME,
      revealedLetters: [],
      isTimerRunning: false,
      isGameOver: false,
      isAnswering: false,
      isAnswerTimerRunning: false,
      answerTimeLeft: ANSWER_TIME,
      totalScore: 0,
    }));

    const response = await generateHostResponse('welcome');
    await speak(response);
  };

  const resetGame = () => {
    setGameState({
      currentQuestionIndex: 0,
      score: 0,
      timeLeft: INITIAL_TIME,
      revealedLetters: [],
      isTimerRunning: false,
      isGameOver: false,
      isAnswering: false,
      isAnswerTimerRunning: false,
      answerTimeLeft: ANSWER_TIME,
      totalScore: 0,
    });
    setGameStarted(false);
    setManualAnswer('');
  };

  useEffect(() => {
    if (gameState.isAnswering && manualInputRef.current) {
      manualInputRef.current.focus();
    }
  }, [gameState.isAnswering]);

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (manualAnswer.trim()) {
      handleAnswer(manualAnswer);
      setManualAnswer('');
    }
  };

  return (
    <div className="flex flex-col h-screen bg-[#0a0a20] text-white p-2 md:p-4 font-sans transition-standard transform-gpu overflow-hidden">
      {!gameStarted ? (
        <div className="flex flex-col items-center justify-center flex-1 p-4 overflow-y-auto">
          <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center space-y-4 md:space-y-8 max-w-2xl"
          >
            <h1 className="text-3xl md:text-7xl font-black tracking-tighter text-blue-400 drop-shadow-2xl">KELİME OYUNU</h1>
            <p className="text-sm md:text-2xl text-gray-400 px-4 max-w-xl mx-auto leading-relaxed">
              Efsane yarışma Kelime Oyunu'na hoş geldiniz! 
              Verilen tanımlara göre en kısa sürede kelimeleri bulmaya çalışın.
            </p>
            <div className="pt-4 flex justify-center">
              <button 
                onClick={() => startGame()}
                className="group relative px-8 md:px-16 py-4 md:py-8 bg-blue-600 hover:bg-blue-500 rounded-[1.5rem] md:rounded-[2rem] text-lg md:text-3xl font-black transition-all hover:scale-105 active:scale-95 shadow-[0_10px_30px_rgba(37,99,235,0.4)] border-b-4 md:border-b-8 border-blue-800"
              >
                <span className="flex items-center gap-3 md:gap-4 justify-center">
                  <Play size={20} className="md:w-10 md:h-10 fill-current" /> YARIŞMAYI BAŞLAT
                </span>
              </button>
            </div>

            <div className="flex flex-wrap gap-3 md:gap-6 mt-4 justify-center">
              <button 
                onClick={() => setShowInstructions(true)}
                className="flex items-center gap-2 text-blue-400 hover:text-blue-300 transition-colors font-medium text-xs md:text-base"
              >
                <Info size={16} /> Nasıl Oynanır?
              </button>
              <button 
                onClick={() => setShowCredits(true)}
                className="flex items-center gap-2 text-blue-400 hover:text-blue-300 transition-colors font-medium text-xs md:text-base"
              >
                <Users size={16} /> Yapımcılar
              </button>
            </div>
          </motion.div>
        </div>
      ) : (
        <div className="flex flex-col h-full overflow-hidden">
          {/* Header - Compact */}
          <div className="flex justify-between items-center gap-2 mb-2 md:mb-6 shrink-0">
            <div className="flex items-center gap-2 md:gap-4">
              <div className="bg-blue-900/40 border border-blue-500/30 p-2 md:p-4 rounded-2xl backdrop-blur-xl min-w-[60px] md:min-w-[100px] shadow-lg">
                <div className="flex items-center gap-1 text-blue-400 mb-0.5">
                  <Trophy size={10} className="md:w-3 md:h-3" />
                  <span className="text-[6px] md:text-[8px] font-black uppercase tracking-widest">En Yüksek</span>
                </div>
                <div className="text-sm md:text-xl font-mono font-black text-blue-300 leading-none">{highScore}</div>
              </div>

              <div className="bg-blue-900/40 border border-blue-500/30 p-2 md:p-4 rounded-2xl backdrop-blur-xl min-w-[80px] md:min-w-[120px] shadow-lg">
                <div className="flex items-center gap-1 text-blue-400 mb-0.5">
                  <Trophy size={12} className="md:w-4 md:h-4" />
                  <span className="text-[8px] md:text-[10px] font-black uppercase tracking-widest">Puan</span>
                </div>
                <div className="text-xl md:text-3xl font-mono font-black text-white leading-none">{gameState.totalScore}</div>
              </div>
              
              <div className={`p-2 md:p-4 rounded-2xl border transition-all duration-500 min-w-[80px] md:min-w-[120px] shadow-lg ${gameState.timeLeft < 30 ? 'bg-red-900/40 border-red-500/50 animate-pulse' : 'bg-blue-900/40 border-blue-500/30'}`}>
                <div className="flex items-center gap-1 text-blue-400 mb-0.5">
                  <Timer size={12} className="md:w-4 md:h-4" />
                  <span className="text-[8px] md:text-[10px] font-black uppercase tracking-widest">Süre</span>
                </div>
                <div className="text-xl md:text-3xl font-mono font-black text-white leading-none">
                  {Math.floor(gameState.timeLeft / 60)}:{(gameState.timeLeft % 60).toString().padStart(2, '0')}
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button onClick={resetGame} className="p-1.5 md:p-2 hover:bg-white/10 rounded-full transition-colors">
                <RotateCcw size={16} className="md:w-5 md:h-5" />
              </button>
            </div>
          </div>

          {/* Main Game Area - Flexible */}
          <div className="flex-1 flex flex-col items-center justify-center max-w-5xl mx-auto w-full overflow-hidden">
            <AnimatePresence mode="wait">
              {!gameState.isGameOver ? (
                <motion.div 
                  key={gameState.currentQuestionIndex}
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 1.05 }}
                  className="w-full flex flex-col items-center justify-center space-y-4 md:space-y-8 h-full"
                >
                  <AnimatePresence>
                    {!isQuestionIntroActive && (
                      <motion.div 
                        initial={{ opacity: 0, scale: 0.5, rotateX: -90 }}
                        animate={{ opacity: 1, scale: 1, rotateX: 0 }}
                        transition={{ duration: 0.6, type: "spring", bounce: 0.4 }}
                        className="honeycomb-grid flex-wrap max-h-[40%] overflow-y-auto py-2"
                      >
                        {currentQuestion && currentQuestion.word.split('').map((char, index) => {
                          const isRevealed = gameState.revealedLetters.includes(index) || gameState.isGameOver;
                          const wordLength = currentQuestion.word.length;
                          const sizeClass = wordLength > 9 ? 'w-7 h-8 md:w-14 md:h-16' : 
                                           wordLength > 7 ? 'w-9 h-10 md:w-18 md:h-22' : 
                                           'w-11 h-13 md:w-22 md:h-26';

                          return (
                            <div key={`${gameState.currentQuestionIndex}-${index}`} className={`hexagon-container ${sizeClass} transform-gpu`}>
                              <div className="hexagon-inner w-full h-full">
                                <motion.div
                                  initial={false}
                                  animate={{ 
                                    rotateY: isRevealed ? 0 : 90,
                                    scale: isRevealed ? [1, 1.1, 1] : 1
                                  }}
                                  transition={{ 
                                    rotateY: { duration: 0.4, ease: "easeOut" },
                                    scale: { duration: 0.3, times: [0, 0.5, 1] }
                                  }}
                                  className="absolute inset-0 z-10"
                                >
                                  <div className="hexagon w-full h-full bg-gradient-to-br from-yellow-300 via-yellow-500 to-yellow-700 flex items-center justify-center shadow-lg border-t border-yellow-200/50">
                                    <span className="text-lg md:text-4xl font-black text-blue-950 drop-shadow-sm">
                                      {char}
                                    </span>
                                  </div>
                                </motion.div>

                                <motion.div
                                  initial={false}
                                  animate={{ rotateY: isRevealed ? -90 : 0 }}
                                  transition={{ duration: 0.4, ease: "easeOut" }}
                                  className="absolute inset-0"
                                >
                                  <div className="hexagon w-full h-full bg-slate-800/90 border border-slate-700/50 flex items-center justify-center overflow-hidden">
                                    <div className="hexagon w-[85%] h-[85%] bg-slate-900/80 shadow-inner flex items-center justify-center">
                                       <div className="w-full h-full bg-[radial-gradient(circle_at_center,rgba(59,130,246,0.1)_0%,transparent_70%)]" />
                                    </div>
                                  </div>
                                </motion.div>
                              </div>
                            </div>
                          );
                        })}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  <AnimatePresence>
                    {!isQuestionIntroActive && (
                      <motion.div 
                        initial={{ opacity: 0, y: 30, rotateX: 45 }}
                        animate={{ opacity: 1, y: 0, rotateX: 0 }}
                        transition={{ delay: 0.3, duration: 0.5 }}
                        className="relative w-full max-h-[50%] overflow-y-auto shrink-0"
                      >
                        <div className={`bg-slate-900/60 border p-4 md:p-8 rounded-2xl md:rounded-3xl backdrop-blur-md shadow-2xl transition-standard transform-gpu ${gameState.isAnswering ? 'border-blue-500 ring-2 ring-blue-500/20' : 'border-slate-700/50'}`}>
                          <div className="flex justify-between items-start mb-2 md:mb-4">
                            <div className="flex items-center gap-2 text-blue-400">
                              <HelpCircle size={16} className="md:w-5 md:h-5" />
                              <span className="text-[8px] md:text-xs font-bold uppercase tracking-[0.2em]">Soru</span>
                            </div>
                            {gameState.isAnswering && (
                              <motion.div 
                                initial={{ scale: 0.8, opacity: 0 }}
                                animate={{ scale: 1, opacity: 1 }}
                                className="flex items-center gap-2 bg-blue-600 px-2 md:px-3 py-1 rounded-lg shadow-lg"
                              >
                                <span className="text-[8px] md:text-[10px] font-black uppercase tracking-widest">Cevap Süresi</span>
                                <span className="text-lg md:text-xl font-mono font-black">{gameState.answerTimeLeft}</span>
                              </motion.div>
                            )}
                          </div>
                          <p className="text-lg md:text-3xl font-medium leading-tight text-slate-100">
                            {currentQuestion?.definition}
                          </p>
                          
                          {gameState.isAnswering && (
                            <div className="mt-4 space-y-4">
                              <motion.div 
                                initial={{ opacity: 0 }}
                                animate={{ opacity: 1 }}
                                className="flex items-center gap-2 text-blue-400 font-bold italic animate-pulse text-sm md:text-base mb-2"
                              >
                                <Play size={16} />
                                <span>Lütfen cevabınızı yazın...</span>
                              </motion.div>

                              <form onSubmit={handleManualSubmit} className="flex gap-2">
                                <input 
                                  ref={manualInputRef}
                                  type="text"
                                  value={manualAnswer}
                                  onChange={(e) => setManualAnswer(e.target.value)}
                                  placeholder="Cevabınız..."
                                  className="flex-1 bg-slate-800 border-2 border-blue-500/50 rounded-xl px-4 py-2 text-white outline-none focus:border-blue-400 transition-colors uppercase font-black"
                                  autoFocus
                                />
                                <button 
                                  type="submit"
                                  className="bg-blue-600 hover:bg-blue-500 px-6 py-2 rounded-xl font-bold transition-all text-sm md:text-base"
                                >
                                  CEVAP VER
                                </button>
                              </form>
                            </div>
                          )}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>

                  {/* Manual Controls */}
                  {!gameState.isGameOver && !gameState.isAnswering && !isQuestionIntroActive && (
                    <div className="flex gap-4 mt-4">
                      <button 
                        onClick={handleHarfLutfen}
                        className="px-6 py-3 bg-yellow-600 hover:bg-yellow-500 rounded-xl font-black text-sm md:text-base border-b-4 border-yellow-800 transition-all active:translate-y-1 active:border-b-0"
                      >
                        HARF LÜTFEN
                      </button>
                      <button 
                        onClick={handleStop}
                        className="px-6 py-3 bg-red-600 hover:bg-red-500 rounded-xl font-black text-sm md:text-base border-b-4 border-red-800 transition-all active:translate-y-1 active:border-b-0"
                      >
                        CEVAP VERİYORUM
                      </button>
                    </div>
                  )}
                </motion.div>
              ) : (
                <motion.div 
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="text-center space-y-4 md:space-y-8"
                >
                  <h2 className="text-4xl md:text-7xl font-black text-blue-400">OYUN BİTTİ</h2>
                  <div className="text-xl md:text-4xl text-gray-400">Toplam Puanınız</div>
                  <div className="text-6xl md:text-9xl font-mono font-black text-white">{gameState.totalScore}</div>
                  <button 
                    onClick={resetGame}
                    className="px-8 py-4 md:px-12 md:py-6 bg-blue-600 hover:bg-blue-500 rounded-full text-lg md:text-2xl font-bold transition-all"
                  >
                    TEKRAR OYNA
                  </button>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Footer - Spacer */}
          <div className="mt-auto py-2 md:py-4 shrink-0" />
        </div>
      )}

      {/* Feedback Toast */}
      <AnimatePresence>
        {feedback && (
          <motion.div 
            initial={{ opacity: 0, y: 50 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 50 }}
            className={`fixed bottom-12 left-1/2 -translate-x-1/2 px-12 py-4 rounded-full text-2xl font-bold shadow-2xl z-[100]
              ${feedback === 'Doğru!' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}
          >
            {feedback}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Modals */}
      <AnimatePresence>
        {showInstructions && (
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md"
          >
            <motion.div 
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.9, y: 20 }}
              className="bg-[#1a1a3a] border border-blue-500/30 p-8 rounded-3xl max-w-2xl w-full relative shadow-2xl"
            >
              <button 
                onClick={() => setShowInstructions(false)}
                className="absolute top-6 right-6 p-2 hover:bg-white/10 rounded-full transition-colors"
              >
                <X size={24} />
              </button>
              <h2 className="text-3xl font-bold text-blue-400 mb-6 flex items-center gap-3">
                <Info className="text-blue-400" /> Nasıl Oynanır?
              </h2>
              <div className="space-y-6 text-gray-300">
                <section>
                  <h3 className="text-xl font-semibold text-white mb-2">Oyun Amacı</h3>
                  <p>4 harften 10 harfe kadar değişen kelimeleri, verilen tanımlara bakarak en kısa sürede bulmaya çalışın.</p>
                </section>
                <section>
                  <h3 className="text-xl font-semibold text-white mb-2">Nasıl Oynanır?</h3>
                  <ul className="list-disc list-inside space-y-2">
                    <li><span className="text-blue-400 font-bold uppercase">Harf Lütfen:</span> Bir harf açar ve kelime puanınızdan 100 puan eksiltir.</li>
                    <li><span className="text-blue-400 font-bold uppercase">Cevap Veriyorum:</span> Süreyi durdurur ve 5 saniye içinde cevabınızı yazmanız istenir.</li>
                    <li>Cevap verirken tüm harfleri doğru ve eksiksiz yazmalısınız.</li>
                  </ul>
                </section>
                <section>
                  <h3 className="text-xl font-semibold text-white mb-2">Puanlama</h3>
                  <p>Her kelime, harf sayısı x 100 puan değerindedir. Alınan her harf bu puandan 100 eksiltir.</p>
                </section>
              </div>
            </motion.div>
          </motion.div>
        )}

        {showCredits && (
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md"
          >
            <motion.div 
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.9, y: 20 }}
              className="bg-gradient-to-br from-[#1a1a3a] to-[#0a0a20] border border-blue-500/30 p-12 rounded-3xl max-w-xl w-full relative shadow-[0_0_50px_rgba(37,99,235,0.2)] text-center"
            >
              <button 
                onClick={() => setShowCredits(false)}
                className="absolute top-6 right-6 p-2 hover:bg-white/10 rounded-full transition-colors"
              >
                <X size={24} />
              </button>
              
              <div className="mb-8 flex justify-center">
                <div className="p-4 bg-blue-600/20 rounded-full border border-blue-500/30">
                  <Users size={48} className="text-blue-400" />
                </div>
              </div>

              <h2 className="text-4xl font-black text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400 mb-12 tracking-tight">
                YAPIMCILAR
              </h2>

              <div className="space-y-10">
                <div className="group">
                  <p className="text-blue-400/60 text-sm font-bold uppercase tracking-[0.2em] mb-2">Oyun Düşüncesi</p>
                  <p className="text-3xl font-bold text-white group-hover:text-blue-400 transition-colors duration-300">Alper Bardakçı</p>
                </div>
                
                <div className="w-12 h-px bg-blue-500/20 mx-auto" />

                <div className="group">
                  <p className="text-blue-400/60 text-sm font-bold uppercase tracking-[0.2em] mb-2">Oyunu Tasarlayan</p>
                  <p className="text-3xl font-bold text-white group-hover:text-blue-400 transition-colors duration-300">Kerem Akşahin</p>
                </div>
              </div>

              <div className="mt-12 pt-8 border-t border-blue-500/10">
                <p className="text-gray-500 text-sm italic">© 2026 Kelime Oyunu AI Edition</p>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
