import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import * as d3 from 'd3';
import { feature } from 'topojson-client';
import { motion, AnimatePresence } from 'motion/react';
import { Globe as GlobeIcon, Trophy, RotateCcw, CheckCircle2, XCircle, HelpCircle, Languages, ZoomIn, ZoomOut, User, PlayCircle, Users, Phone, Percent } from 'lucide-react';
import { cn } from './lib/utils';

interface Country {
  id: string;
  name: string;
  geometry: any;
  color: string;
  isSolved?: boolean;
  isCorrect?: boolean;
}

const WORLD_JSON_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json';

const TURKISH_COUNTRY_NAMES: Record<string, string> = {
  "Afghanistan": "Afganistan", "Albania": "Arnavutluk", "Algeria": "Cezayir", "Angola": "Angola", "Antarctica": "Antarktika",
  "Argentina": "Arjantin", "Armenia": "Ermenistan", "Australia": "Avustralya", "Austria": "Avusturya", "Azerbaijan": "Azerbaycan",
  "Bahamas": "Bahamalar", "Bangladesh": "Bangladeş", "Belarus": "Belarus", "Belgium": "Belçika", "Belize": "Belize",
  "Benin": "Benin", "Bhutan": "Butan", "Bolivia": "Bolivya", "Bosnia and Herz.": "Bosna Hersek", "Botswana": "Botsvana",
  "Brazil": "Brezilya", "Brunei": "Bruney", "Bulgaria": "Bulgaristan", "Burkina Faso": "Burkina Faso", "Burundi": "Burundi",
  "Cambodia": "Kamboçya", "Cameroon": "Kamerun", "Canada": "Kanada", "Central African Rep.": "Orta Afrika Cumhuriyeti", "Chad": "Çad",
  "Chile": "Şili", "China": "Çin", "Colombia": "Kolombiya", "Congo": "Kongo", "Costa Rica": "Kosta Rika",
  "Croatia": "Hırvatistan", "Cuba": "Küba", "Cyprus": "Kıbrıs", "Czechia": "Çekya", "Denmark": "Danimarka",
  "Djibouti": "Cibuti", "Dominican Rep.": "Dominik Cumhuriyeti", "Ecuador": "Ekvador", "Egypt": "Mısır", "El Salvador": "El Salvador",
  "Equatorial Guinea": "Ekvator Ginesi", "Eritrea": "Eritre", "Estonia": "Estonya", "Ethiopia": "Etiyopya", "Falkland Is.": "Falkland Adaları",
  "Fiji": "Fiji", "Finland": "Finlandiya", "France": "Fransa", "Gabon": "Gabon", "Gambia": "Gambiya",
  "Georgia": "Gürcistan", "Germany": "Almanya", "Ghana": "Gana", "Greece": "Yunanistan", "Greenland": "Grönland",
  "Guatemala": "Guatemala", "Guinea": "Gine", "Guinea-Bissau": "Gine-Bissau", "Guyana": "Guyana", "Haiti": "Haiti",
  "Honduras": "Honduras", "Hungary": "Macaristan", "Iceland": "İzlanda", "India": "Hindistan", "Indonesia": "Endonezya",
  "Iran": "İran", "Iraq": "Irak", "Ireland": "İrlanda", "Israel": "İsrail", "Italy": "İtalya",
  "Jamaica": "Jamaika", "Japan": "Japonya", "Jordan": "Ürdün", "Kazakhstan": "Kazakistan", "Kenya": "Kenya",
  "Korea": "Kore", "Kuwait": "Kuveyt", "Kyrgyzstan": "Kırgızistan", "Laos": "Laos", "Latvia": "Letonya",
  "Lebanon": "Lübnan", "Lesotho": "Lesoto", "Liberia": "Liberya", "Libya": "Libya", "Lithuania": "Litvanya",
  "Luxembourg": "Lüksemburg", "Madagascar": "Madagaskar", "Malawi": "Malavi", "Malaysia": "Malezya", "Mali": "Mali",
  "Mauritania": "Moritanya", "Mexico": "Meksika", "Moldova": "Moldova", "Mongolia": "Moğolistan", "Montenegro": "Karadağ",
  "Morocco": "Fas", "Mozambique": "Mozambik", "Myanmar": "Myanmar", "Namibia": "Namibya", "Nepal": "Nepal",
  "Netherlands": "Hollanda", "New Caledonia": "Yeni Kaledonya", "New Zealand": "Yeni Zelanda", "Nicaragua": "Nikaragua", "Niger": "Nijer",
  "Nigeria": "Nijerya", "North Korea": "Kuzey Kore", "Norway": "Norveç", "Oman": "Umman", "Pakistan": "Pakistan",
  "Panama": "Panama", "Papua New Guinea": "Papua Yeni Gine", "Paraguay": "Paraguay", "Peru": "Peru", "Philippines": "Filipinler",
  "Poland": "Polonya", "Portugal": "Portekiz", "Puerto Rico": "Porto Riko", "Qatar": "Katar", "Romania": "Romanya",
  "Russia": "Rusya", "Rwanda": "Ruanda", "S. Sudan": "Güney Sudan", "Saudi Arabia": "Suudi Arabistan", "Senegal": "Senegal",
  "Serbia": "Sırbistan", "Sierra Leone": "Sierra Leone", "Slovakia": "Slovakya", "Slovenia": "Slovenya", "Solomon Is.": "Solomon Adaları",
  "Somalia": "Somali", "South Africa": "Güney Afrika", "South Korea": "Güney Kore", "Spain": "İspanya", "Sri Lanka": "Sri Lanka",
  "Sudan": "Sudan", "Suriname": "Surinam", "Sweden": "İsveç", "Switzerland": "İsviçre", "Syria": "Suriye",
  "Taiwan": "Tayvan", "Tajikistan": "Tacikistan", "Tanzania": "Tanzanya", "Thailand": "Tayland", "Timor-Leste": "Doğu Timor",
  "Togo": "Togo", "Trinidad and Tobago": "Trinidad ve Tobago", "Tunisia": "Tunus", "Turkey": "Türkiye", "Turkmenistan": "Türkmenistan",
  "Uganda": "Uganda", "Ukraine": "Ukrayna", "United Arab Emirates": "Birleşik Arap Emirlikleri", "United Kingdom": "Birleşik Krallık", "United States": "Amerika Birleşik Devletleri",
  "Uruguay": "Uruguay", "Uzbekistan": "Özbekistan", "Vanuatu": "Vanuatu", "Venezuela": "Venezuela", "Vietnam": "Vietnam",
  "W. Sahara": "Batı Sahra", "Yemen": "Yemen", "Zambia": "Zambiya", "Zimbabwe": "Zimbabve"
};

const TRANSLATIONS = {
  en: {
    loading: "Loading the World...",
    findCountry: "Find this country:",
    score: "Score",
    attempts: "Attempts",
    skip: "Skip (-50)",
    gameOver: "Game Over!",
    tourComplete: "You've completed your world tour.",
    finalScore: "Final Score",
    accuracy: "Accuracy",
    playAgain: "Play Again",
    dragInfo: "Drag to rotate • Scroll/Pinch to zoom • Click to select",
    correct: "Correct!",
    wrong: "Wrong!",
    thats: "That's",
    was: "That was",
    jokerDouble: "Double Answer",
    jokerForty: "40% Joker",
    jokerPhone: "Phone Joker",
    jokerAudience: "Audience Joker",
    phoneThinking: "Calling a friend...",
    phoneSays: "I think the answer is",
    audienceThinking: "Asking the audience...",
    doubleActive: "Double Answer Active! You have 2 chances.",
    time: "Time",
    tutorialTitle: "Welcome to Globe Quiz Master!",
    tutorialDesc: "Test your geography skills by finding countries on the 3D globe. You have 4 lifelines (jokers) to help you:",
    jokerDoubleDesc: "Gives you 2 chances to pick the correct answer.",
    jokerFortyDesc: "Removes 2 incorrect options from the list.",
    jokerPhoneDesc: "Calls a friend who suggests an answer.",
    jokerAudienceDesc: "Shows the audience poll results.",
    startGame: "Start Game",
    correctAnswers: "Correct Answers",
    wrongAnswers: "Wrong Answers",
    jokerCorrect: "Correct with Joker",
    totalAttempts: "Total Attempts",
    nickname: "Nickname",
    enterNickname: "Enter your nickname",
    watchAd: "Watch Ad & Continue",
    adLoading: "Loading Advertisement...",
    welcomeBack: "Welcome back",
  },
  tr: {
    loading: "Dünya Yükleniyor...",
    findCountry: "Bu ülkeyi bul:",
    score: "Puan",
    attempts: "Deneme",
    skip: "Atla (-50)",
    gameOver: "Oyun Bitti!",
    tourComplete: "Dünya turunu tamamladın.",
    finalScore: "Toplam Puan",
    accuracy: "Doğruluk",
    playAgain: "Tekrar Oyna",
    dragInfo: "Döndürmek için sürükle • Yakınlaşmak için kaydır • Seçmek için tıkla",
    correct: "Doğru!",
    wrong: "Yanlış!",
    thats: "Bu",
    was: "Bu şuydu:",
    jokerDouble: "Çift Cevap",
    jokerForty: "Yüzde Kırk",
    jokerPhone: "Telefon Jokeri",
    jokerAudience: "Seyirci Jokeri",
    phoneThinking: "Arkadaş aranıyor...",
    phoneSays: "Bence cevap",
    audienceThinking: "Seyirciye soruluyor...",
    doubleActive: "Çift Cevap Aktif! 2 hakkınız var.",
    time: "Süre",
    tutorialTitle: "Globe Quiz Master'a Hoş Geldiniz!",
    tutorialDesc: "3D dünya üzerinde ülkeleri bularak coğrafya bilginizi test edin. Size yardımcı olacak 4 jokeriniz var:",
    jokerDoubleDesc: "Doğru cevabı bulmak için size 2 hak verir.",
    jokerFortyDesc: "Seçeneklerden 2 yanlış cevabı eler.",
    jokerPhoneDesc: "Bir arkadaşınızı arar ve size bir cevap önerir.",
    jokerAudienceDesc: "Seyirci oylaması sonuçlarını gösterir.",
    startGame: "Oyuna Başla",
    correctAnswers: "Doğru Cevaplar",
    wrongAnswers: "Yanlış Cevaplar",
    jokerCorrect: "Jokerle Doğru",
    totalAttempts: "Toplam Deneme",
    nickname: "Takma İsim",
    enterNickname: "Takma isminizi girin",
    watchAd: "Reklam İzle ve Devam Et",
    adLoading: "Reklam Yükleniyor...",
    welcomeBack: "Tekrar hoş geldin",
  }
};

type Language = 'en' | 'tr';

export default function App() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [lang, setLang] = useState<Language>('tr');
  const t = TRANSLATIONS[lang];

  const [countries, setCountries] = useState<Country[]>([]);
  const countriesRef = useRef<Country[]>([]);
  const [selectedCountry, setSelectedCountry] = useState<Country | null>(null);
  const selectedCountryRef = useRef<Country | null>(null);
  const [quizOptions, setQuizOptions] = useState<Country[]>([]);
  const [score, setScore] = useState(0);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string; correctId: string } | null>(null);

  // Game state
  const [gameTime, setGameTime] = useState(0);
  const [showTutorial, setShowTutorial] = useState(true);
  const [isGameActive, setIsGameActive] = useState(false);
  const [correctCount, setCorrectCount] = useState(0);
  const [wrongCount, setWrongCount] = useState(0);
  const [correctWithJokerCount, setCorrectWithJokerCount] = useState(0);
  const [nickname, setNickname] = useState('');
  const [isAdLoading, setIsAdLoading] = useState(false);

  // Lifelines state
  const [jokers, setJokers] = useState({
    double: { used: false, active: false, attempt: 1 },
    forty: { used: false, active: false, hiddenIds: [] as string[] },
    phone: { used: false, active: false, suggestionId: null as string | null },
    audience: { used: false, active: false, percentages: {} as Record<string, number> }
  });
  const [isJokerLoading, setIsJokerLoading] = useState<string | null>(null);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };
  
  // Update refs when state changes
  useEffect(() => {
    countriesRef.current = countries;
  }, [countries]);

  useEffect(() => {
    selectedCountryRef.current = selectedCountry;
  }, [selectedCountry]);

  // High-frequency state moved to refs for performance
  const rotationRef = useRef<[number, number, number]>([0, -20, 0]);
  const scaleRef = useRef<number>(250);
  
  const [isLoading, setIsLoading] = useState(true);
  // Memoized stars to prevent flickering and ensure unique keys
  const stars = useMemo(() => 
    [...Array(50)].map((_, i) => ({
      id: `star-${i}`,
      style: {
        width: Math.random() * 2 + 'px',
        height: Math.random() * 2 + 'px',
        top: Math.random() * 100 + '%',
        left: Math.random() * 100 + '%',
      }
    }))
  , []);
  const [gameOver, setGameOver] = useState(false);
  const [solvedCount, setSolvedCount] = useState(0);
  const maxCountries = 50;

  // Timer effect
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isGameActive && !gameOver && !isLoading) {
      interval = setInterval(() => {
        setGameTime(prev => prev + 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isGameActive, gameOver, isLoading]);

  // Memoized graticule
  const graticule = useMemo(() => d3.geoGraticule()(), []);

  // Projection and path generator
  const projection = useMemo(() => 
    d3.geoOrthographic()
      .scale(250) // Initial scale
      .translate([window.innerWidth / 2, window.innerHeight / 2])
      .clipAngle(90)
  , []);

  const pathGenerator = useMemo(() => d3.geoPath(projection), [projection]);

  // Load data
  useEffect(() => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort('timeout'), 20000); // 20s timeout

    const loadData = async () => {
      try {
        const response = await fetch(WORLD_JSON_URL, { signal: controller.signal });
        clearTimeout(timeoutId);
        
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        const worldData = await response.json();
        
        if (!worldData || !worldData.objects || !worldData.objects.countries) {
          throw new Error('Invalid world data structure');
        }

        const featureCollection = feature(worldData, worldData.objects.countries) as any;
        const countriesData = featureCollection.features;
        
        if (!countriesData || !Array.isArray(countriesData)) {
          throw new Error('Failed to extract features from topojson');
        }

        // Map to our structure
        const mappedCountries = countriesData.map((f: any, i: number) => ({
          id: f.id || `country-${i}`,
          name: f.properties?.name || `Unknown-${i}`,
          geometry: f.geometry,
          color: d3.interpolateRainbow(i / countriesData.length)
        }));

        setCountries(mappedCountries);
      } catch (error: any) {
        if (error.name === 'AbortError' || error === 'timeout' || controller.signal.aborted) {
          // Silently ignore aborts (usually cleanup or timeout handled elsewhere)
          return;
        }
        console.error('Failed to load map data:', error);
        setCountries([]);
      } finally {
        // Only set loading to false if we weren't aborted by cleanup
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      }
    };
    
    loadData();
    
    // Safety fallback: if still loading after 25s, stop loading anyway
    const safetyTimeout = setTimeout(() => {
      setIsLoading(prev => {
        if (prev) console.warn('Loading timed out, forcing stop');
        return false;
      });
    }, 25000);

    return () => {
      controller.abort('cleanup');
      clearTimeout(timeoutId);
      clearTimeout(safetyTimeout);
    };
  }, []);

  const getCountryName = useCallback((country: Country) => {
    if (lang === 'tr') {
      return TURKISH_COUNTRY_NAMES[country.name] || country.name;
    }
    return country.name;
  }, [lang]);

  // Render loop
  useEffect(() => {
    if (isLoading) return;

    let animId: number;
    let width = window.innerWidth;
    let height = window.innerHeight;

    const startRender = () => {
      if (!canvasRef.current) {
        animId = requestAnimationFrame(startRender);
        return;
      }

      const canvas = canvasRef.current;
      const context = canvas.getContext('2d', { alpha: false });
      if (!context) return;

      const updateSize = () => {
        width = window.innerWidth;
        height = window.innerHeight;
        canvas.width = width;
        canvas.height = height;
      };
      
      window.addEventListener('resize', updateSize);
      updateSize();

      const render = () => {
        const rotation = rotationRef.current;
        const scale = scaleRef.current;
        const currentCountries = countriesRef.current;
        const currentSelected = selectedCountryRef.current;

        context.clearRect(0, 0, width, height);
        
        const minDim = Math.min(width, height);
        const baseScale = minDim * 0.35;
        const currentScale = baseScale * (scale / 250);
        
        projection.scale(currentScale);
        projection.translate([width / 2, height / 2]);
        projection.rotate(rotation);

        // Draw ocean/sphere background
        context.beginPath();
        context.arc(width / 2, height / 2, currentScale, 0, 2 * Math.PI);
        context.fillStyle = '#0f172a';
        context.fill();
        context.strokeStyle = '#1e293b';
        context.lineWidth = 2;
        context.stroke();

        // Draw graticule (grid lines)
        context.beginPath();
        pathGenerator.context(context)(graticule);
        context.strokeStyle = 'rgba(255, 255, 255, 0.05)';
        context.stroke();

        // Optimized single-pass drawing
        currentCountries.forEach(country => {
          if (country.id === currentSelected?.id) return;

          context.beginPath();
          if (country.isSolved) {
            context.fillStyle = country.isCorrect ? '#059669' : '#991b1b';
            context.globalAlpha = 0.6;
          } else {
            context.fillStyle = country.color;
            context.globalAlpha = 0.8;
          }
          
          pathGenerator.context(context)(country.geometry);
          context.fill();
          
          if (!country.isSolved) {
            context.strokeStyle = 'rgba(255, 255, 255, 0.1)';
            context.lineWidth = 0.5;
            context.stroke();
          }
        });

        if (currentSelected) {
          context.globalAlpha = 1.0;
          context.fillStyle = '#f59e0b';
          context.beginPath();
          pathGenerator.context(context)(currentSelected.geometry);
          context.fill();
          context.strokeStyle = '#ffffff';
          context.lineWidth = 2;
          context.stroke();
        }

        context.globalAlpha = 1.0;
        context.font = 'bold 12px Inter, sans-serif';
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        
        currentCountries.forEach(country => {
          if (country.isSolved) {
            const centroid = d3.geoCentroid(country.geometry);
            const visible = d3.geoDistance(centroid, [-rotation[0], -rotation[1]]) < Math.PI / 2;
            
            if (visible) {
              const pos = projection(centroid);
              if (pos) {
                const text = getCountryName(country);
                const metrics = context.measureText(text);
                
                context.fillStyle = 'rgba(0, 0, 0, 0.5)';
                context.fillRect(pos[0] - metrics.width / 2 - 4, pos[1] - 8, metrics.width + 8, 16);
                
                context.fillStyle = '#ffffff';
                context.fillText(text, pos[0], pos[1]);
              }
            }
          }
        });

        animId = requestAnimationFrame(render);
      };

      animId = requestAnimationFrame(render);

      return () => {
        window.removeEventListener('resize', updateSize);
      };
    };

    const cleanup = startRender();

    return () => {
      if (animId) cancelAnimationFrame(animId);
      if (typeof cleanup === 'function') cleanup();
    };
  }, [isLoading, pathGenerator, projection, graticule, getCountryName]);

  // Interaction handlers
  const handlePointerDown = (e: React.PointerEvent) => {
    const startX = e.clientX;
    const startY = e.clientY;
    const startRotation = [...rotationRef.current] as [number, number, number];
    let lastPinchDist = 0;

    const onPointerMove = (moveEvent: PointerEvent) => {
      // Basic rotation
      const dx = (moveEvent.clientX - startX) / 2;
      const dy = (moveEvent.clientY - startY) / 2;
      rotationRef.current = [
        startRotation[0] + dx,
        startRotation[1] - dy,
        startRotation[2]
      ];
    };

    const onPointerUp = (upEvent: PointerEvent) => {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);

      // If it was a click (not a drag), check for country
      const dist = Math.sqrt(Math.pow(upEvent.clientX - startX, 2) + Math.pow(upEvent.clientY - startY, 2));
      if (dist < 5) {
        handleCountryClick(upEvent.clientX, upEvent.clientY);
      }
    };

    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
  };

  const handleWheel = (e: React.WheelEvent) => {
    const delta = e.deltaY;
    scaleRef.current = Math.min(3000, Math.max(150, scaleRef.current - delta * 0.5));
  };

  // Pinch to zoom for touch devices
  const touchStartRef = useRef<number>(0);
  const handleTouchMove = useCallback((e: TouchEvent) => {
    if (e.touches.length === 2) {
      const dist = Math.hypot(
        e.touches[0].pageX - e.touches[1].pageX,
        e.touches[0].pageY - e.touches[1].pageY
      );
      if (touchStartRef.current > 0) {
        const delta = dist - touchStartRef.current;
        scaleRef.current = Math.min(3000, Math.max(150, scaleRef.current + delta * 2));
      }
      touchStartRef.current = dist;
    }
  }, []);

  const handleTouchEnd = useCallback(() => {
    touchStartRef.current = 0;
  }, []);

  useEffect(() => {
    window.addEventListener('touchmove', handleTouchMove, { passive: false });
    window.addEventListener('touchend', handleTouchEnd);
    return () => {
      window.removeEventListener('touchmove', handleTouchMove);
      window.removeEventListener('touchend', handleTouchEnd);
    };
  }, [handleTouchMove, handleTouchEnd]);

  const handleCountryClick = (x: number, y: number) => {
    if (gameOver || feedback || selectedCountry) return;

    const coords = projection.invert!([x, y]);
    if (!coords) return;

    // Use current rotation from ref for accurate inversion
    projection.rotate(rotationRef.current);
    const clickedCountry = countries.find(c => d3.geoContains(c.geometry, coords));

    if (clickedCountry && !clickedCountry.isSolved) {
      setSelectedCountry(clickedCountry);
      
      // Generate 5 options including the correct one
      const otherCountries = countries.filter(c => c.id !== clickedCountry.id);
      const shuffledOthers = [...otherCountries].sort(() => 0.5 - Math.random());
      const options = [clickedCountry, ...shuffledOthers.slice(0, 4)].sort(() => 0.5 - Math.random());
      
      setQuizOptions(options);
    }
  };

  const handleOptionSelect = (option: Country) => {
    if (!selectedCountry || feedback) return;

    const isCorrect = option.id === selectedCountry.id;
    
    // Handle Double Answer Joker
    if (jokers.double.active && !isCorrect && jokers.double.attempt === 1) {
      setJokers(prev => ({
        ...prev,
        double: { ...prev.double, attempt: 2 }
      }));
      setFeedback({
        type: 'error',
        message: lang === 'tr' ? 'Yanlış! 2. hakkınızı kullanın.' : 'Wrong! Use your 2nd chance.',
        correctId: '' // Don't reveal yet
      });
      setTimeout(() => setFeedback(null), 1500);
      return;
    }

    setFeedback({
      type: isCorrect ? 'success' : 'error',
      message: isCorrect ? `${t.correct} ${t.thats} ${getCountryName(selectedCountry)}!` : `${t.wrong} ${t.was} ${getCountryName(selectedCountry)}.`,
      correctId: selectedCountry.id
    });

    let newScore = score;
    if (isCorrect) {
      newScore += 100;
      setCorrectCount(prev => prev + 1);
      
      // Check if any joker was used for this correct answer
      const anyJokerActive = jokers.double.active || jokers.forty.active || jokers.phone.active || jokers.audience.active;
      if (anyJokerActive) {
        setCorrectWithJokerCount(prev => prev + 1);
      }
    } else {
      newScore -= 100;
      setWrongCount(prev => prev + 1);
    }
    
    setScore(newScore);

    // Mark as solved
    setCountries(prev => prev.map(c => 
      c.id === selectedCountry.id 
        ? { ...c, isSolved: true, isCorrect } 
        : c
    ));

    setSolvedCount(prev => prev + 1);

    setTimeout(() => {
      setFeedback(null);
      setSelectedCountry(null);
      setQuizOptions([]);
      setJokers(prev => ({
        ...prev,
        double: { ...prev.double, active: false, attempt: 1 },
        forty: { ...prev.forty, active: false, hiddenIds: [] },
        phone: { ...prev.phone, active: false, suggestionId: null },
        audience: { ...prev.audience, active: false, percentages: {} }
      }));
      
      if (newScore < 0 || solvedCount + 1 >= maxCountries) {
        setGameOver(true);
      }
    }, 2500);
  };

  const useDoubleJoker = () => {
    if (jokers.double.used || !selectedCountry) return;
    setJokers(prev => ({
      ...prev,
      double: { used: true, active: true, attempt: 1 }
    }));
  };

  const useFortyJoker = () => {
    if (jokers.forty.used || !selectedCountry) return;
    
    const wrongOptions = quizOptions.filter(o => o.id !== selectedCountry.id);
    const hiddenIds = [...wrongOptions].sort(() => 0.5 - Math.random()).slice(0, 2).map(o => o.id);
    
    setJokers(prev => ({
      ...prev,
      forty: { used: true, active: true, hiddenIds }
    }));
  };

  const usePhoneJoker = () => {
    if (jokers.phone.used || !selectedCountry) return;
    
    setIsJokerLoading('phone');
    setTimeout(() => {
      // 70% chance to suggest the correct answer
      const isCorrectSuggestion = Math.random() < 0.7;
      let suggestionId;
      if (isCorrectSuggestion) {
        suggestionId = selectedCountry.id;
      } else {
        const wrongOptions = quizOptions.filter(o => o.id !== selectedCountry.id);
        suggestionId = wrongOptions[Math.floor(Math.random() * wrongOptions.length)].id;
      }
      
      setJokers(prev => ({
        ...prev,
        phone: { used: true, active: true, suggestionId }
      }));
      setIsJokerLoading(null);
    }, 2000);
  };

  const useAudienceJoker = () => {
    if (jokers.audience.used || !selectedCountry) return;
    
    setIsJokerLoading('audience');
    setTimeout(() => {
      const percentages: Record<string, number> = {};
      let remaining = 100;
      
      // Correct answer gets ~60% (as requested, high probability)
      const correctP = Math.floor(Math.random() * 15) + 55; 
      percentages[selectedCountry.id] = correctP;
      remaining -= correctP;
      
      const otherOptions = quizOptions.filter(o => o.id !== selectedCountry.id);
      const wrongCount = otherOptions.length;
      
      // Distribute remaining percentages somewhat evenly among wrong answers
      otherOptions.forEach((opt, i) => {
        if (i === wrongCount - 1) {
          percentages[opt.id] = remaining;
        } else {
          // Average remaining / count, with a small random variance
          const avg = Math.floor(remaining / (wrongCount - i));
          const variance = Math.floor(Math.random() * 6) - 3; // -3 to +3
          const p = Math.max(1, Math.min(remaining - (wrongCount - i - 1), avg + variance));
          percentages[opt.id] = p;
          remaining -= p;
        }
      });
      
      setJokers(prev => ({
        ...prev,
        audience: { used: true, active: true, percentages }
      }));
      setIsJokerLoading(null);
    }, 5000);
  };

  const handleWatchAd = () => {
    setIsAdLoading(true);
    // Simulate AdMob ad loading and watching
    setTimeout(() => {
      setIsAdLoading(false);
      setScore(0); // Reset score to 0 so they can continue
      setGameOver(false);
      setIsGameActive(true);
      setFeedback(null);
      setSelectedCountry(null);
      setQuizOptions([]);
    }, 3000);
  };

  const resetGame = (keepNickname = true) => {
    setScore(0);
    setSolvedCount(0);
    setGameTime(0);
    setCorrectCount(0);
    setWrongCount(0);
    setCorrectWithJokerCount(0);
    setCountries(prev => prev.map(c => ({ ...c, isSolved: false, isCorrect: undefined })));
    setGameOver(false);
    setSelectedCountry(null);
    setQuizOptions([]);
    setFeedback(null);
    setIsGameActive(true);
    if (!keepNickname) {
      setNickname('');
      setShowTutorial(true);
      setIsGameActive(false);
    }
    setJokers({
      double: { used: false, active: false, attempt: 1 },
      forty: { used: false, active: false, hiddenIds: [] },
      phone: { used: false, active: false, suggestionId: null },
      audience: { used: false, active: false, percentages: {} }
    });
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center h-screen bg-slate-950 text-white font-sans">
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
        >
          <GlobeIcon size={48} className="text-blue-500" />
        </motion.div>
        <p className="mt-4 text-slate-400 animate-pulse">{t.loading}</p>
      </div>
    );
  }

  return (
    <div className="relative w-full h-screen overflow-hidden bg-slate-950 text-white select-none touch-none">
      {/* Background Stars */}
      <div className="absolute inset-0 opacity-20 pointer-events-none">
        {stars.map((star) => (
          <div
            key={star.id}
            className="absolute bg-white rounded-full"
            style={star.style}
          />
        ))}
      </div>

      {/* Main Canvas */}
      <canvas
        ref={canvasRef}
        onPointerDown={handlePointerDown}
        onWheel={handleWheel}
        className="w-full h-full cursor-grab active:cursor-grabbing"
      />

      {/* UI Overlay - Top */}
      <div className="absolute top-0 left-0 w-full p-6 flex flex-col items-center pointer-events-none">
        <div className="w-full flex justify-between items-start pointer-events-none">
          {/* Language */}
          <div className="flex gap-2 pointer-events-auto">
            <button
              onClick={() => setLang(lang === 'en' ? 'tr' : 'en')}
              className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 flex items-center gap-2 hover:bg-slate-800 transition-colors"
            >
              <Languages size={18} className="text-blue-400" />
              <span className="text-xs font-bold uppercase">{lang === 'en' ? 'TR' : 'EN'}</span>
            </button>
          </div>

          {/* Jokers */}
          <div className="flex gap-2 pointer-events-auto">
            <button
              onClick={useDoubleJoker}
              disabled={jokers.double.used || !selectedCountry || !!feedback}
              className={cn(
                "bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 flex flex-col items-center gap-1 transition-all",
                jokers.double.used ? "opacity-30 grayscale" : "hover:bg-slate-800 active:scale-95",
                jokers.double.active && "border-blue-500 shadow-[0_0_10px_rgba(59,130,246,0.5)]"
              )}
              title={t.jokerDouble}
            >
              <RotateCcw size={18} className="text-blue-400" />
              <span className="text-[8px] font-bold uppercase">x2</span>
            </button>
            <button
              onClick={useFortyJoker}
              disabled={jokers.forty.used || !selectedCountry || !!feedback}
              className={cn(
                "bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 flex flex-col items-center gap-1 transition-all",
                jokers.forty.used ? "opacity-30 grayscale" : "hover:bg-slate-800 active:scale-95"
              )}
              title={t.jokerForty}
            >
              <Percent size={18} className="text-emerald-400" />
              <span className="text-[8px] font-bold uppercase">40%</span>
            </button>
            <button
              onClick={usePhoneJoker}
              disabled={jokers.phone.used || !selectedCountry || !!feedback}
              className={cn(
                "bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 flex flex-col items-center gap-1 transition-all",
                jokers.phone.used ? "opacity-30 grayscale" : "hover:bg-slate-800 active:scale-95"
              )}
              title={t.jokerPhone}
            >
              <Phone size={18} className="text-purple-400" />
              <span className="text-[8px] font-bold uppercase">TEL</span>
            </button>
            <button
              onClick={useAudienceJoker}
              disabled={jokers.audience.used || !selectedCountry || !!feedback}
              className={cn(
                "bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 flex flex-col items-center gap-1 transition-all",
                jokers.audience.used ? "opacity-30 grayscale" : "hover:bg-slate-800 active:scale-95"
              )}
              title={t.jokerAudience}
            >
              <Users size={18} className="text-yellow-400" />
              <span className="text-[8px] font-bold uppercase">AUD</span>
            </button>
          </div>

          {/* Stats */}
          <div className="flex gap-4 pointer-events-auto">
            {nickname && (
              <div className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl px-4 py-2 flex items-center gap-2">
                <User size={18} className="text-purple-400" />
                <div className="flex flex-col">
                  <span className="text-[10px] uppercase font-bold text-slate-500 leading-none mb-1">{t.nickname}</span>
                  <span className="font-mono text-sm font-bold leading-none truncate max-w-[80px]">{nickname}</span>
                </div>
              </div>
            )}
            <div className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl px-4 py-2 flex items-center gap-2">
              <RotateCcw size={18} className="text-blue-400" />
              <div className="flex flex-col">
                <span className="text-[10px] uppercase font-bold text-slate-500 leading-none mb-1">{t.time}</span>
                <span className="font-mono text-xl font-bold leading-none">{formatTime(gameTime)}</span>
              </div>
            </div>
            <div className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl px-4 py-2 flex items-center gap-2">
              <Trophy size={18} className="text-yellow-500" />
              <div className="flex flex-col">
                <span className="text-[10px] uppercase font-bold text-slate-500 leading-none mb-1">{t.score}</span>
                <span className="font-mono text-xl font-bold leading-none">{score}</span>
              </div>
            </div>
            <div className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl px-4 py-2 flex items-center gap-2">
              <GlobeIcon size={18} className="text-blue-400" />
              <div className="flex flex-col">
                <span className="text-[10px] uppercase font-bold text-slate-500 leading-none mb-1">{t.attempts}</span>
                <span className="font-mono text-xl font-bold leading-none">{solvedCount}</span>
              </div>
            </div>
          </div>

          {/* Zoom Controls */}
          <div className="flex flex-col gap-2 pointer-events-auto">
            <button
              onClick={() => scaleRef.current = Math.min(3000, scaleRef.current + 100)}
              className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 hover:bg-slate-800 transition-colors"
            >
              <ZoomIn size={18} className="text-slate-400" />
            </button>
            <button
              onClick={() => scaleRef.current = Math.max(150, scaleRef.current - 100)}
              className="bg-slate-900/80 backdrop-blur-md border border-slate-800 rounded-xl p-3 hover:bg-slate-800 transition-colors"
            >
              <ZoomOut size={18} className="text-slate-400" />
            </button>
          </div>
        </div>
      </div>

      {/* Quiz Options Overlay */}
      <AnimatePresence>
        {selectedCountry && quizOptions.length > 0 && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.9, y: 20 }}
            className="absolute bottom-24 left-1/2 -translate-x-1/2 w-full max-w-md px-6 pointer-events-auto"
          >
            <div className="bg-slate-900/90 backdrop-blur-xl border border-slate-800 rounded-3xl p-6 shadow-2xl">
              {/* Joker Info */}
              {jokers.double.active && (
                <div className="mb-4 text-center text-blue-400 text-xs font-bold animate-pulse">
                  {t.doubleActive}
                </div>
              )}
              
              {jokers.phone.active && jokers.phone.suggestionId && (
                <div className="mb-4 p-3 bg-purple-500/10 border border-purple-500/30 rounded-xl text-center">
                  <p className="text-[10px] text-purple-400 uppercase font-bold mb-1">{t.phoneSays}</p>
                  <p className="text-lg font-black text-white">
                    {getCountryName(quizOptions.find(o => o.id === jokers.phone.suggestionId)!)}
                  </p>
                </div>
              )}

              {jokers.audience.active && (
                <div className="mb-4 grid grid-cols-5 gap-1 h-20 items-end">
                  {quizOptions.map((opt, i) => (
                    <div key={opt.id} className="flex flex-col items-center gap-1">
                      <span className="text-[10px] font-bold text-yellow-400">{jokers.audience.percentages[opt.id] || 0}%</span>
                      <div 
                        className="w-full bg-yellow-500/50 rounded-t-sm transition-all duration-1000"
                        style={{ height: `${(jokers.audience.percentages[opt.id] || 0) * 0.8}%` }}
                      />
                      <span className="text-[8px] font-bold text-slate-500">{String.fromCharCode(65 + i)}</span>
                    </div>
                  ))}
                </div>
              )}

              <div className="grid grid-cols-1 gap-3">
                {quizOptions.map((option, i) => {
                  const isCorrect = option.id === feedback?.correctId;
                  const isHidden = jokers.forty.hiddenIds.includes(option.id);
                  
                  if (isHidden) return null;

                  return (
                    <button
                      key={option.id}
                      onClick={() => handleOptionSelect(option)}
                      disabled={!!feedback}
                      className={cn(
                        "w-full py-4 px-6 rounded-2xl font-bold text-lg transition-all flex items-center justify-between group",
                        !feedback && "bg-slate-800 hover:bg-slate-700 text-white active:scale-[0.98]",
                        feedback && isCorrect && "bg-emerald-600 text-white shadow-[0_0_20px_rgba(16,185,129,0.4)] animate-pulse",
                        feedback && feedback.type === 'error' && option.id !== feedback.correctId && "opacity-50 grayscale bg-slate-800"
                      )}
                    >
                      <div className="flex items-center gap-3">
                        <span className="text-slate-500 font-mono text-sm">{String.fromCharCode(65 + i)}:</span>
                        <span>{getCountryName(option)}</span>
                      </div>
                      {!feedback && <div className="w-2 h-2 rounded-full bg-blue-500 opacity-0 group-hover:opacity-100 transition-opacity" />}
                      {feedback && isCorrect && <CheckCircle2 size={20} />}
                      {feedback && feedback.type === 'error' && option.id !== feedback.correctId && <XCircle size={20} className="opacity-20" />}
                    </button>
                  );
                })}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Joker Loading Overlay */}
      <AnimatePresence>
        {isJokerLoading && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center z-40"
          >
            <div className="flex flex-col items-center gap-4">
              <motion.div
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              >
                {isJokerLoading === 'phone' ? <Phone size={48} className="text-purple-400" /> : <Users size={48} className="text-yellow-400" />}
              </motion.div>
              <p className="text-xl font-bold animate-pulse">
                {isJokerLoading === 'phone' ? t.phoneThinking : t.audienceThinking}
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Feedback Overlay */}
      <AnimatePresence>
        {feedback && (
          <motion.div
            initial={{ opacity: 0, y: 20, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 1.1 }}
            className="absolute inset-0 flex items-center justify-center pointer-events-none"
          >
            <div className={cn(
              "px-8 py-6 rounded-3xl flex flex-col items-center gap-4 shadow-2xl backdrop-blur-xl border-2",
              feedback.type === 'success' ? "bg-emerald-500/20 border-emerald-500/50" : "bg-rose-500/20 border-rose-500/50"
            )}>
              {feedback.type === 'success' ? (
                <CheckCircle2 size={64} className="text-emerald-400" />
              ) : (
                <XCircle size={64} className="text-rose-400" />
              )}
              <p className="text-2xl font-bold text-center">{feedback.message}</p>
              <p className={cn(
                "text-4xl font-black",
                feedback.type === 'success' ? "text-emerald-400" : "text-rose-400"
              )}>
                {feedback.type === 'success' ? '+100' : '-100'}
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Tutorial Modal */}
      <AnimatePresence>
        {showTutorial && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-slate-950/90 backdrop-blur-xl flex items-center justify-center p-6 z-[100]"
          >
            <motion.div
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              className="bg-slate-900 border border-slate-800 p-6 rounded-3xl max-w-lg w-full flex flex-col gap-4 shadow-2xl"
            >
              <div className="text-center">
                <GlobeIcon size={48} className="text-blue-500 mx-auto mb-3" />
                <h2 className="text-2xl font-black mb-1">{t.tutorialTitle}</h2>
                <p className="text-slate-400 text-xs mb-3">{t.tutorialDesc}</p>
                
                {/* Nickname Input */}
                <div className="bg-slate-800/50 p-3 rounded-2xl border border-slate-700 mb-1">
                  <p className="text-[10px] text-slate-400 uppercase font-bold mb-1 text-left">{t.nickname}</p>
                  <input
                    type="text"
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    placeholder={t.enterNickname}
                    className="w-full bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 transition-colors"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-2">
                <div className="flex items-start gap-3 p-3 bg-slate-800/50 rounded-2xl">
                  <div className="p-1.5 bg-blue-500/20 rounded-lg">
                    <RotateCcw size={16} className="text-blue-400" />
                  </div>
                  <div>
                    <p className="font-bold text-xs">{t.jokerDouble}</p>
                    <p className="text-[10px] text-slate-400 leading-tight">{t.jokerDoubleDesc}</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 bg-slate-800/50 rounded-2xl">
                  <div className="p-1.5 bg-emerald-500/20 rounded-lg">
                    <Percent size={16} className="text-emerald-400" />
                  </div>
                  <div>
                    <p className="font-bold text-xs">{t.jokerForty}</p>
                    <p className="text-[10px] text-slate-400 leading-tight">{t.jokerFortyDesc}</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 bg-slate-800/50 rounded-2xl">
                  <div className="p-1.5 bg-purple-500/20 rounded-lg">
                    <Phone size={16} className="text-purple-400" />
                  </div>
                  <div>
                    <p className="font-bold text-xs">{t.jokerPhone}</p>
                    <p className="text-[10px] text-slate-400 leading-tight">{t.jokerPhoneDesc}</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 bg-slate-800/50 rounded-2xl">
                  <div className="p-1.5 bg-yellow-500/20 rounded-lg">
                    <Users size={16} className="text-yellow-400" />
                  </div>
                  <div>
                    <p className="font-bold text-xs">{t.jokerAudience}</p>
                    <p className="text-[10px] text-slate-400 leading-tight">{t.jokerAudienceDesc}</p>
                  </div>
                </div>
              </div>

              <button
                onClick={() => {
                  if (!nickname.trim()) return;
                  setShowTutorial(false);
                  setIsGameActive(true);
                }}
                disabled={!nickname.trim()}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold py-3 rounded-2xl transition-all active:scale-95 shadow-lg shadow-blue-600/20"
              >
                {t.startGame}
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Game Over Modal */}
      <AnimatePresence>
        {gameOver && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="absolute inset-0 bg-slate-950/90 backdrop-blur-xl flex items-center justify-center p-6 z-50"
          >
            <motion.div
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              className="bg-slate-900 border border-slate-800 p-6 rounded-3xl max-w-md w-full flex flex-col items-center gap-4 shadow-2xl"
            >
              <Trophy size={56} className="text-yellow-500" />
              <div className="text-center">
                <h2 className="text-2xl font-black mb-1">{t.gameOver}</h2>
                <p className="text-slate-400 text-sm">{t.tourComplete}</p>
              </div>
              
              <div className="grid grid-cols-2 gap-2 w-full">
                <div className="bg-slate-800/50 p-3 rounded-2xl text-center col-span-2">
                  <p className="text-[10px] text-slate-400 uppercase font-bold mb-0.5">{t.finalScore}</p>
                  <p className="text-3xl font-black text-blue-400">{score}</p>
                </div>
                <div className="bg-slate-800/50 p-3 rounded-2xl text-center">
                  <p className="text-[10px] text-slate-400 uppercase font-bold mb-0.5">{t.time}</p>
                  <p className="text-lg font-black text-white">{formatTime(gameTime)}</p>
                </div>
                <div className="bg-slate-800/50 p-3 rounded-2xl text-center">
                  <p className="text-[10px] text-slate-400 uppercase font-bold mb-0.5">{t.totalAttempts}</p>
                  <p className="text-lg font-black text-white">{solvedCount}</p>
                </div>
                <div className="bg-slate-800/50 p-3 rounded-2xl text-center">
                  <p className="text-[10px] text-emerald-400 uppercase font-bold mb-0.5">{t.correctAnswers}</p>
                  <p className="text-lg font-black text-emerald-400">{correctCount}</p>
                </div>
                <div className="bg-slate-800/50 p-3 rounded-2xl text-center">
                  <p className="text-[10px] text-rose-400 uppercase font-bold mb-0.5">{t.wrongAnswers}</p>
                  <p className="text-lg font-black text-rose-400">{wrongCount}</p>
                </div>
                <div className="bg-slate-800/50 p-3 rounded-2xl text-center col-span-2">
                  <p className="text-[10px] text-purple-400 uppercase font-bold mb-0.5">{t.jokerCorrect}</p>
                  <p className="text-lg font-black text-purple-400">{correctWithJokerCount}</p>
                </div>
              </div>

              <div className="flex flex-col gap-2 w-full">
                {score < 0 && (
                  <button
                    onClick={handleWatchAd}
                    disabled={isAdLoading}
                    className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-3 rounded-2xl flex items-center justify-center gap-2 transition-all active:scale-95 shadow-lg shadow-emerald-600/20"
                  >
                    <PlayCircle size={18} />
                    {isAdLoading ? t.adLoading : t.watchAd}
                  </button>
                )}
                <button
                  onClick={() => resetGame(true)}
                  className="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-3 rounded-2xl flex items-center justify-center gap-2 transition-all active:scale-95 shadow-lg shadow-blue-600/20"
                >
                  <RotateCcw size={18} />
                  {t.playAgain}
                </button>
                <button
                  onClick={() => resetGame(false)}
                  className="w-full bg-slate-800 hover:bg-slate-700 text-white font-bold py-3 rounded-2xl flex items-center justify-center gap-2 transition-all active:scale-95"
                >
                  <User size={18} />
                  {lang === 'tr' ? 'Takma İsmi Değiştir' : 'Change Nickname'}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Instructions - Bottom */}
      <div className="absolute bottom-6 left-0 w-full flex justify-center pointer-events-none">
        <p className="bg-slate-900/50 backdrop-blur-sm px-4 py-2 rounded-full text-xs text-slate-400 border border-slate-800">
          {t.dragInfo}
        </p>
      </div>
    </div>
  );
}
