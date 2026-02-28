
export enum NizamType {
  AYRIK = 'AYRIK',
  BITISIK = 'BITISIK'
}

export interface ArsaBilgileri {
  alan: number;
  il: string;
  ilce: string;
  mahalle: string;
  ada: string;
  parsel: string;
  nizam: NizamType;
  cekmeOn: number;
  cekmeYan1: number;
  cekmeYan2: number;
  cekmeArka: number;
  taks: number;
  kaks: number;
  useKaks: boolean;
  manuelTabanAlani: number;
  katAdedi: number;
  daireSayisiTercihi: number;
  birimMaliyet: number; // TL/m2
  birimSatisFiyati: number; // TL/m2 (Piyasa Verisi)
  arsaBedeli?: number; // TL
}

export interface AnalizDetay {
  adet: number;
  tip: string;
  m2: number;
}

export interface MalzemeTahmini {
  beton: number; // m3
  demir: number; // ton
  tugla: number; // adet
}

export interface HesapSonuclari {
  kaks: number;
  tabanAlani: number;
  zeminBrut: number;
  normalKatBrut: number;
  toplamInsaatAlani: number;
  toplamKat: number;
  toplamDaire: number;
  toplamMaliyet: number;
  toplamMaliyetArsaDahil?: number;
  toplamGelir: number;
  toplamKar: number;
  birimDaireAnalizi: AnalizDetay[];
  malzeme: MalzemeTahmini;
}
