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
      recognition.interimResults = true; // Faster feedback
      recognition.lang = 'tr-TR';

      recognition.onresult = (event: any) => {
        let finalTranscript = '';
        let interimTranscript = '';

        for (let i = event.resultIndex; i < event.results.length; ++i) {
          if (event.results[i].isFinal) {
            finalTranscript += event.results[i][0].transcript;
          } else {
            interimTranscript += event.results[i][0].transcript;
          }
        }

        const command = (finalTranscript || interimTranscript).toLowerCase().trim();
        if (command) {
          onCommandRef.current(command);
        }
      };

      recognition.onerror = (event: any) => {
        if (event.error === 'no-speech') return; // Ignore silent periods
        
        if (event.error === 'audio-capture' || event.error === 'not-allowed') {
          setIsListening(false);
          return;
        }
        console.error("Speech Recognition Error:", event.error);
      };

      recognition.onstart = () => setIsListening(true);
      recognition.onend = () => {
        setIsListening(false);
        // Auto-restart if still enabled
        if (enabled && recognitionRef.current) {
          try {
            recognitionRef.current.start();
          } catch (e) {
            // Already started or other error
          }
        }
      };
      
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
