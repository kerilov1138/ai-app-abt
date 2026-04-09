import { useState, useEffect, useRef } from 'react';

export function useSpeechRecognition(onCommand: (command: string) => void, enabled: boolean = true) {
  const [isListening, setIsListening] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const onCommandRef = useRef(onCommand);
  const recognitionRef = useRef<any>(null);

  // Update ref when onCommand changes
  useEffect(() => {
    onCommandRef.current = onCommand;
  }, [onCommand]);

  useEffect(() => {
    if (!enabled) {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch (e) {
          // Ignore stop errors
        }
      }
      return;
    }

    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      console.warn("Speech recognition not supported in this browser.");
      return;
    }

    if (!recognitionRef.current) {
      const recognition = new SpeechRecognition();
      recognition.continuous = true;
      recognition.interimResults = false;
      recognition.lang = 'tr-TR';

      recognition.onresult = (event: any) => {
        const last = event.results.length - 1;
        const command = event.results[last][0].transcript.toLowerCase().trim();
        onCommandRef.current(command);
      };

      recognition.onerror = (event: any) => {
        if (event.error === 'no-speech' || event.error === 'audio-capture' || event.error === 'not-allowed') {
          setIsListening(false);
          return;
        }
        console.error("Speech Recognition Error:", event.error);
      };

      recognition.onstart = () => setIsListening(true);
      recognition.onend = () => setIsListening(false);
      
      recognitionRef.current = recognition;
    }

    try {
      recognitionRef.current.start();
    } catch (e) {
      // Ignore start errors if already started
    }

    return () => {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch (e) {
          // Ignore stop errors
        }
      }
    };
  }, [enabled]);

  return { isListening, error };
}
