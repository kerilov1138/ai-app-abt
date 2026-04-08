import { GoogleGenAI, Modality } from "@google/genai";

const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

export async function generateHostResponse(prompt: string, context: string) {
  try {
    const response = await ai.models.generateContent({
      model: "gemini-3-flash-preview",
      contents: `Sen bir TV yarışma programı sunucususun (Ali İhsan Varol gibi). 
      Yarışma: Kelime Oyunu.
      Bağlam: ${context}
      Kullanıcı: ${prompt}
      Kısa, esprili ve teşvik edici bir cevap ver.`,
    });
    return response.text;
  } catch (error) {
    console.error("AI Response Error:", error);
    return "Harika gidiyorsun!";
  }
}

export async function speakText(text: string): Promise<boolean> {
  if (!('speechSynthesis' in window)) {
    console.error("Speech synthesis not supported");
    return false;
  }

  return new Promise((resolve) => {
    // Cancel any ongoing speech
    window.speechSynthesis.cancel();

    // Phonetic replacements for more natural Turkish pronunciation
    let phoneticText = text;
    const phoneticMap: { [key: string]: string } = {
      "DAİRE": "DAYRE",
      "ABİDE": "AABİDE",
      "daire": "dayre",
      "abide": "aabide",
      "Daire": "Dayre",
      "Abide": "Aabide"
    };

    Object.keys(phoneticMap).forEach(key => {
      const regex = new RegExp(`\\b${key}\\b`, 'gi');
      phoneticText = phoneticText.replace(regex, phoneticMap[key]);
    });

    const utterance = new SpeechSynthesisUtterance(phoneticText);
    utterance.lang = 'tr-TR';
    utterance.rate = 1.0;
    utterance.pitch = 1.0;

    utterance.onend = () => resolve(true);
    utterance.onerror = (event) => {
      console.error("SpeechSynthesis error:", event);
      resolve(false);
    };

    window.speechSynthesis.speak(utterance);
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
