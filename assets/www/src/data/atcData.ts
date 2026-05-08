export interface Drug {
  sn: number;
  name: string;
  atc: string; // Top level ATC category
  descriptionTr: string;
  descriptionEn: string;
}

export interface ATCCategory {
  id: string;
  nameTr: string;
  nameEn: string;
  color: string;
  icon: string;
}

export const ATC_CATEGORIES: ATCCategory[] = [
  { id: 'A', nameTr: 'Sindirim Sistemi ve Metabolizma', nameEn: 'Alimentary Tract and Metabolism', color: 'bg-green-500', icon: 'Apple' },
  { id: 'B', nameTr: 'Kan ve Kan Yapıcı Organlar', nameEn: 'Blood and Blood Forming Organs', color: 'bg-red-600', icon: 'Droplet' },
  { id: 'C', nameTr: 'Kardiyovasküler Sistem', nameEn: 'Cardiovascular System', color: 'bg-red-400', icon: 'Heart' },
  { id: 'D', nameTr: 'Dermatolojik Preparatlar', nameEn: 'Dermatologicals', color: 'bg-orange-400', icon: 'Thermometer' },
  { id: 'G', nameTr: 'Genito-Üriner Sistem ve Cinsiyet Hormonları', nameEn: 'Genito-Urinary System and Sex Hormones', color: 'bg-pink-400', icon: 'Users' },
  { id: 'H', nameTr: 'Sistemik Hormonal Preparatlar', nameEn: 'Systemic Hormonal Preparations', color: 'bg-purple-400', icon: 'Activity' },
  { id: 'J', nameTr: 'Sistemik Antiinfektifler', nameEn: 'Anti-infectives for Systemic Use', color: 'bg-blue-600', icon: 'ShieldAlert' },
  { id: 'L', nameTr: 'Antineoplastik ve İmmünomodülatörler', nameEn: 'Antineoplastic and Immunomodulating Agents', color: 'bg-indigo-600', icon: 'Microscope' },
  { id: 'M', nameTr: 'Kas-İskelet Sistemi', nameEn: 'Musculo-Skeletal System', color: 'bg-amber-600', icon: 'Bone' },
  { id: 'N', nameTr: 'Sinir Sistemi', nameEn: 'Nervous System', color: 'bg-blue-400', icon: 'Brain' },
  { id: 'P', nameTr: 'Antiparaziter Ürünler', nameEn: 'Antiparasitic Products', color: 'bg-teal-500', icon: 'Bug' },
  { id: 'R', nameTr: 'Solunum Sistemi', nameEn: 'Respiratory System', color: 'bg-sky-400', icon: 'Wind' },
  { id: 'S', nameTr: 'Duyu Organları', nameEn: 'Sensory Organs', color: 'bg-yellow-400', icon: 'Eye' },
  { id: 'V', nameTr: 'Çeşitli (Laboratuvar ve Diğer)', nameEn: 'Various', color: 'bg-gray-500', icon: 'FlaskConical' },
];

export const DRUGS: Drug[] = [
  { sn: 1, name: 'KAL. SULFURICUM', atc: 'V', descriptionTr: 'Potasyum Sülfat - Kimyasal reaktif.', descriptionEn: 'Potassium Sulfate - Chemical reagent.' },
  { sn: 2, name: 'ÄTHANOL 45%', atc: 'D', descriptionTr: 'Etil Alkol - Dezenfektan.', descriptionEn: 'Ethyl Alcohol - Disinfectant.' },
  { sn: 3, name: 'MAGNES. SULFUR. SICC.', atc: 'A', descriptionTr: 'Kurutulmuş Magnezyum Sülfat - Müshil etkili.', descriptionEn: 'Dried Magnesium Sulfate - Laxative effect.' },
  { sn: 4, name: 'PILULAE LAXANT. RF', atc: 'A', descriptionTr: 'Müshil Hapları - Sindirimi kolaylaştırır.', descriptionEn: 'Laxative Pills - Aids digestion.' },
  { sn: 7, name: 'ACID. ASCORBIC.', atc: 'A', descriptionTr: 'C Vitamini - Bağışıklık güçlendirici.', descriptionEn: 'Vitamin C - Immune booster.' },
  { sn: 11, name: 'ANAESTHESIN', atc: 'N', descriptionTr: 'Benzokain - Yerel anestezik.', descriptionEn: 'Benzocaine - Local anesthetic.' },
  { sn: 12, name: 'FERR. PYROPHOSPHOR.', atc: 'B', descriptionTr: 'Demir Pirofosfat - Kan yapıcı.', descriptionEn: 'Iron Pyrophosphate - Blood former.' },
  { sn: 14, name: 'TINCT. OPII SIMPL.', atc: 'N', descriptionTr: 'Afyon Tentürü - Ağrı kesici.', descriptionEn: 'Opium Tincture - Analgesic.' },
  { sn: 22, name: 'COFFEIN. CITRIC.', atc: 'N', descriptionTr: 'Kafein Sitrat - MSS uyarıcı.', descriptionEn: 'Caffeine Citrate - CNS stimulant.' },
  { sn: 31, name: 'AETHER', atc: 'N', descriptionTr: 'Dietil Eter - Tarihsel genel anestezi.', descriptionEn: 'Diethyl Ether - Historical general anesthesia.' },
  { sn: 52, name: 'TINCT. VALERIANAE', atc: 'N', descriptionTr: 'Kediotu Tentürü - Sakinleştirici.', descriptionEn: 'Valerian Tincture - Sedative.' },
  { sn: 58, name: 'VASELIN. ALB.', atc: 'D', descriptionTr: 'Beyaz Vazelin - Koruyucu merhem bazı.', descriptionEn: 'White Vaseline - Protective ointment base.' },
  { sn: 63, name: 'CARBO MEDICINALIS', atc: 'A', descriptionTr: 'Tıbbi Kömür - Zehirlenme karşıtı.', descriptionEn: 'Medicinal Charcoal - Anti-poison.' },
  { sn: 70, name: 'EXTR. CASCAR. SAGR. FLD.', atc: 'A', descriptionTr: 'Barut Ağacı Ekstresi - Müshil.', descriptionEn: 'Cascara Sagrada Extract - Laxative.' },
  { sn: 91, name: 'TINCT. ALOES', atc: 'A', descriptionTr: 'Aloe Tentürü - Sindirim destekleyici.', descriptionEn: 'Aloe Tincture - Digestive support.' },
  { sn: 97, name: 'ACID. ACETYLOSALICYLIC.', atc: 'N', descriptionTr: 'Aspirin - Ağrı kesici ve ateş düşürücü.', descriptionEn: 'Aspirin - Analgesic and antipyretic.' },
  { sn: 111, name: 'TINCT. BELLADONNAE', atc: 'N', descriptionTr: 'Güzelavrat Otu Tentürü - Antispazmodik.', descriptionEn: 'Belladonna Tincture - Antispasmodic.' },
  { sn: 126, name: 'TERPINUM HYDRATUM', atc: 'R', descriptionTr: 'Terpin Hidrat - Balgam söktürücü.', descriptionEn: 'Terpin Hydrate - Expectorant.' },
  { sn: 133, name: 'KAL. SULFOGUAJACOL.', atc: 'R', descriptionTr: 'Potasyum Guaiakolsülfonat - Öksürük giderici.', descriptionEn: 'Potassium Sulfoguaiacolate - Cough relief.' },
  { sn: 149, name: 'TABULETT. ACID. ACETYLOSALIC. 0,5', atc: 'N', descriptionTr: 'Standart Aspirin Tableti.', descriptionEn: 'Standard Aspirin Tablet.' },
  { sn: 156, name: 'UNGT. ZINCI', atc: 'D', descriptionTr: 'Çinko Merhemi - Cilt koruyucu.', descriptionEn: 'Zinc Ointment - Skin protectant.' },
  { sn: 159, name: 'UNGT. ICHTHYOL 10%', atc: 'D', descriptionTr: 'İhtiyol Merhemi - Egzama ve sivilce için.', descriptionEn: 'Ichthyol Ointment - For eczema and acne.' },
  { sn: 164, name: 'LANOLIN.', atc: 'D', descriptionTr: 'Yün Yağı - Yumuşatıcı merhem bazı.', descriptionEn: 'Lanolin - Emollient ointment base.' },
  { sn: 167, name: 'BORAX PLV.', atc: 'D', descriptionTr: 'Boraks Tozu - Antiseptik ve temizleyici.', descriptionEn: 'Borax Powder - Antiseptic and cleaner.' },
  { sn: 172, name: 'EXTR. SENEGAE FLD.', atc: 'R', descriptionTr: 'Senega Ekstresi - Göğüs yumuşatıcı.', descriptionEn: 'Senega Extract - Chest softener.' },
  { sn: 176, name: 'TINCT. ANTICHOLERICA', atc: 'A', descriptionTr: 'Antikolera Tentürü - Şiddetli ishal için.', descriptionEn: 'Anticholera Tincture - For severe diarrhea.' },
  { sn: 182, name: 'SUCC. LIQUIR. DEP. PULV.', atc: 'R', descriptionTr: 'Meyan Kökü Özütü - Öksürük giderici.', descriptionEn: 'Licorice Extract - Cough relief.' },
  { sn: 194, name: 'EXTR. DROSERAE FLD.', atc: 'R', descriptionTr: 'Güneşgülü Ekstresi - Öksürük otu.', descriptionEn: 'Drosera Extract - Cough herb.' },
  { sn: 203, name: 'CIBAZOL', atc: 'J', descriptionTr: 'Sülfatiyazol - Eski bir antibiyotik.', descriptionEn: 'Sulfathiazole - An old antibiotic.' },
  { sn: 216, name: 'FLOR. TILIAE', atc: 'R', descriptionTr: 'Ihlamur Çiçeği - Rahatlatıcı bitki çayı.', descriptionEn: 'Linden Flower - Soothing herbal tea.' },
  { sn: 227, name: 'PORSELEN HAVAN VE ELİ', atc: 'V', descriptionTr: 'Eczacılık laboratuvar aracı.', descriptionEn: 'Pharmacy lab tool.' },
  { sn: 234, name: 'MAJİSTRAL ANALİTİK TERAZİ', atc: 'V', descriptionTr: 'Hassas tartım cihazı.', descriptionEn: 'Precision weighing device.' },
  { sn: 245, name: 'TAŞINABİLİR MONOKÜLER IŞIK MİKROSKOBU', atc: 'V', descriptionTr: 'Laboratuvar analiz gereci.', descriptionEn: 'Lab analysis tool.' },
  { sn: 256, name: 'HAP KESME MAKİNESİ', atc: 'V', descriptionTr: 'İlaç bölme aparatı.', descriptionEn: 'Drug splitting device.' },
];
