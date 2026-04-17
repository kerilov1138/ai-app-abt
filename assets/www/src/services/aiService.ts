// Local response pool to replace AI
const HOST_RESPONSES = {
  correct: [
    "Harika! Tam üstüne bastınız.",
    "Efendim muazzam bir cevap!",
    "Tebrikler, puanlar hanenize yazıldı.",
    "İşte budur! Kelimeyi şak diye buldunuz.",
    "Bravo! Bir sonraki soruya geçelim mi?"
  ],
  wrong: [
    "Maalesef, bu sefer olmadı.",
    "Hay aksi! Doğru cevap burnumuzun ucundaydı.",
    "Üzülmeyin, bir sonraki soruda telafi ederiz.",
    "Sağlık olsun, puanları geri aldık ama moral bozmak yok.",
    "Ah, çok yakındınız ama tam olarak o değildi."
  ],
  letter: [
    "Buyursunlar bir harf.",
    "İşte bir ipucu daha.",
    "Bir harf daha açtık, bakalım şimdi ne olacak?",
    "Harf geldi, işler kolaylaşıyor mu?",
    "Puanınızdan biraz feda ettik ama harfimiz geldi."
  ],
  welcome: [
    "Kelime Oyunu'na hoş geldiniz! Ben sunucunuz, hazırsanız başlayalım.",
    "Efendim merhabalar! Yeni bir yarışma, yeni heyecanlar.",
    "Ekran başındakiler ve siz hazırsanız, ilk sorumuz geliyor!"
  ]
};

function getRandomResponse(type: keyof typeof HOST_RESPONSES) {
  const responses = HOST_RESPONSES[type];
  return responses[Math.floor(Math.random() * responses.length)];
}

export async function generateHostResponse(type: 'correct' | 'wrong' | 'letter' | 'welcome', context?: string) {
  // No more API calls, completely local and fast
  return getRandomResponse(type);
}

let turkishVoice: SpeechSynthesisVoice | null = null;

// Function to find the best Turkish voice on the system
function findTurkishVoice() {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return null;
  
  const voices = window.speechSynthesis.getVoices();
  // Priority 1: Local Turkish voice (embedded in OS)
  // Priority 2: Any Turkish voice
  const voice = voices.find(v => v.lang.startsWith('tr') && v.localService) || 
                voices.find(v => v.lang.startsWith('tr'));
  
  if (voice) {
    turkishVoice = voice;
  }
  return turkishVoice;
}

// Initialize voices
if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
  window.speechSynthesis.getVoices();
  if (window.speechSynthesis.onvoiceschanged !== undefined) {
    window.speechSynthesis.onvoiceschanged = findTurkishVoice;
  }
}

export async function speakText(text: string): Promise<boolean> {
  if (!('speechSynthesis' in window)) {
    return false;
  }

  return new Promise((resolve) => {
    try {
      window.speechSynthesis.cancel();
    } catch (e) {}

    const utterance = new SpeechSynthesisUtterance(text);
    
    // Use the best found Turkish voice
    const voice = findTurkishVoice();
    if (voice) {
      utterance.voice = voice;
    }
    
    utterance.lang = 'tr-TR';
    utterance.rate = 1.05; // Slightly faster for a more natural TV host feel
    utterance.pitch = 1.0;

    utterance.onend = () => resolve(true);
    utterance.onerror = () => resolve(false);

    try {
      window.speechSynthesis.speak(utterance);
    } catch (e) {
      resolve(false);
    }
  });
}

export function playRevealSound() {
  try {
    const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(880, audioCtx.currentTime); // A5 note
    oscillator.frequency.exponentialRampToValueAtTime(440, audioCtx.currentTime + 0.1);

    gainNode.gain.setValueAtTime(0.1, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.1);

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + 0.1);
  } catch (e) {
    console.error("Audio Context Error:", e);
  }
}
