// Arabic / English / French translations (shared with the Android app) + TV-only strings.
import base from './strings.base.json';

const extra = {
  en: {
    catchup_unavailable: 'Catch-up is not available for this programme', channel_list: 'Channel list', channels: 'Channels', choose_channel: 'Choose a channel',
    delete_profile_q: 'Delete this profile?', edit_profile: 'Edit profile', epg_url_optional: 'EPG (XMLTV) link — optional', fill_required: 'Please fill in the required fields',
    link_split: 'Link detected: server, username and password filled in', next_episode: 'Next episode', options: 'Options',
    playlist_hint: 'OK: use this playlist · Hold OK: edit, refresh or delete', remind_me: 'Remind me', remove_my_list: 'Remove from My list',
    resume: 'Resume', search_min: 'Type at least 2 letters', sleep_set: 'Sleep timer: {0} min', start_over: 'Start over', watch_catchup: 'Watch (catch-up)',
    profile_hint: 'Hold OK on a profile to edit or delete it.',
    exit_app: 'Exit Z-Vision?', exit: 'Exit', remote_help: 'Remote control',
    help_ok: 'OK: open / channel list', help_updown: '▲▼ or CH+/CH−: change channel', help_right: '▶ / MENU: options',
    help_back: 'BACK: return (the channel keeps playing small)', help_colors: 'Red: favorite · Green: guide · Yellow: search · Blue: multi-view',
    help_long: 'Hold OK: more options', press_back_exit: 'Press BACK again to exit',
    player_engine: 'Player engine', engine_auto: 'Automatic', engine_html5: 'Web player (HLS/TS)', engine_native: 'Samsung native player (AVPlay)',
    reminder_now: 'Starting now', watch: 'Watch', later: 'Later',
    recording_not_supported: 'Recording is not available on this TV',
    open_failed: 'Couldn\'t open this app',
    store_opened: 'Opening the app store…',
    keyboard_hint: 'Press OK to type',
    mac_warning: 'Some MAC portals block TV web apps. If it fails, ask your provider for Xtream or M3U details.',
    pictures_tv_hint: 'Paste a direct image link (ends with .jpg/.png). You can also use a short link service to type it faster.',
    cast_hint: 'Use the Magic Remote pointer or the arrow keys.',
    multiview_limit: 'Some TVs can only decode 2 videos at once',
    loading: 'Loading…', welcome: 'Welcome to Z-Vision', welcome_sub: 'Add your IPTV subscription to start watching',
    tv_apps_hint: 'OK: open (or install). Hold OK: add/remove from the home screen.',
    fav_hint: 'Red button: add to favorites',
  },
  ar: {
    catchup_unavailable: 'إعادة المشاهدة غير متاحة لهذا البرنامج', channel_list: 'قائمة القنوات', channels: 'القنوات', choose_channel: 'اختر قناة',
    delete_profile_q: 'حذف هذا الملف؟', edit_profile: 'تعديل الملف', epg_url_optional: 'رابط دليل البرامج (XMLTV) — اختياري', fill_required: 'يرجى ملء الحقول المطلوبة',
    link_split: 'تم التعرّف على الرابط: عُبّئ الخادم واسم المستخدم وكلمة المرور', next_episode: 'الحلقة التالية', options: 'الخيارات',
    playlist_hint: 'OK: استخدام هذه القائمة · الضغط المطوّل: تعديل أو تحديث أو حذف', remind_me: 'ذكّرني', remove_my_list: 'إزالة من قائمتي',
    resume: 'متابعة', search_min: 'اكتب حرفين على الأقل', sleep_set: 'مؤقت النوم: {0} دقيقة', start_over: 'من البداية', watch_catchup: 'مشاهدة (إعادة)',
    profile_hint: 'اضغط مطولًا على الملف لتعديله أو حذفه.',
    exit_app: 'الخروج من Z-Vision؟', exit: 'خروج', remote_help: 'أزرار الريموت',
    help_ok: 'OK: فتح / قائمة القنوات', help_updown: '▲▼ أو CH+/CH−: تغيير القناة', help_right: '▶ / القائمة: الخيارات',
    help_back: 'رجوع: العودة (تبقى القناة تعمل في نافذة صغيرة)', help_colors: 'الأحمر: المفضلة · الأخضر: الدليل · الأصفر: البحث · الأزرق: المشاهدة المتعددة',
    help_long: 'الضغط المطوّل على OK: خيارات إضافية', press_back_exit: 'اضغط رجوع مرة أخرى للخروج',
    player_engine: 'محرّك التشغيل', engine_auto: 'تلقائي', engine_html5: 'مشغّل الويب (HLS/TS)', engine_native: 'مشغّل سامسونج الأصلي (AVPlay)',
    reminder_now: 'يبدأ الآن', watch: 'مشاهدة', later: 'لاحقًا',
    recording_not_supported: 'التسجيل غير متاح على هذا التلفاز',
    open_failed: 'تعذّر فتح هذا التطبيق',
    store_opened: 'جارٍ فتح متجر التطبيقات…',
    keyboard_hint: 'اضغط OK للكتابة',
    mac_warning: 'بعض بوابات MAC تحظر تطبيقات التلفاز. إن فشل الاتصال اطلب من المزوّد بيانات Xtream أو رابط M3U.',
    pictures_tv_hint: 'الصق رابطًا مباشرًا لصورة (ينتهي بـ ‎.jpg أو ‎.png). يمكنك استخدام رابط مختصر لتسهيل الكتابة.',
    cast_hint: 'استخدم مؤشر Magic Remote أو أزرار الأسهم.',
    multiview_limit: 'بعض التلفزيونات لا تشغّل أكثر من فيديوهين في الوقت نفسه',
    loading: 'جارٍ التحميل…', welcome: 'مرحبًا بك في Z-Vision', welcome_sub: 'أضف اشتراك IPTV لتبدأ المشاهدة',
    tv_apps_hint: 'OK: فتح (أو تثبيت). الضغط المطوّل على OK: إضافة/إزالة من الرئيسية.',
    fav_hint: 'الزر الأحمر: إضافة إلى المفضلة',
  },
  fr: {
    catchup_unavailable: 'Replay indisponible pour ce programme', channel_list: 'Liste des chaînes', channels: 'Chaînes', choose_channel: 'Choisir une chaîne',
    delete_profile_q: 'Supprimer ce profil ?', edit_profile: 'Modifier le profil', epg_url_optional: 'Lien EPG (XMLTV) — facultatif', fill_required: 'Veuillez remplir les champs obligatoires',
    link_split: 'Lien détecté : serveur, identifiant et mot de passe remplis', next_episode: 'Épisode suivant', options: 'Options',
    playlist_hint: 'OK : utiliser cette playlist · Maintenir OK : modifier, actualiser ou supprimer', remind_me: 'Me le rappeler', remove_my_list: 'Retirer de Ma liste',
    resume: 'Reprendre', search_min: 'Tapez au moins 2 lettres', sleep_set: 'Mise en veille : {0} min', start_over: 'Depuis le début', watch_catchup: 'Regarder (replay)',
    profile_hint: 'Maintenez OK sur un profil pour le modifier ou le supprimer.',
    exit_app: 'Quitter Z-Vision ?', exit: 'Quitter', remote_help: 'Télécommande',
    help_ok: 'OK : ouvrir / liste des chaînes', help_updown: '▲▼ ou CH+/CH− : changer de chaîne', help_right: '▶ / MENU : options',
    help_back: 'RETOUR : revenir (la chaîne continue en petit)', help_colors: 'Rouge : favori · Vert : guide · Jaune : recherche · Bleu : multi-écran',
    help_long: 'Maintenir OK : plus d\'options', press_back_exit: 'Appuyez encore sur RETOUR pour quitter',
    player_engine: 'Moteur de lecture', engine_auto: 'Automatique', engine_html5: 'Lecteur web (HLS/TS)', engine_native: 'Lecteur natif Samsung (AVPlay)',
    reminder_now: 'Commence maintenant', watch: 'Regarder', later: 'Plus tard',
    recording_not_supported: 'L\'enregistrement n\'est pas disponible sur ce téléviseur',
    open_failed: 'Impossible d\'ouvrir cette application',
    store_opened: 'Ouverture de la boutique…',
    keyboard_hint: 'Appuyez sur OK pour écrire',
    mac_warning: 'Certains portails MAC bloquent les applis TV web. En cas d\'échec, demandez les identifiants Xtream ou M3U.',
    pictures_tv_hint: 'Collez un lien direct vers une image (.jpg/.png). Un lien raccourci est plus rapide à taper.',
    cast_hint: 'Utilisez le pointeur Magic Remote ou les flèches.',
    multiview_limit: 'Certains téléviseurs ne décodent que 2 vidéos à la fois',
    loading: 'Chargement…', welcome: 'Bienvenue sur Z-Vision', welcome_sub: 'Ajoutez votre abonnement IPTV pour commencer',
    tv_apps_hint: 'OK : ouvrir (ou installer). Maintenir OK : ajouter/retirer de l\'accueil.',
    fav_hint: 'Bouton rouge : ajouter aux favoris',
  },
};

let lang = 'en';
let dict = Object.assign({}, base.en, extra.en);

export function systemLang() {
  const l = (navigator.language || 'en').slice(0, 2).toLowerCase();
  return l === 'ar' || l === 'fr' ? l : 'en';
}

export function setLang(code) {
  lang = code || systemLang();
  if (!base[lang]) lang = 'en';
  dict = Object.assign({}, base.en, extra.en, base[lang], extra[lang]);
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
}

export const getLang = () => lang;
export const isRtl = () => lang === 'ar';

/** Translates a key. Placeholders {0}, {1}… are replaced by args. */
export function t(key, ...args) {
  let s = dict[key];
  if (s == null) { s = key; }
  for (let i = 0; i < args.length; i++) s = s.split('{' + i + '}').join(String(args[i]));
  return s;
}
