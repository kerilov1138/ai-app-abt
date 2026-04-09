import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Timer, Trophy, Mic, MicOff, HelpCircle, Play, RotateCcw, X, Info, Users, Settings, Plus, Trash2, LogOut, Lock } from 'lucide-react';
import { Question, GameState } from '../types';
import { QUESTIONS_POOL } from '../constants';
import { useSpeechRecognition } from '../hooks/useSpeech';
import { speakText, playRevealSound } from '../services/aiService';
import { db } from '../firebase';
import { collection, addDoc, onSnapshot, query, orderBy, deleteDoc, doc, serverTimestamp } from 'firebase/firestore';

/**
 * ANDROID / JAVA COMPATIBILITY NOTE:
 * This application is built as a Progressive Web App (PWA).
 * It is fully compatible with Android WebViews (Android 5.0+).
 * For native Android integration (Java 20 / Kotlin), this app can be wrapped 
 * using tools like Capacitor, Cordova, or a custom WebView Activity.
 * The UI is designed using Flutter-inspired responsive principles.
 */

const INITIAL_TIME = 240; // 4 minutes
const ANSWER_TIME = 5; // 5 seconds to answer after stopping

export default function GameBoard() {
  const [isAppReady, setIsAppReady] = useState(false);
  const [gameQuestions, setGameQuestions] = useState<Question[]>([]);
  const [customQuestions, setCustomQuestions] = useState<Question[]>([]);
  const [showInstructions, setShowInstructions] = useState(false);
  const [showCredits, setShowCredits] = useState(false);
  const [showAdminLogin, setShowAdminLogin] = useState(false);
  const [showAdminPanel, setShowAdminPanel] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  
  const [adminUsername, setAdminUsername] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [loginError, setLoginError] = useState('');

  const [newWord, setNewWord] = useState('');
  const [newDefinition, setNewDefinition] = useState('');
  const [isAdding, setIsAdding] = useState(false);

  // Fetch custom questions from Firebase
  useEffect(() => {
    const q = query(collection(db, 'questions'), orderBy('createdAt', 'desc'));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const questions: any[] = [];
      snapshot.forEach((doc) => {
        questions.push({ id: doc.id, ...doc.data() });
      });
      setCustomQuestions(questions);
    });
    return () => unsubscribe();
  }, []);

  const handleAdminLogin = (e: React.FormEvent) => {
    e.preventDefault();
    if (adminUsername === "Kejiosfer-64" && adminPassword === "alperkejmarcopolo") {
      setIsAdmin(true);
      setShowAdminPanel(true);
      setShowAdminLogin(false);
      setLoginError('');
    } else {
      setLoginError('Hatalı kullanıcı adı veya şifre!');
    }
  };

  const handleAddQuestion = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWord || !newDefinition) return;
    
    setIsAdding(true);
    try {
      await addDoc(collection(db, 'questions'), {
        word: newWord.toUpperCase().trim(),
        definition: newDefinition.trim(),
        length: newWord.trim().length,
        createdAt: serverTimestamp()
      });
      setNewWord('');
      setNewDefinition('');
    } catch (error) {
      console.error("Error adding question:", error);
    } finally {
      setIsAdding(false);
    }
  };

  const handleDeleteQuestion = async (id: string) => {
    if (window.confirm('Bu soruyu silmek istediğinize emin misiniz?')) {
      try {
        await deleteDoc(doc(db, 'questions', id));
      } catch (error) {
        console.error("Error deleting question:", error);
      }
    }
  };
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
    
    // Pause timer while speaking only if requested
    if (pauseTimer && wasTimerRunning) {
      setGameState(prev => ({ ...prev, isTimerRunning: false }));
    }
    
    setIsHostSpeaking(true);
    await speakText(text);
    setIsHostSpeaking(false);
    
    // Resume timer if it was running before and we paused it
    if (pauseTimer && wasTimerRunning && !gameStateRef.current.isGameOver && !gameStateRef.current.isAnswering) {
      setGameState(prev => ({ ...prev, isTimerRunning: true }));
    }
  };

  // Speak definition when question changes
  useEffect(() => {
    const triggerQuestion = async () => {
      if (gameStarted && !gameState.isGameOver && !gameState.isAnswering && currentQuestion) {
        // Start timer immediately so it runs while the host is reading the definition
        setGameState(prev => ({ ...prev, isTimerRunning: true }));
        
        await speak(currentQuestion.definition, false);
      }
    };
    triggerQuestion();
  }, [gameState.currentQuestionIndex, gameStarted, !!currentQuestion]);

  const handleGameOver = useCallback(() => {
    setGameState(prev => ({ ...prev, isGameOver: true, isTimerRunning: false }));
    speak("Yarışma sona erdi! Toplam puanınız: " + gameStateRef.current.totalScore + ". Tebrikler!");
    confetti({
      particleCount: 150,
      spread: 70,
      origin: { y: 0.6 }
    });
  }, []);

  // Main Timer
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

  // Answer Timer
  useEffect(() => {
    if (gameState.isAnswering && gameState.isAnswerTimerRunning && gameState.answerTimeLeft > 0) {
      answerTimerRef.current = setInterval(() => {
        setGameState(prev => {
          if (prev.answerTimeLeft <= 1) {
            handleAnswer(""); // Time out
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
      await speak("Buyursunlar bir harf.");
    } else {
      await speak("Son harf kaldı, artık cevaplamanız gerekiyor.");
    }
  }, [gameQuestions]);

  const handleStop = useCallback(async () => {
    const state = gameStateRef.current;
    if (state.isGameOver || state.isAnswering || !state.isTimerRunning) return;
    
    // Stop main timer and enter answering mode, but don't start the 5s timer yet
    setGameState(prev => ({ 
      ...prev, 
      isTimerRunning: false, 
      isAnswering: true, 
      isAnswerTimerRunning: false,
      answerTimeLeft: ANSWER_TIME 
    }));
    
    await speak("Bekliyoruz", false);
    
    // Start the 5s countdown AFTER the host finishes speaking
    setGameState(prev => ({ ...prev, isAnswerTimerRunning: true }));
  }, []);

  const handleAnswer = useCallback(async (answer: string) => {
    const state = gameStateRef.current;
    if (!state.isAnswering) return; // Prevent double calls

    // Immediately stop answering mode and timers
    setGameState(prev => ({ 
      ...prev, 
      isAnswering: false, 
      isAnswerTimerRunning: false 
    }));

    const question = gameQuestions[state.currentQuestionIndex];
    if (!question) return;
    
    const phoneticNormalization = (str: string) => {
      return str
        .replace(/DAYRE/g, 'DAİRE')
        .replace(/AABİDE/g, 'ABİDE')
        .replace(/AĞBİDE/g, 'ABİDE')
        .replace(/AABİDE/g, 'ABİDE');
    };

    const normalizedAnswer = phoneticNormalization(answer.toUpperCase())
      .replace(/İ/g, 'I').replace(/Ğ/g, 'G').replace(/Ü/g, 'U').replace(/Ş/g, 'S').replace(/Ö/g, 'O').replace(/Ç/g, 'C');
    const normalizedWord = phoneticNormalization(question.word.toUpperCase())
      .replace(/İ/g, 'I').replace(/Ğ/g, 'G').replace(/Ü/g, 'U').replace(/Ş/g, 'S').replace(/Ö/g, 'O').replace(/Ç/g, 'C');

    const isCorrect = normalizedAnswer === normalizedWord;
    const wordPoints = (question.word.length - state.revealedLetters.length) * 100;

    if (isCorrect) {
      setFeedback("Doğru!");
      
      // Sequential Reveal Animation
      const allIndices = Array.from({ length: question.word.length }, (_, i) => i);
      for (const index of allIndices) {
        if (!gameStateRef.current.revealedLetters.includes(index)) {
          playRevealSound();
          setGameState(prev => ({
            ...prev,
            revealedLetters: [...prev.revealedLetters, index]
          }));
          await new Promise(resolve => setTimeout(resolve, 150)); // Fast sequential reveal
        }
      }

      await speak("Harika! Doğru cevap.");
      
      setGameState(prev => ({
        ...prev,
        totalScore: prev.totalScore + wordPoints,
        isTimerRunning: true,
        currentQuestionIndex: prev.currentQuestionIndex + 1,
        revealedLetters: [],
      }));
      
      if (state.currentQuestionIndex + 1 >= gameQuestions.length) {
        handleGameOver();
      }
    } else {
      setFeedback("Yanlış!");
      
      // Reveal all letters even if wrong, so user sees the word
      const allIndices = Array.from({ length: question.word.length }, (_, i) => i);
      setGameState(prev => ({ ...prev, revealedLetters: allIndices }));
      
      if (answer === "") {
        await speak(`Süreniz doldu. Doğru cevap ${question.word} olacaktı.`);
      } else {
        await speak(`Maalesef yanlış. Doğru cevap ${question.word} olacaktı.`);
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

  const onCommand = useCallback((command: string) => {
    const state = gameStateRef.current;
    const started = gameStartedRef.current;
    const hostSpeaking = isHostSpeakingRef.current;
    
    if (!started || state.isGameOver || hostSpeaking) return;

    const normalizedCommand = command.toLowerCase();

    // Expanded command recognition
    const harfCommands = ["harf lütfen", "harf alayım", "harf almak istiyorum", "bir harf", "harf ver"];
    const stopCommands = ["durdur", "buton", "cevap", "cevap veriyorum", "kelimeyi söylüyorum", "dur"];

    if (harfCommands.some(cmd => normalizedCommand.includes(cmd))) {
      handleHarfLutfen();
    } else if (stopCommands.some(cmd => normalizedCommand.includes(cmd))) {
      handleStop();
    } else if (state.isAnswering) {
      handleAnswer(command);
    }
  }, [handleHarfLutfen, handleStop, handleAnswer]);

  const { isListening, error: speechError } = useSpeechRecognition(onCommand, gameStarted && !gameState.isGameOver);

  // App Initialization
  useEffect(() => {
    const init = async () => {
      // Simulate a professional splash screen delay
      await new Promise(resolve => setTimeout(resolve, 2000));
      setIsAppReady(true);
    };
    init();
  }, []);

  if (!isAppReady) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-[#0a0a20] text-white">
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.5, ease: "easeOut" }}
          className="flex flex-col items-center space-y-8"
        >
          <div className="relative">
            <div className="w-24 h-24 md:w-32 md:h-32 bg-blue-600 rounded-[2rem] shadow-[0_0_50px_rgba(37,99,235,0.3)] flex items-center justify-center animate-pulse">
              <HelpCircle size={48} className="md:w-16 md:h-16 text-white" />
            </div>
            <div className="absolute -inset-4 border-4 border-blue-500/20 rounded-[2.5rem] animate-[spin_10s_linear_infinite]" />
          </div>
          <div className="text-center">
            <h1 className="text-3xl md:text-5xl font-black tracking-tighter text-blue-400 mb-2">KELİME OYUNU</h1>
            <div className="flex items-center justify-center gap-2 text-blue-500/60 font-mono text-sm uppercase tracking-widest">
              <div className="w-2 h-2 bg-blue-500 rounded-full animate-bounce" />
              Sistem Hazırlanıyor
            </div>
          </div>
        </motion.div>
      </div>
    );
  }

  const startGame = async () => {
    // Explicitly request microphone permission to ensure prompt on mobile
    try {
      await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (err) {
      console.error("Microphone permission denied:", err);
      alert("Oyunu sesli komutlarla oynamak için mikrofon izni vermeniz gerekmektedir.");
      return;
    }

    // Combine static pool with custom questions from Firebase
    const fullPool = [...QUESTIONS_POOL, ...customQuestions];
    
    // Select 2 random questions for each length from 4 to 10
    const selected: Question[] = [];
    for (let len = 4; len <= 10; len++) {
      const pool = fullPool.filter(q => q.length === len);
      const shuffled = [...pool].sort(() => Math.random() - 0.5);
      selected.push(...shuffled.slice(0, 2));
    }
    setGameQuestions(selected);
    setGameStarted(true);
    
    // Reset state for new game
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

    await speak("Kelime Oyunu'na hoş geldiniz! İlk sorunuz geliyor.");
    // The useEffect for currentQuestionIndex will trigger the first question speech
    // But we need to make sure the timer starts AFTER that speech.
    // So we'll handle the first question speech here manually or adjust the effect.
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
  };

  if (!gameStarted) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-[#0a0a20] text-white p-8">
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="text-center space-y-8 max-w-2xl"
        >
          <h1 className="text-4xl md:text-7xl font-black tracking-tighter text-blue-400 drop-shadow-2xl">KELİME OYUNU</h1>
          <p className="text-lg md:text-2xl text-gray-400 px-6 max-w-xl mx-auto leading-relaxed">
            Efsane yarışma artık sesinizle kontrolünüzde. 
            "Harf alayım" diyerek harf alabilir, "Cevap veriyorum" diyerek süreyi durdurup cevabınızı söyleyebilirsiniz.
          </p>
          <div className="pt-8">
            <button 
              onClick={startGame}
              className="group relative px-10 md:px-16 py-5 md:py-8 bg-blue-600 hover:bg-blue-500 rounded-[2rem] text-xl md:text-3xl font-black transition-all hover:scale-105 active:scale-95 shadow-[0_20px_50px_rgba(37,99,235,0.4)] border-b-8 border-blue-800"
            >
              <span className="flex items-center gap-4">
                <Play size={24} className="md:w-10 md:h-10 fill-current" /> YARIŞMAYI BAŞLAT
              </span>
            </button>
          </div>

          <div className="flex flex-wrap gap-4 md:gap-6 mt-8 justify-center">
            <button 
              onClick={() => setShowInstructions(true)}
              className="flex items-center gap-2 text-blue-400 hover:text-blue-300 transition-colors font-medium text-sm md:text-base"
            >
              <Info size={18} /> Nasıl Oynanır?
            </button>
            <button 
              onClick={() => setShowCredits(true)}
              className="flex items-center gap-2 text-blue-400 hover:text-blue-300 transition-colors font-medium text-sm md:text-base"
            >
              <Users size={18} /> Yapımcılar
            </button>
            <button 
              onClick={() => isAdmin ? setShowAdminPanel(true) : setShowAdminLogin(true)}
              className="flex items-center gap-2 text-slate-500 hover:text-slate-400 transition-colors font-medium text-sm md:text-base"
            >
              <Settings size={18} /> Yönetim
            </button>
          </div>

          {speechError && (
            <p className="text-red-400 bg-red-900/20 p-4 rounded-lg border border-red-500/50">
              {speechError}
            </p>
          )}
        </motion.div>

        {/* Modals on Landing Page */}
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
                    <h3 className="text-xl font-semibold text-white mb-2">Sesli Komutlar</h3>
                    <ul className="list-disc list-inside space-y-2">
                      <li><span className="text-blue-400 font-mono">"Harf lütfen"</span> veya <span className="text-blue-400 font-mono">"Harf alayım"</span>: Bir harf açar (100 puan eksiltir).</li>
                      <li><span className="text-blue-400 font-mono">"Durdur"</span> veya <span className="text-blue-400 font-mono">"Cevap veriyorum"</span>: Süreyi durdurur ve cevap moduna geçer.</li>
                      <li>Cevap modundayken sadece kelimeyi söylemeniz yeterlidir.</li>
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

          {/* Admin Login Modal */}
          {showAdminLogin && (
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/90 backdrop-blur-xl"
            >
              <motion.div 
                initial={{ scale: 0.9, y: 20 }}
                animate={{ scale: 1, y: 0 }}
                exit={{ scale: 0.9, y: 20 }}
                className="bg-[#1a1a3a] border border-blue-500/30 p-6 md:p-10 rounded-2xl md:rounded-3xl max-w-md w-full relative shadow-2xl"
              >
                <button 
                  onClick={() => setShowAdminLogin(false)}
                  className="absolute top-4 right-4 md:top-6 md:right-6 p-2 hover:bg-white/10 rounded-full transition-colors"
                >
                  <X size={20} className="md:w-6 md:h-6" />
                </button>
                <div className="flex flex-col items-center mb-6 md:mb-8">
                  <div className="p-3 md:p-4 bg-blue-600/20 rounded-full mb-4">
                    <Lock size={24} className="md:w-8 md:h-8 text-blue-400" />
                  </div>
                  <h2 className="text-xl md:text-2xl font-bold text-white">Yönetici Girişi</h2>
                </div>
                <form onSubmit={handleAdminLogin} className="space-y-6">
                  <div>
                    <label className="block text-sm font-bold text-blue-400 mb-2 uppercase tracking-widest">Kullanıcı Adı</label>
                    <input 
                      type="text" 
                      value={adminUsername}
                      onChange={(e) => setAdminUsername(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:border-blue-500 outline-none transition-colors"
                      placeholder="Username"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-blue-400 mb-2 uppercase tracking-widest">Şifre</label>
                    <input 
                      type="password" 
                      value={adminPassword}
                      onChange={(e) => setAdminPassword(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:border-blue-500 outline-none transition-colors"
                      placeholder="••••••••"
                    />
                  </div>
                  {loginError && <p className="text-red-400 text-sm font-medium">{loginError}</p>}
                  <button 
                    type="submit"
                    className="w-full py-4 bg-blue-600 hover:bg-blue-500 rounded-xl text-lg font-bold transition-all shadow-lg shadow-blue-600/20"
                  >
                    Giriş Yap
                  </button>
                </form>
              </motion.div>
            </motion.div>
          )}

          {/* Admin Panel Modal */}
          {showAdminPanel && (
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/95 backdrop-blur-2xl"
            >
              <motion.div 
                initial={{ scale: 0.95, y: 20 }}
                animate={{ scale: 1, y: 0 }}
                exit={{ scale: 0.95, y: 20 }}
                className="bg-[#0a0a20] border border-blue-500/30 rounded-2xl md:rounded-3xl max-w-4xl w-full h-[90vh] md:h-[85vh] flex flex-col relative shadow-2xl overflow-hidden"
              >
                <div className="p-4 md:p-8 border-b border-blue-500/20 flex justify-between items-center bg-blue-900/10">
                  <div className="flex items-center gap-3 md:gap-4">
                    <Settings className="text-blue-400 w-5 h-5 md:w-6 md:h-6" />
                    <h2 className="text-lg md:text-2xl font-bold text-white">Soru Yönetimi</h2>
                  </div>
                  <div className="flex items-center gap-2 md:gap-4">
                    <button 
                      onClick={() => { setIsAdmin(false); setShowAdminPanel(false); }}
                      className="flex items-center gap-2 px-3 md:px-4 py-1.5 md:py-2 bg-red-900/20 text-red-400 border border-red-500/30 rounded-xl hover:bg-red-900/40 transition-all text-[10px] md:text-sm font-bold"
                    >
                      <LogOut size={16} className="md:w-[18px] md:h-[18px]" /> Çıkış
                    </button>
                    <button 
                      onClick={() => setShowAdminPanel(false)}
                      className="p-1.5 md:p-2 hover:bg-white/10 rounded-full transition-colors"
                    >
                      <X size={20} className="md:w-6 md:h-6" />
                    </button>
                  </div>
                </div>

                <div className="flex-1 overflow-y-auto p-4 md:p-8 space-y-6 md:space-y-10">
                  {/* Add Question Form */}
                  <section className="bg-blue-900/10 border border-blue-500/20 p-5 md:p-8 rounded-xl md:rounded-2xl">
                    <h3 className="text-lg md:text-xl font-bold text-blue-400 mb-4 md:mb-6 flex items-center gap-2">
                      <Plus size={18} className="md:w-5 md:h-5" /> Yeni Soru Ekle
                    </h3>
                    <form onSubmit={handleAddQuestion} className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6">
                      <div className="md:col-span-1">
                        <label className="block text-[10px] md:text-xs font-bold text-slate-400 mb-2 uppercase tracking-widest">Kelime (4-10 Harf)</label>
                        <input 
                          type="text" 
                          maxLength={10}
                          value={newWord}
                          onChange={(e) => setNewWord(e.target.value.toUpperCase())}
                          className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-2.5 md:py-3 text-white focus:border-blue-500 outline-none text-sm md:text-base"
                          placeholder="Örn: KALEM"
                          required
                        />
                      </div>
                      <div className="md:col-span-2">
                        <label className="block text-[10px] md:text-xs font-bold text-slate-400 mb-2 uppercase tracking-widest">Tanım / Soru</label>
                        <textarea 
                          value={newDefinition}
                          onChange={(e) => setNewDefinition(e.target.value)}
                          className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-2.5 md:py-3 text-white focus:border-blue-500 outline-none h-20 md:h-24 resize-none text-sm md:text-base"
                          placeholder="Kelimeyi açıklayan tanımı buraya yazın..."
                          required
                        />
                      </div>
                      <div className="md:col-span-2 flex justify-end">
                        <button 
                          type="submit"
                          disabled={isAdding}
                          className="w-full md:w-auto px-8 py-3 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 rounded-xl font-bold transition-all flex items-center justify-center gap-2 text-sm md:text-base"
                        >
                          {isAdding ? 'Ekleniyor...' : 'Sisteme Ekle'}
                        </button>
                      </div>
                    </form>
                  </section>

                  {/* Questions List */}
                  <section>
                    <h3 className="text-lg md:text-xl font-bold text-white mb-4 md:mb-6">Eklenen Sorular ({customQuestions.length})</h3>
                    <div className="space-y-3 md:space-y-4">
                      {customQuestions.length === 0 ? (
                        <p className="text-slate-500 italic text-sm">Henüz özel soru eklenmemiş.</p>
                      ) : (
                        customQuestions.map((q: any) => (
                          <div key={q.id} className="bg-slate-900/50 border border-slate-800 p-4 md:p-6 rounded-xl md:rounded-2xl flex justify-between items-center group hover:border-blue-500/30 transition-all">
                            <div className="space-y-1 md:space-y-2">
                              <div className="flex items-center gap-2 md:gap-3">
                                <span className="text-xl md:text-2xl font-black text-white">{q.word}</span>
                                <span className="px-1.5 py-0.5 bg-blue-900/40 text-blue-400 text-[8px] md:text-[10px] font-bold rounded border border-blue-500/20">{q.length} HARF</span>
                              </div>
                              <p className="text-slate-400 text-xs md:text-sm line-clamp-2">{q.definition}</p>
                            </div>
                            <button 
                              onClick={() => handleDeleteQuestion(q.id)}
                              className="p-2 md:p-3 text-slate-600 hover:text-red-400 hover:bg-red-400/10 rounded-xl transition-all"
                            >
                              <Trash2 size={18} className="md:w-5 md:h-5" />
                            </button>
                          </div>
                        ))
                      )}
                    </div>
                  </section>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-screen bg-[#0a0a20] text-white p-4 md:p-8 font-sans">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-center gap-6 mb-8 md:mb-16">
        <div className="flex flex-wrap justify-center md:justify-start items-center gap-4 md:gap-8">
          <div className="bg-blue-900/40 border-2 border-blue-500/30 p-4 md:p-6 rounded-[2rem] backdrop-blur-xl min-w-[140px] shadow-2xl">
            <div className="flex items-center gap-2 text-blue-400 mb-1">
              <Trophy size={18} className="md:w-6 md:h-6" />
              <span className="text-[10px] md:text-sm font-black uppercase tracking-widest">Puan</span>
            </div>
            <div className="text-3xl md:text-5xl font-mono font-black text-white">{gameState.totalScore}</div>
          </div>
          
          <div className={`p-4 md:p-6 rounded-[2rem] border-2 transition-all duration-500 min-w-[140px] shadow-2xl ${gameState.timeLeft < 30 ? 'bg-red-900/40 border-red-500/50 animate-pulse' : 'bg-blue-900/40 border-blue-500/30'}`}>
            <div className="flex items-center gap-2 text-blue-400 mb-1">
              <Timer size={18} className="md:w-6 md:h-6" />
              <span className="text-[10px] md:text-sm font-black uppercase tracking-widest">Süre</span>
            </div>
            <div className="text-3xl md:text-5xl font-mono font-black text-white">
              {Math.floor(gameState.timeLeft / 60)}:{(gameState.timeLeft % 60).toString().padStart(2, '0')}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap justify-center items-center gap-3 md:gap-4">
          {/* Host Visual */}
          <div className="flex items-center gap-2 md:gap-3 bg-blue-900/20 px-3 md:px-4 py-1.5 md:py-2 rounded-full border border-blue-500/30">
            <div className={`w-2 h-2 md:w-3 md:h-3 rounded-full ${isHostSpeaking ? 'bg-blue-400 animate-ping' : 'bg-blue-900'}`} />
            <span className="text-[10px] md:text-xs font-bold uppercase tracking-widest text-blue-400">Sunucu</span>
          </div>

          <div className={`flex items-center gap-2 px-3 md:px-4 py-1.5 md:py-2 rounded-full border transition-all duration-300 ${isListening && !isHostSpeaking ? 'bg-green-900/20 border-green-500/50 text-green-400' : 'bg-red-900/20 border-red-500/50 text-red-400'}`}>
            {isListening && !isHostSpeaking ? <Mic size={16} className="md:w-[18px] md:h-[18px] animate-pulse" /> : <MicOff size={16} className="md:w-[18px] md:h-[18px]" />}
            <span className="text-[10px] md:text-sm font-bold uppercase tracking-tighter">
              {isListening && !isHostSpeaking ? 'Dinliyorum' : isHostSpeaking ? 'Konuşuyor...' : 'Kapalı'}
            </span>
          </div>
          <button onClick={resetGame} className="p-2 md:p-3 hover:bg-white/10 rounded-full transition-colors">
            <RotateCcw size={20} className="md:w-6 md:h-6" />
          </button>
        </div>
      </div>

      {/* Main Game Area */}
      <div className="flex-1 flex flex-col items-center justify-center max-w-5xl mx-auto w-full">
        <AnimatePresence mode="wait">
          {!gameState.isGameOver ? (
            <motion.div 
              key={gameState.currentQuestionIndex}
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 1.05 }}
              className="w-full space-y-12"
            >
              {/* Word Grid (Honeycomb) */}
              <div className="honeycomb-grid">
                {currentQuestion.word.split('').map((char, index) => {
                  const isRevealed = gameState.revealedLetters.includes(index) || gameState.isGameOver;
                  const wordLength = currentQuestion.word.length;
                  // Calculate dynamic size based on word length to ensure it fits on one line
                  // Mobile-first sizing
                  const sizeClass = wordLength > 8 ? 'w-8 h-10 md:w-16 md:h-20' : 
                                   wordLength > 6 ? 'w-10 h-12 md:w-20 md:h-24' : 
                                   'w-12 h-14 md:w-24 md:h-28';

                  return (
                    <div key={`${gameState.currentQuestionIndex}-${index}`} className={`hexagon-container ${sizeClass}`}>
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
                          {/* Revealed Side */}
                          <div className="hexagon w-full h-full bg-gradient-to-br from-yellow-300 via-yellow-500 to-yellow-700 flex items-center justify-center shadow-[0_5px_15px_rgba(234,179,8,0.3)] md:shadow-[0_10px_30px_rgba(234,179,8,0.5)] border-t border-yellow-200/50">
                            <span className="text-xl md:text-5xl font-black text-blue-950 drop-shadow-[0_1px_1px_rgba(255,255,255,0.3)] md:drop-shadow-[0_2px_2px_rgba(255,255,255,0.3)]">
                              {char}
                            </span>
                          </div>
                        </motion.div>

                        {/* Hidden Side (Empty Hexagon) */}
                        <motion.div
                          initial={false}
                          animate={{ rotateY: isRevealed ? -90 : 0 }}
                          transition={{ duration: 0.4, ease: "easeOut" }}
                          className="absolute inset-0"
                        >
                          <div className="hexagon w-full h-full bg-slate-800/90 border-2 border-slate-700/50 flex items-center justify-center overflow-hidden">
                            <div className="hexagon w-[85%] h-[85%] bg-slate-900/80 shadow-inner flex items-center justify-center">
                               <div className="w-full h-full bg-[radial-gradient(circle_at_center,rgba(59,130,246,0.1)_0%,transparent_70%)]" />
                            </div>
                          </div>
                        </motion.div>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Definition and Answering Status */}
              <div className="relative w-full">
                <div className={`bg-slate-900/60 border p-5 md:p-8 rounded-2xl md:rounded-3xl backdrop-blur-md shadow-2xl transition-all duration-500 ${gameState.isAnswering ? 'border-blue-500 ring-4 ring-blue-500/20' : 'border-slate-700/50'}`}>
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex items-center gap-2 md:gap-3 text-blue-400">
                      <HelpCircle size={20} className="md:w-6 md:h-6" />
                      <span className="text-[10px] md:text-sm font-bold uppercase tracking-[0.2em]">Soru</span>
                    </div>
                    {gameState.isAnswering && (
                      <motion.div 
                        initial={{ scale: 0.8, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        className="flex items-center gap-2 md:gap-3 bg-blue-600 px-3 md:px-4 py-1.5 md:py-2 rounded-xl shadow-lg"
                      >
                        <span className="text-[10px] md:text-xs font-black uppercase tracking-widest">Süre</span>
                        <span className="text-xl md:text-2xl font-mono font-black">{gameState.answerTimeLeft}</span>
                      </motion.div>
                    )}
                  </div>
                  <p className="text-xl md:text-4xl font-medium leading-tight text-slate-100">
                    {currentQuestion.definition}
                  </p>
                  
                  {gameState.isAnswering && (
                    <motion.div 
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      className="mt-6 flex items-center gap-3 text-blue-400 font-bold italic animate-pulse"
                    >
                      <Mic size={20} />
                      <span>Lütfen cevabınızı söyleyin...</span>
                    </motion.div>
                  )}
                </div>
              </div>
            </motion.div>
          ) : (
            <motion.div 
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="text-center space-y-8"
            >
              <h2 className="text-7xl font-black text-blue-400">OYUN BİTTİ</h2>
              <div className="text-4xl text-gray-400">Toplam Puanınız</div>
              <div className="text-9xl font-mono font-black text-white">{gameState.totalScore}</div>
              <button 
                onClick={resetGame}
                className="px-12 py-6 bg-blue-600 hover:bg-blue-500 rounded-full text-2xl font-bold transition-all"
              >
                TEKRAR OYNA
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

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

      {/* Controls Help */}
      <div className="mt-auto pt-8 flex flex-wrap justify-center gap-4 md:gap-12 text-slate-500 text-[10px] md:text-sm font-bold uppercase tracking-widest">
        <div className="flex items-center gap-2">
          <span className="px-2 py-1 bg-slate-800 rounded border border-slate-700 text-slate-300">"Harf Alayım"</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="px-2 py-1 bg-slate-800 rounded border border-slate-700 text-slate-300">"Cevap Veriyorum"</span>
        </div>
      </div>
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
                  <h3 className="text-xl font-semibold text-white mb-2">Sesli Komutlar</h3>
                  <ul className="list-disc list-inside space-y-2">
                    <li><span className="text-blue-400 font-mono">"Harf lütfen"</span> veya <span className="text-blue-400 font-mono">"Harf alayım"</span>: Bir harf açar (100 puan eksiltir).</li>
                    <li><span className="text-blue-400 font-mono">"Durdur"</span> veya <span className="text-blue-400 font-mono">"Cevap veriyorum"</span>: Süreyi durdurur ve sunucu "Bekliyoruz" dedikten sonra 5 saniyelik cevap süreniz başlar.</li>
                    <li>Cevap modundayken sadece kelimeyi söylemeniz yeterlidir.</li>
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
