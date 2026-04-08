import { useState, useEffect, useRef } from 'react';

export function useSpeechRecognition(onCommand: (command: string) => void) {
  const [isListening, setIsListening] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const onCommandRef = useRef(onCommand);

  // Update ref when onCommand changes
  useEffect(() => {
    onCommandRef.current = onCommand;
  }, [onCommand]);

  useEffect(() => {
    if (error && error !== "Ses tanıma başlatılamadı.") return;

    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setError("Tarayıcınız ses tanımayı desteklemiyor.");
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = false;
    recognition.lang = 'tr-TR';

    let shouldRestart = true;
    let restartTimeout: NodeJS.Timeout | null = null;

    recognition.onresult = (event: any) => {
      const last = event.results.length - 1;
      const command = event.results[last][0].transcript.toLowerCase().trim();
      console.log("Detected command:", command);
      onCommandRef.current(command);
    };

    recognition.onerror = (event: any) => {
      if (event.error === 'no-speech') {
        return;
      }
      
      if (event.error === 'network' || event.error === 'aborted') {
        console.warn(`Speech Recognition ${event.error} Error - Will attempt auto-restart...`);
        return;
      }
      
      console.error("Speech Recognition Error:", event.error);
      if (event.error === 'not-allowed') {
        setError("Mikrofon erişimi reddedildi. Lütfen tarayıcı ayarlarından izin verin.");
        shouldRestart = false;
      } else {
        setError(`Hata: ${event.error}`);
      }
      setIsListening(false);
    };

    recognition.onstart = () => {
      setIsListening(true);
      setError(null);
    };

    recognition.onend = () => {
      setIsListening(false);
      if (shouldRestart) {
        // Use a small delay to prevent rapid restart loops on persistent errors
        restartTimeout = setTimeout(() => {
          try {
            recognition.start();
          } catch (e) {
            console.error("Failed to restart recognition:", e);
          }
        }, 1000);
      }
    };

    try {
      recognition.start();
    } catch (e) {
      console.error("Failed to start recognition:", e);
      // Don't set fatal error here to allow retry
    }

    return () => {
      shouldRestart = false;
      if (restartTimeout) clearTimeout(restartTimeout);
      recognition.stop();
    };
  }, [error]);

  return { isListening, error };
}
