import express from "express";
import { createServer as createViteServer } from "vite";
import path from "path";
import { YoutubeTranscript } from 'youtube-transcript';
import ytdl from '@distube/ytdl-core';

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // API Route to fetch YouTube Transcript
  app.get("/api/transcript", async (req, res) => {
    const videoUrl = req.query.url as string;
    if (!videoUrl) {
      return res.status(400).json({ error: "URL parametresi gerekli." });
    }

    try {
      const videoIdMatch = videoUrl.match(/(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?|shorts)\/|.*[?&]v=)|youtu\.be\/)([^"&?\/\s]{11})/);
      const videoId = videoIdMatch ? videoIdMatch[1] : (videoUrl.length === 11 ? videoUrl : null);

      if (!videoId) {
         return res.status(400).json({ error: "Geçersiz YouTube URL formatı. Lütfen videonun tam linkini yapıştırın." });
      }

      console.log(`Video Analiz Başlatıldı: ${videoId}`);
      
      let transcript;
      let audioFallback = false;

      try {
        transcript = await YoutubeTranscript.fetchTranscript(videoId, { lang: 'tr' });
      } catch (trError) {
        try {
          transcript = await YoutubeTranscript.fetchTranscript(videoId);
        } catch (anyError: any) {
          const errorName = anyError.name || "";
          const msg = anyError.message || "";
          
          if (errorName === 'YoutubeTranscriptDisabledError' || msg.includes('disabled') || msg.includes('not available')) {
            console.log(`Uyarı: Altyazılar devre dışı (${videoId}), sesli analiz denenecek.`);
            return res.json({ videoId, transcript: [], audioFallback: true });
          }
          
          if (msg.includes('Too many requests') || msg.includes('429')) {
            return res.status(429).json({
              error: "YouTube erişimi geçici olarak kısıtladı.",
              suggestion: "Birkaç dakika sonra tekrar deneyin."
            });
          }
          throw anyError;
        }
      }
      
      res.json({ 
        videoId,
        transcript: transcript.map((t: any) => ({
          text: t.text,
          start: t.offset / 1000,
          duration: t.duration / 1000
        })),
        audioFallback: false
      });
    } catch (error) {
      console.error("General API Error:", error);
      res.status(500).json({ 
        error: "Beklenmedik bir hata oluştu veya altyazı verisi alınamadı.",
        details: error instanceof Error ? error.message : "Bilinmeyen hata"
      });
    }
  });

  // API Route for Gemini Summarization
  app.post("/api/summarize", async (req, res) => {
    const { transcript, videoId, audioFallback, deepAnalysis } = req.body;
    const userApiKey = req.headers['x-gemini-key'] as string;

    try {
      const { GoogleGenAI } = await import("@google/genai");
      const apiKey = userApiKey || process.env.GEMINI_API_KEY;

      if (!apiKey) {
        return res.status(200).json({ error: "Gemini API anahtarı bulunamadı. Lütfen ayarlardan anahtarınızı girin." });
      }

      const ai = new GoogleGenAI({
        apiKey,
        httpOptions: {
          headers: {
            'User-Agent': 'aistudio-build',
          }
        }
      });

      let prompt = "";
      let contents: any[] = [];

      // Use audio if fallback is active OR if user requested deep analysis
      if ((audioFallback || deepAnalysis) && videoId) {
        console.log(`Deep Audio Analysis Başlatıldı: ${videoId}`);
        
        // Advanced bypass headers (Mobile Android approach)
        const bypassHeaders = {
          'User-Agent': 'com.google.android.youtube/19.29.37 (Linux; U; Android 11; en_US; Pixel 4 XL Build/RQ3A.210705.001)',
          'X-YouTube-Client-Name': '3',
          'X-YouTube-Client-Version': '19.29.37',
          'Origin': 'https://www.youtube.com',
          'Referer': `https://www.youtube.com/watch?v=${videoId}`,
          'Accept-Language': 'en-US,en;q=0.9',
          'Cache-Control': 'no-cache',
        };

        // Support for custom cookies if provided in environment
        const cookie = process.env.YOUTUBE_COOKIE;
        if (cookie) {
          (bypassHeaders as any)['Cookie'] = cookie;
        }

        // Download audio
        const audioStream = ytdl(videoId, { 
          filter: 'audioonly', 
          quality: 'lowestaudio',
          requestOptions: {
            headers: bypassHeaders
          }
        });
        const chunks: Buffer[] = [];
        
        let totalSize = 0;
        const MAX_SIZE = 15 * 1024 * 1024; // 15MB safe limit for inlineData

        try {
          for await (const chunk of audioStream) {
            totalSize += chunk.length;
            if (totalSize < MAX_SIZE) {
              chunks.push(chunk);
            } else {
              console.log("Audio size limit reached, using partial audio.");
              break;
            }
          }
        } catch (streamError: any) {
             console.error("Stream Error (Audio):", streamError);
             const isBot = streamError.message?.includes('Sign in') || streamError.message?.includes('bot');
             if (isBot) {
               return res.status(200).json({ 
                 error: "YouTube bot koruması tespit edildi.", 
                 suggestion: "Bu video maalesef sesli olarak analiz edilemiyor (YouTube kısıtlaması). Engelden kurtulmak için: 1. Altyazısı olan bir video deneyin. 2. Ayarlar kısmından 'YOUTUBE_COOKIE' değişkenini tanımlayın (En kesin çözüm)." 
               });
             }
             throw streamError;
        }
        
        const audioBuffer = Buffer.concat(chunks);
        const base64Audio = audioBuffer.toString('base64');

        prompt = `
          Lütfen bu videonun sesini (audio) derinlemesine analiz et. Konuşmacıların tonlamalarını, arka plan seslerini ve anlatılan her detayı "dinleyerek" şu çıktıları oluştur:
          1. "shortSummary": En fazla 2-3 cümleden oluşan ana fikir özeti.
          2. "longSummary": Önemli noktaları, detayları ve videodaki duygu durumunu/tonu içeren kapsamlı özet.
          
          Çıktıyı sadece JSON formatında ver:
          {
            "shortSummary": "...",
            "longSummary": "..."
          }
        `;

        contents = [
          {
            role: "user",
            parts: [
              { text: prompt },
              { inlineData: { mimeType: "audio/mp4", data: base64Audio } }
            ]
          }
        ];
      } else {
        if (!transcript) {
          return res.status(200).json({ error: "Transkript veya Video ID gerekli." });
        }

        prompt = `
          Aşağıdaki YouTube videosu transkript metnini saniyeler içinde analiz et ve Türkçe olarak iki farklı özet oluştur:
          1. "shortSummary": En fazla 2-3 cümleden oluşan, ana fikri veren çok kısa özet.
          2. "longSummary": Videodaki ana noktaları, önemli detayları ve sonuçları içeren kapsamlı özet.
          
          Çıktıyı sadece JSON formatında ver:
          {
            "shortSummary": "...",
            "longSummary": "..."
          }

          Transkript Metni:
          ${transcript.slice(0, 30000)}
        `;

        contents = [{ role: "user", parts: [{ text: prompt }] }];
      }

      const response = await ai.models.generateContent({
        model: "gemini-3-flash-preview",
        contents,
        config: {
          systemInstruction: "Sen bir YouTube Video Analiz Asistanısın. Gelen ses veya metin verisini derinlemesine analiz ederek, altyazı bağımlılığı olmadan en doğru bilgiyi sunmalısın.",
          responseMimeType: "application/json",
        }
      });

      const resultText = response.text;
      const parsed = JSON.parse(resultText || "{}");
      res.json(parsed);
    } catch (error: any) {
      console.error("Summarization Error:", error);
      const errorMsg = error.message || "";
      const isBotBlock = errorMsg.includes('Sign in') || 
                        errorMsg.includes('bot') || 
                        errorMsg.includes('status code: 403') ||
                        error.name === 'UnrecoverableError';

      if (isBotBlock) {
        return res.status(200).json({ 
          error: "YouTube bot koruması tespit edildi.", 
          suggestion: "Bu video maalesef sesli olarak analiz edilemiyor (YouTube kısıtlaması). Lütfen altyazısı (CC) olan başka bir video deneyin." 
        });
      }
      res.status(200).json({ error: "Video analiz edilirken bir hata oluştu: " + errorMsg });
    }
  });

  // API Route for Interactive Chat about the video
  app.post("/api/chat", async (req, res) => {
    const { message, history, transcript, videoId, audioFallback } = req.body;
    const userApiKey = req.headers['x-gemini-key'] as string;
    
    if (!message) {
      return res.status(200).json({ error: "Mesaj gerekli." });
    }

    try {
      const { GoogleGenAI } = await import("@google/genai");
      const apiKey = userApiKey || process.env.GEMINI_API_KEY;
      if (!apiKey) return res.status(200).json({ error: "Gemini API anahtarı bulunamadı. Lütfen ayarlardan anahtarınızı girin." });

      const ai = new GoogleGenAI({
        apiKey,
        httpOptions: { headers: { 'User-Agent': 'aistudio-build' } }
      });

      let contents: any[] = [];
      
      // Prepare history
      if (history && Array.isArray(history)) {
        contents = history.map((h: any) => ({
          role: h.role === 'user' ? 'user' : 'model',
          parts: [{ text: h.text }]
        }));
      }

      if (audioFallback && videoId) {
        // Chat using audio fallback
        const bypassHeaders = {
          'User-Agent': 'com.google.android.youtube/19.29.37 (Linux; U; Android 11; en_US; Pixel 4 XL Build/RQ3A.210705.001)',
          'X-YouTube-Client-Name': '3',
          'X-YouTube-Client-Version': '19.29.37',
          'Origin': 'https://www.youtube.com',
          'Referer': `https://www.youtube.com/watch?v=${videoId}`,
          'Accept-Language': 'en-US,en;q=0.9',
          'Cache-Control': 'no-cache',
        };

        const cookie = process.env.YOUTUBE_COOKIE;
        if (cookie) (bypassHeaders as any)['Cookie'] = cookie;

        const audioStream = ytdl(videoId, { 
          filter: 'audioonly', 
          quality: 'lowestaudio',
          requestOptions: {
            headers: bypassHeaders
          }
        });
        const chunks: Buffer[] = [];
        let totalSize = 0;
        const MAX_SIZE = 15 * 1024 * 1024;

        try {
          for await (const chunk of audioStream) {
            totalSize += chunk.length;
            if (totalSize < MAX_SIZE) chunks.push(chunk);
            else break;
          }
        } catch (streamError: any) {
             console.error("Chat Stream Error (Audio):", streamError);
             const isBot = streamError.message?.includes('Sign in') || streamError.message?.includes('bot');
             if (isBot) {
               return res.status(200).json({ 
                 error: "YouTube bot koruması tespit edildi.", 
                 suggestion: "Bu video şu anda sesli olarak analiz edilemiyor. Altyazısı olan bir video deneyebilir veya 'YOUTUBE_COOKIE' ayarını yapılandırabilirsiniz." 
               });
             }
             throw streamError;
        }
        
        const base64Audio = Buffer.concat(chunks).toString('base64');
        
        contents.push({
          role: "user",
          parts: [
            { text: `Video ses dosyası ektedir. Lütfen bu videoya göre şu soruyu cevapla: ${message}` },
            { inlineData: { mimeType: "audio/mp4", data: base64Audio } }
          ]
        });
      } else {
        // Chat using text transcript
        const context = `Videonun transkripti: ${transcript?.slice(0, 30000) || "Transkript yok."}`;
        contents.push({
          role: "user",
          parts: [{ text: `${context}\n\nKullanıcı Sorusu: ${message}` }]
        });
      }

      const response = await ai.models.generateContent({
        model: "gemini-3-flash-preview",
        contents,
        config: {
          systemInstruction: "Sen bir YouTube Video Analiz Asistanısın. Sadece sana verilen transkript veya ses kaydı içeriğine dayanarak kısa ve etkili cevaplar vermeli, video dışına çıkmamalısın.",
        }
      });

      res.json({ text: response.text });
    } catch (error: any) {
      console.error("Chat Error:", error);
      const errorMsg = error.message || "";
      const isBotBlock = errorMsg.includes('Sign in') || 
                        errorMsg.includes('bot') || 
                        errorMsg.includes('status code: 403') ||
                        error.name === 'UnrecoverableError';

      if (isBotBlock) {
        return res.status(200).json({ 
          error: "YouTube bot koruması tespit edildi.", 
          suggestion: "Bu video şu anda sesli olarak analiz edilemiyor. Lütfen altyazısı olan bir video deneyin." 
        });
      }
      res.status(200).json({ error: "Soru cevaplanırken bir hata oluştu: " + errorMsg });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
