const express = require('express');
const {
  sessions,
  messages,
  documents,
  documentPages,
  aiProfiles,
  quizAttempts,
  sentReports,
  nowIso
} = require('../data');
const { authMiddleware } = require('../middleware');
const { buildPdf, coverColorFor, initialsFor } = require('../reports');
const { CORE_PROMPT, CAPABILITIES, assembledSlotText } = require('../aiProfiles');

const router = express.Router();

const SUPPORTED_LANGUAGES = ['en', 'es', 'fr'];
const LANGUAGE_NAMES = { en: 'English', es: 'Spanish', fr: 'French' };
const RUBRIC_SLOT = { easy: 'rubric_easy', medium: 'rubric_medium', hard: 'rubric_hard' };
const RUBRIC_LEVEL_LABEL = { easy: 'Easy', medium: 'Medium', hard: 'Advanced' };

// Friendlier labels for the per-function layers in the context view.
const FUNCTION_LAYER_LABEL = {
  fn_quiz_question: 'When you ask for a quiz question',
  fn_answer_grading: 'When BooKI grades a quiz answer',
  fn_summary: 'When you ask for a summary',
  fn_explain: 'When you ask BooKI to explain a passage',
  fn_mnemonic: 'When you ask for a memory aid'
};

function profileFor(session) {
  return aiProfiles.find((p) => p.id === session.aiProfileId) || null;
}

function slot(profile, key) {
  return profile ? profile.slots.find((s) => s.key === key) || null : null;
}

function slotContent(profile, key) {
  const s = slot(profile, key);
  return s && s.text ? s.text : null;
}

function enabledCapabilitiesFor(session) {
  const profile = profileFor(session);
  return profile && Array.isArray(profile.enabledCapabilities)
    ? profile.enabledCapabilities
    : [...CAPABILITIES];
}

// Every part of the instructions BooKI reads before answering — not just the
// editable ones. `group` lets the client fold the heavy parts away.
function buildContext(session) {
  const lang = SUPPORTED_LANGUAGES.includes(session.language) ? session.language : 'en';
  const difficulty = ['easy', 'medium', 'hard'].includes(session.difficulty) ? session.difficulty : 'medium';
  const profile = profileFor(session);
  const doc = documents.find((d) => d.id === session.documentId);
  const source = profile ? `AI profile "${profile.name}"` : 'AI profile';
  const enabled = enabledCapabilitiesFor(session);

  const layers = [
    { key: 'core', group: 'core', label: 'BooKI core', editable: false, source: 'App', content: CORE_PROMPT },
    {
      key: 'rubric',
      group: 'difficulty',
      label: `Difficulty — ${RUBRIC_LEVEL_LABEL[difficulty]}`,
      editable: true,
      source,
      content: slotContent(profile, RUBRIC_SLOT[difficulty])
    },
    { key: 'persona', group: 'persona', label: 'Persona', editable: true, source, content: slotContent(profile, 'persona') },
    {
      key: 'reader_context',
      group: 'reader',
      label: 'Reader context',
      editable: true,
      source,
      content: slotContent(profile, 'reader_context')
    }
  ];

  for (const key of Object.keys(FUNCTION_LAYER_LABEL)) {
    const s = slot(profile, key);
    layers.push({
      key,
      group: 'functions',
      label: FUNCTION_LAYER_LABEL[key],
      editable: true,
      source,
      content: s ? assembledSlotText(s) : null
    });
  }

  const routingSlot = slot(profile, 'capability_routing');
  layers.push({
    key: 'capability_routing',
    group: 'routing',
    label: 'When BooKI acts on its own in chat',
    editable: true,
    source,
    content:
      (routingSlot ? assembledSlotText(routingSlot) + '\n\n' : '') +
      `Enabled here: ${enabled.length ? enabled.join(', ') : 'none'}.`
  });

  layers.push({
    key: 'session',
    group: 'session',
    label: 'This session',
    editable: false,
    source: 'Session',
    content:
      `Document: ${doc ? doc.title : 'Unknown'}\n` +
      `Pages ${session.startPage}–${session.endPage} · you are on page ${session.currentPage}\n` +
      `The text of pages ${session.startPage}–${session.endPage} is included in every answer.`
  });

  return {
    aiProfileId: profile ? profile.id : null,
    aiProfileName: profile ? profile.name : null,
    language: lang,
    difficulty,
    enabledCapabilities: enabled,
    layers
  };
}

function toSessionResponse(session) {
  return {
    id: session.id,
    documentId: session.documentId,
    title: session.title,
    startPage: session.startPage,
    endPage: session.endPage,
    currentPage: session.currentPage,
    difficulty: session.difficulty,
    aiProfileId: session.aiProfileId ?? null,
    enabledCapabilities: enabledCapabilitiesFor(session),
    language: session.language || 'en',
    // The provider is a backend/deployment concern, not a per-session user choice.
    aiProvider: 'claude',
    createdAt: session.createdAt
  };
}

function toMessageResponse(message) {
  return {
    id: message.id,
    sessionId: message.sessionId,
    speaker: message.speaker,
    inputType: message.inputType,
    message: message.message,
    createdAt: message.createdAt
  };
}

const KEYWORDS = {
  en: {
    greeting: ['hi', 'hello', 'hey'],
    summary: ['summary', 'summarize'],
    question: ['what', 'how', 'who', 'which', 'why'],
    quiz: ['quiz', 'question me', 'test me'],
    hint: ['hint', 'help', 'clue']
  },
  es: {
    greeting: ['hola', 'buenas'],
    summary: ['resumen', 'resume'],
    question: ['qué', 'que', 'cómo', 'como', 'quién', 'quien', 'cuál', 'cual'],
    quiz: ['pregunta', 'prueba', 'quiz'],
    hint: ['pista', 'ayuda']
  },
  fr: {
    greeting: ['bonjour', 'salut'],
    summary: ['résumé', 'resume'],
    question: ['quoi', 'comment', 'qui', 'quel', 'quelle', 'pourquoi'],
    quiz: ['question', 'quiz', 'teste-moi'],
    hint: ['indice', 'aide']
  }
};

const PHRASES = {
  en: {
    greeting: (tone, start, end) =>
      `Hi! I'm BooKI in ${tone} mode. What part of pages ${start}-${end} would you like to talk about?`,
    summary: (start, end, body) => `Here's a summary of pages ${start} to ${end}: ${body}`,
    found: (page, snippet) => `Based on the text on page ${page}: ${snippet}... Want me to go deeper?`,
    fallback: (start, end, snippet) =>
      `Good question. Based on pages ${start}-${end}, I can tell you this document covers: ${snippet}...`,
    quizPrompt: 'Here\'s a comprehension question: what is the main idea of the text you just read?',
    hintText: 'Hint: check the first sentence of each paragraph — that\'s usually where the key idea is.',
    idle: (tone, start, end) =>
      `Got it. As your ${tone}, let me know if you'd like an explanation, a summary, a question, or a hint about pages ${start}-${end}.`
  },
  es: {
    greeting: (tone, start, end) =>
      `¡Hola! Soy BooKI en modo ${tone}. ¿Sobre qué parte de las páginas ${start}-${end} quieres hablar?`,
    summary: (start, end, body) => `Este es un resumen de las páginas ${start} a ${end}: ${body}`,
    found: (page, snippet) => `Basándome en el texto de la página ${page}: ${snippet}... ¿Te gustaría que profundice?`,
    fallback: (start, end, snippet) =>
      `Buena pregunta. Según el contexto de las páginas ${start}-${end}, puedo decirte que el documento trata sobre: ${snippet}...`,
    quizPrompt: 'Aquí tienes una pregunta de comprensión: ¿cuál es la idea principal del texto que acabas de leer?',
    hintText: 'Pista: revisa la primera oración de cada párrafo. Ahí suele estar la idea clave.',
    idle: (tone, start, end) =>
      `Entiendo. Como tu ${tone}, dime si quieres una explicación, un resumen, una pregunta o una pista sobre las páginas ${start}-${end}.`
  },
  fr: {
    greeting: (tone, start, end) =>
      `Salut ! Je suis BooKI en mode ${tone}. De quelle partie des pages ${start}-${end} veux-tu parler ?`,
    summary: (start, end, body) => `Voici un résumé des pages ${start} à ${end} : ${body}`,
    found: (page, snippet) => `D'après le texte de la page ${page} : ${snippet}... Tu veux que j'approfondisse ?`,
    fallback: (start, end, snippet) =>
      `Bonne question. D'après les pages ${start}-${end}, je peux te dire que ce document traite de : ${snippet}...`,
    quizPrompt: 'Voici une question de compréhension : quelle est l\'idée principale du texte que tu viens de lire ?',
    hintText: 'Indice : regarde la première phrase de chaque paragraphe, c\'est souvent là qu\'est l\'idée clé.',
    idle: (tone, start, end) =>
      `Compris. En tant que ton ${tone}, dis-moi si tu veux une explication, un résumé, une question ou un indice sur les pages ${start}-${end}.`
  }
};

function includesAny(lower, list) {
  return list.some((kw) => lower.includes(kw));
}

const USER_NOTE_PREFIX = {
  en: (note) => ` (Keeping in mind what you told BooKI about yourself: "${note}")`,
  es: (note) => ` (Recordando lo que le contaste a BooKI sobre ti: "${note}")`,
  fr: (note) => ` (En tenant compte de ce que tu as dit à BooKI à ton sujet : "${note}")`
};

function mockReply(session, userMessage, isFirstExchange) {
  const lang = SUPPORTED_LANGUAGES.includes(session.language) ? session.language : 'en';
  const lower = userMessage.toLowerCase();
  const kw = KEYWORDS[lang];
  const t = PHRASES[lang];

  const pages = documentPages.filter(
    (p) => p.documentId === session.documentId && p.pageNumber >= session.startPage && p.pageNumber <= session.endPage
  );
  const contextText = pages.map((p) => p.extractedText).join(' ').toLowerCase();
  const profile = profileFor(session);
  const tone = profile ? profile.name : 'assistant';

  let reply;
  if (includesAny(lower, kw.greeting)) {
    reply = t.greeting(tone, session.startPage, session.endPage);
  } else if (includesAny(lower, kw.summary)) {
    const body = pages.map((p, i) => `p.${session.startPage + i}: ${p.extractedText.substring(0, 80)}...`).join(' ');
    reply = t.summary(session.startPage, session.endPage, body);
  } else if (includesAny(lower, kw.quiz)) {
    reply = t.quizPrompt;
  } else if (includesAny(lower, kw.hint)) {
    reply = t.hintText;
  } else if (includesAny(lower, kw.question)) {
    const cleaned = lower.replace(/[¿?]/g, '').trim();
    const matchedPage = pages.find((page) => contextText.includes(cleaned));
    reply = matchedPage
      ? t.found(matchedPage.pageNumber, matchedPage.extractedText.substring(0, 160))
      : t.fallback(session.startPage, session.endPage, pages[0]?.extractedText.substring(0, 120) || 'this topic');
  } else {
    reply = t.idle(tone, session.startPage, session.endPage);
  }

  if (isFirstExchange) {
    const readerContext = slotContent(profile, 'reader_context');
    if (readerContext) {
      reply += (USER_NOTE_PREFIX[lang] || USER_NOTE_PREFIX.en)(readerContext);
    }
  }

  return reply;
}

const QUIZ_TEMPLATES = {
  en: (n) => `In your own words, what does page ${n} explain?`,
  es: (n) => `Con tus propias palabras, ¿qué explica la página ${n}?`,
  fr: (n) => `Avec tes propres mots, qu'est-ce que la page ${n} explique ?`
};

const STOPWORDS = new Set([
  'the', 'and', 'for', 'with', 'that', 'this', 'from', 'are', 'was', 'were', 'have', 'has',
  'les', 'des', 'une', 'pour', 'dans', 'sont', 'avec',
  'los', 'las', 'del', 'que', 'una', 'para', 'como', 'sus', 'por'
]);

function pagesInRange(session) {
  return documentPages.filter(
    (p) => p.documentId === session.documentId && p.pageNumber >= session.startPage && p.pageNumber <= session.endPage
  );
}

function summaryLengthSettings(lengthPages) {
  const pages = Math.min(10, Math.max(1, Number(lengthPages) || 2));
  return {
    pages,
    charsPerPage: Math.round(80 + pages * 90),
    messageCount: Math.min(40, Math.max(2, pages * 4))
  };
}

const CUSTOM_PROMPT_NOTE = {
  en: (p) => ` Following your request: "${p}".`,
  es: (p) => ` Siguiendo tu pedido: "${p}".`,
  fr: (p) => ` Suivant ta demande : "${p}".`
};

const SUMMARY_INTRO = {
  en: (tone) => `Summary prepared as your ${tone}, combining the book's content with our discussion so far.`,
  es: (tone) => `Resumen preparado como tu ${tone}, combinando el contenido del libro con nuestra conversación hasta ahora.`,
  fr: (tone) => `Résumé préparé en tant que ton ${tone}, combinant le contenu du livre et notre discussion jusqu'ici.`
};

const SUMMARY_LABELS = {
  en: {
    book: 'From the book',
    discussion: 'From our discussion',
    noDiscussion: "You haven't chatted with BooKI yet in this session."
  },
  es: {
    book: 'Del libro',
    discussion: 'De nuestra conversación',
    noDiscussion: 'Todavía no has conversado con BooKI en esta sesión.'
  },
  fr: {
    book: 'Du livre',
    discussion: 'De notre discussion',
    noDiscussion: "Tu n'as pas encore discuté avec BooKI dans cette session."
  }
};

function buildSummaryContent(session, lengthPages, customPrompt) {
  const lang = SUPPORTED_LANGUAGES.includes(session.language) ? session.language : 'en';
  const settings = summaryLengthSettings(lengthPages);
  const labels = SUMMARY_LABELS[lang];
  const profile = profileFor(session);
  const readerContext = slotContent(profile, 'reader_context');
  const tone = profile ? profile.name : 'assistant';

  const pages = pagesInRange(session);
  const bookPart = pages
    .map((p) => {
      const truncated = p.extractedText.length > settings.charsPerPage;
      return `p.${p.pageNumber}: ${p.extractedText.substring(0, settings.charsPerPage)}${truncated ? '…' : ''}`;
    })
    .join(' ');

  const sessionMessages = messages
    .filter((m) => m.sessionId === session.id)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  const recentMessages = sessionMessages.slice(-settings.messageCount);
  const discussionPart = recentMessages
    .map((m) => `${m.speaker === 'USER' ? 'You' : 'BooKI'}: ${m.message.substring(0, 200)}`)
    .join(' | ');

  let intro = (SUMMARY_INTRO[lang] || SUMMARY_INTRO.en)(tone);
  if (customPrompt && customPrompt.trim()) {
    intro += (CUSTOM_PROMPT_NOTE[lang] || CUSTOM_PROMPT_NOTE.en)(customPrompt.trim());
  }
  if (readerContext) {
    intro += (USER_NOTE_PREFIX[lang] || USER_NOTE_PREFIX.en)(readerContext);
  }

  return { intro, bookPart, discussionPart: discussionPart || labels.noDiscussion, labels, pages: settings.pages };
}

const QUIZ_DIFFICULTIES = ['easy', 'medium', 'hard'];
const DIFFICULTY_SETTINGS = {
  easy: { threshold: 0.15, denomCap: 4 },
  medium: { threshold: 0.25, denomCap: 6 },
  hard: { threshold: 0.4, denomCap: 8 }
};

function generateQuiz(session, config) {
  const lang = SUPPORTED_LANGUAGES.includes(session.language) ? session.language : 'en';
  const template = QUIZ_TEMPLATES[lang] || QUIZ_TEMPLATES.en;
  const questionCount = Math.min(10, Math.max(1, Number(config.questionCount) || 3));
  const readerContext = slotContent(profileFor(session), 'reader_context');

  return pagesInRange(session)
    .slice(0, questionCount)
    .map((p, idx) => {
      let question = template(p.pageNumber);
      if (idx === 0 && readerContext) {
        question += (USER_NOTE_PREFIX[lang] || USER_NOTE_PREFIX.en)(readerContext);
      }
      return { id: p.pageNumber, pageNumber: p.pageNumber, question };
    });
}

const FEEDBACK = {
  en: {
    correct: 'Nice! Your answer covers key ideas from this page.',
    incorrect: 'Not quite — try rereading the page and mentioning its key terms.'
  },
  es: {
    correct: '¡Bien! Tu respuesta cubre ideas clave de esta página.',
    incorrect: 'No del todo — vuelve a leer la página y menciona sus términos clave.'
  },
  fr: {
    correct: 'Bien joué ! Ta réponse couvre les idées clés de cette page.',
    incorrect: 'Pas tout à fait — relis la page et mentionne ses termes clés.'
  }
};

function gradeAnswer(session, pageNumber, answer, difficulty, isFirstAttempt) {
  const lang = SUPPORTED_LANGUAGES.includes(session.language) ? session.language : 'en';
  const settings = DIFFICULTY_SETTINGS[difficulty] || DIFFICULTY_SETTINGS.medium;
  const page = documentPages.find((p) => p.documentId === session.documentId && p.pageNumber === Number(pageNumber));
  if (!page) {
    return { correct: false, score: 0, feedback: 'Page not found in this session.' };
  }

  const pageWords = new Set(
    page.extractedText
      .toLowerCase()
      .replace(/[^\p{L}\s]/gu, '')
      .split(/\s+/)
      .filter((w) => w.length > 4 && !STOPWORDS.has(w))
  );
  const answerWords = (answer || '')
    .toLowerCase()
    .replace(/[^\p{L}\s]/gu, '')
    .split(/\s+/)
    .filter(Boolean);
  const matches = new Set(answerWords.filter((w) => pageWords.has(w)));
  const denom = Math.min(settings.denomCap, pageWords.size) || 1;
  const score = Math.min(1, matches.size / denom);
  const correct = score >= settings.threshold && (answer || '').trim().length > 0;

  let feedback = correct ? FEEDBACK[lang].correct : FEEDBACK[lang].incorrect;
  if (isFirstAttempt) {
    const readerContext = slotContent(profileFor(session), 'reader_context');
    if (readerContext) {
      feedback += (USER_NOTE_PREFIX[lang] || USER_NOTE_PREFIX.en)(readerContext);
    }
  }

  return {
    correct,
    score: Number(score.toFixed(2)),
    feedback
  };
}

function computeProgress(session) {
  const totalPages = session.endPage - session.startPage + 1;
  const pagesRead = session.currentPage - session.startPage + 1;
  const pctRead = totalPages ? Math.round((pagesRead / totalPages) * 100) : 0;
  const sessionMessages = messages.filter((m) => m.sessionId === session.id);
  const sessionAttempts = quizAttempts.filter((a) => a.sessionId === session.id);
  const quizzesTaken = sessionAttempts.length;
  const quizAverageScore = quizzesTaken
    ? Math.round((sessionAttempts.reduce((sum, a) => sum + a.score, 0) / quizzesTaken) * 100)
    : 0;
  return {
    pagesRead: Math.max(0, Math.min(pagesRead, totalPages)),
    totalPages,
    pctRead: Math.max(0, Math.min(pctRead, 100)),
    messageCount: sessionMessages.length,
    quizzesTaken,
    quizAverageScore
  };
}

const NOTIF_TEXT = {
  en: {
    halfway: 'You are halfway through this session — keep going!',
    done: 'You finished all the pages in this session. Nice work!',
    sayHi: 'Say hi to BooKI to start the conversation.',
    tryQuiz: 'Try a quick quiz to test your understanding.'
  },
  es: {
    halfway: 'Vas a la mitad de esta sesión, ¡sigue así!',
    done: '¡Terminaste todas las páginas de esta sesión!',
    sayHi: 'Salúdale a BooKI para empezar la conversación.',
    tryQuiz: 'Prueba un quiz rápido para reforzar lo leído.'
  },
  fr: {
    halfway: 'Tu es à mi-chemin de cette session, continue !',
    done: 'Tu as terminé toutes les pages de cette session !',
    sayHi: 'Dis bonjour à BooKI pour démarrer la conversation.',
    tryQuiz: 'Essaie un petit quiz pour tester ta compréhension.'
  }
};

function computeNotifications(session) {
  const lang = SUPPORTED_LANGUAGES.includes(session.language) ? session.language : 'en';
  const t = NOTIF_TEXT[lang];
  const progress = computeProgress(session);
  const notifications = [];

  if (progress.pctRead >= 100) {
    notifications.push({ id: 1, type: 'progress', message: t.done, createdAt: nowIso() });
  } else if (progress.pctRead >= 50) {
    notifications.push({ id: 1, type: 'progress', message: t.halfway, createdAt: nowIso() });
  }
  if (progress.messageCount === 0) {
    notifications.push({ id: 2, type: 'chat', message: t.sayHi, createdAt: nowIso() });
  }
  if (progress.quizzesTaken === 0) {
    notifications.push({ id: 3, type: 'quiz', message: t.tryQuiz, createdAt: nowIso() });
  }
  return notifications;
}

router.post('/', authMiddleware, (req, res) => {
  const { documentId, title, startPage, endPage, difficulty, aiProfileId, language } = req.body;
  const doc = documents.find((d) => d.id === documentId && d.userId === req.userId);
  if (!doc) return res.status(404).json({ error: 'Document not found' });

  const mine = aiProfiles.filter((p) => p.userId === req.userId);
  const resolvedProfile =
    (aiProfileId ? mine.find((p) => p.id === Number(aiProfileId)) : null) ||
    // No profile chosen → the user's pre-designated default profile.
    mine.find((p) => p.isDefault) ||
    mine[0] ||
    null;

  const session = {
    id: sessions.length ? Math.max(...sessions.map((s) => s.id)) + 1 : 1,
    userId: req.userId,
    documentId,
    title: title || `${doc.title} (pages ${startPage}-${endPage})`,
    startPage: Math.max(1, Math.min(startPage, doc.pageCount)),
    endPage: Math.max(1, Math.min(endPage, doc.pageCount)),
    currentPage: startPage,
    difficulty: difficulty || 'medium',
    aiProfileId: resolvedProfile ? resolvedProfile.id : null,
    language: SUPPORTED_LANGUAGES.includes(language) ? language : 'en',
    createdAt: nowIso()
  };
  sessions.push(session);
  res.status(201).json(toSessionResponse(session));
});

router.get('/:id', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  res.json(toSessionResponse(session));
});

router.get('/:id/context', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  res.json(buildContext(session));
});

router.patch('/:id/current-page', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  session.currentPage = req.body.currentPage;
  res.json(toSessionResponse(session));
});

router.get('/:id/messages', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  const list = messages
    .filter((m) => m.sessionId === session.id)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    .map(toMessageResponse);
  res.json(list);
});

router.post('/:id/messages', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { message, inputType, capabilityHint } = req.body;
  if (capabilityHint && !enabledCapabilitiesFor(session).includes(capabilityHint)) {
    return res.status(400).json({ error: `The "${capabilityHint}" capability is turned off for this session's AI Profile.` });
  }
  const isFirstExchange = messages.filter((m) => m.sessionId === session.id).length === 0;
  const userMessage = {
    id: messages.length ? Math.max(...messages.map((m) => m.id)) + 1 : 1,
    sessionId: session.id,
    speaker: 'USER',
    inputType: inputType || 'TEXT',
    message,
    createdAt: nowIso()
  };
  messages.push(userMessage);

  const replyText = mockReply(session, message, isFirstExchange);
  const botMessage = {
    id: messages.length ? Math.max(...messages.map((m) => m.id)) + 1 : 1,
    sessionId: session.id,
    speaker: 'BOOKI',
    inputType: 'TEXT',
    message: replyText,
    createdAt: nowIso()
  };
  messages.push(botMessage);

  res.status(201).json(toMessageResponse(botMessage));
});

router.post('/:id/quiz', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { aiProfileId, difficulty, questionCount } = req.body || {};
  const resolvedDifficulty = QUIZ_DIFFICULTIES.includes(difficulty) ? difficulty : session.difficulty || 'medium';
  const resolvedProfileId = aiProfileId || session.aiProfileId || null;
  const profile = aiProfiles.find((p) => p.id === resolvedProfileId);

  const questions = generateQuiz(session, { questionCount });
  res.json({
    questions,
    config: {
      aiProfileId: resolvedProfileId,
      profileName: profile ? profile.name : null,
      difficulty: resolvedDifficulty,
      questionCount: questions.length
    }
  });
});

router.post('/:id/quiz/answer', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { pageNumber, question, answer, difficulty, aiProfileId } = req.body;
  const resolvedDifficulty = QUIZ_DIFFICULTIES.includes(difficulty) ? difficulty : session.difficulty || 'medium';
  const isFirstAttempt = quizAttempts.filter((a) => a.sessionId === session.id).length === 0;
  const result = gradeAnswer(session, pageNumber, answer, resolvedDifficulty, isFirstAttempt);

  quizAttempts.push({
    id: quizAttempts.length ? Math.max(...quizAttempts.map((a) => a.id)) + 1 : 1,
    sessionId: session.id,
    pageNumber: Number(pageNumber),
    question: question || '',
    answer,
    difficulty: resolvedDifficulty,
    aiProfileId: aiProfileId || session.aiProfileId || null,
    correct: result.correct,
    score: result.score,
    feedback: result.feedback,
    createdAt: nowIso()
  });

  res.json(result);
});

router.get('/:id/quiz/attempts', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const attempts = quizAttempts
    .filter((a) => a.sessionId === session.id)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    .map((a) => {
      const profile = aiProfiles.find((p) => p.id === a.aiProfileId);
      return {
        id: a.id,
        pageNumber: a.pageNumber,
        question: a.question,
        answer: a.answer,
        correct: a.correct,
        score: a.score,
        feedback: a.feedback,
        difficulty: a.difficulty,
        profileName: profile ? profile.name : null,
        createdAt: a.createdAt
      };
    });

  const totalScore = attempts.length ? attempts.reduce((sum, a) => sum + a.score, 0) / attempts.length : 0;
  const correctCount = attempts.filter((a) => a.correct).length;

  res.json({
    attempts,
    summary: {
      total: attempts.length,
      correct: correctCount,
      incorrect: attempts.length - correctCount,
      averageScore: Number((totalScore * 100).toFixed(0))
    }
  });
});

router.get('/:id/progress', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  res.json(computeProgress(session));
});

router.get('/:id/notifications', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  res.json(computeNotifications(session));
});

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function saveSentReport(sessionId, type, email, fileName) {
  const report = {
    id: sentReports.length ? Math.max(...sentReports.map((r) => r.id)) + 1 : 1,
    sessionId,
    type,
    email: email || null,
    fileName,
    createdAt: nowIso()
  };
  sentReports.push(report);
  // No real mail transport is configured for this mock — logging stands in for the send.
  if (email) {
    console.log(`[mock email] "${type}" report for session ${sessionId} -> ${email} (${fileName})`);
  } else {
    console.log(`[mock] "${type}" report generated for session ${sessionId} (${fileName}), not emailed`);
  }
  return report;
}

function toSentReportResponse(report) {
  return {
    id: report.id,
    sessionId: report.sessionId,
    type: report.type,
    email: report.email,
    downloadUrl: `/api/reports/${report.id}/file`,
    simulated: !!report.email,
    createdAt: report.createdAt
  };
}

router.get('/:id/reports', authMiddleware, (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  const list = sentReports
    .filter((r) => r.sessionId === session.id)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map(toSentReportResponse);
  res.json(list);
});

router.post('/:id/reports/progress', authMiddleware, async (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { email } = req.body || {};
  if (!email || !EMAIL_RE.test(email)) {
    return res.status(400).json({ error: 'A valid email is required' });
  }

  const doc = documents.find((d) => d.id === session.documentId);
  const profile = profileFor(session);
  const progress = computeProgress(session);

  const sections = [
    {
      heading: 'Session',
      lines: [
        `Document: ${doc ? doc.title : 'Unknown'}`,
        `Pages: ${session.startPage}-${session.endPage} · Difficulty: ${session.difficulty} · Language: ${LANGUAGE_NAMES[session.language] || session.language}`,
        `AI profile: ${profile ? profile.name : 'None selected'}`
      ]
    },
    {
      heading: 'Progress',
      lines: [
        `Pages read: ${progress.pagesRead}/${progress.totalPages} (${progress.pctRead}%)`,
        `Messages exchanged: ${progress.messageCount}`,
        `Quizzes taken: ${progress.quizzesTaken}`,
        `Quiz average score: ${progress.quizAverageScore}%`
      ]
    }
  ];

  const { fileName } = await buildPdf(`Progress report — ${session.title}`, `Generated ${new Date().toLocaleString()}`, sections);
  const report = saveSentReport(session.id, 'progress', email, fileName);
  res.status(201).json(toSentReportResponse(report));
});

router.post('/:id/reports/quiz', authMiddleware, async (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { email } = req.body || {};
  if (!email || !EMAIL_RE.test(email)) {
    return res.status(400).json({ error: 'A valid email is required' });
  }

  const doc = documents.find((d) => d.id === session.documentId);
  const attempts = quizAttempts
    .filter((a) => a.sessionId === session.id)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());

  if (attempts.length === 0) {
    return res.status(400).json({ error: 'No quiz answers to report yet for this session' });
  }

  const correctCount = attempts.filter((a) => a.correct).length;
  const avgScore = Math.round((attempts.reduce((sum, a) => sum + a.score, 0) / attempts.length) * 100);

  const sections = [
    {
      heading: 'Session',
      lines: [`Document: ${doc ? doc.title : 'Unknown'}`, `Pages: ${session.startPage}-${session.endPage}`]
    },
    {
      heading: 'Quiz summary',
      lines: [`${attempts.length} answered · ${correctCount} correct · ${avgScore}% average score`]
    },
    ...attempts.map((a, idx) => ({
      heading: `Question ${idx + 1} — Page ${a.pageNumber} (${a.difficulty})`,
      lines: [
        `Q: ${a.question}`,
        `Answer: ${a.answer}`,
        `Result: ${a.correct ? 'Correct' : 'Needs work'}`,
        `Feedback: ${a.feedback}`
      ]
    }))
  ];

  const { fileName } = await buildPdf(`Quiz correction report — ${session.title}`, `Generated ${new Date().toLocaleString()}`, sections);
  const report = saveSentReport(session.id, 'quiz', email, fileName);
  res.status(201).json(toSentReportResponse(report));
});

router.post('/:id/summary', authMiddleware, async (req, res) => {
  const session = sessions.find((s) => s.id === Number(req.params.id) && s.userId === req.userId);
  if (!session) return res.status(404).json({ error: 'Session not found' });
  if (!enabledCapabilitiesFor(session).includes('summary')) {
    return res.status(400).json({ error: "The summary capability is turned off for this session's AI Profile." });
  }

  const { lengthPages, prompt, includeCover, deliverAs, email } = req.body || {};
  const resolvedDeliverAs = deliverAs === 'pdf' ? 'pdf' : 'chat';

  if (resolvedDeliverAs === 'pdf' && email && !EMAIL_RE.test(email)) {
    return res.status(400).json({ error: 'A valid email is required' });
  }

  const { intro, bookPart, discussionPart, labels } = buildSummaryContent(session, lengthPages, prompt);

  if (resolvedDeliverAs === 'chat') {
    const summaryText = `${intro}\n\n${labels.book}: ${bookPart}\n\n${labels.discussion}: ${discussionPart}`;
    const botMessage = {
      id: messages.length ? Math.max(...messages.map((m) => m.id)) + 1 : 1,
      sessionId: session.id,
      speaker: 'BOOKI',
      inputType: 'TEXT',
      message: summaryText,
      createdAt: nowIso()
    };
    messages.push(botMessage);
    return res.status(201).json(toMessageResponse(botMessage));
  }

  const doc = documents.find((d) => d.id === session.documentId);
  const cover = includeCover && doc
    ? { color: coverColorFor(doc.title, doc.id), initials: initialsFor(doc.title) }
    : null;

  const sections = [
    { heading: labels.book, lines: [bookPart] },
    { heading: labels.discussion, lines: [discussionPart] }
  ];

  const { fileName } = await buildPdf(
    `Summary — ${session.title}`,
    intro,
    sections,
    cover ? { cover } : {}
  );

  const report = saveSentReport(session.id, 'summary', email || null, fileName);
  res.status(201).json(toSentReportResponse(report));
});

module.exports = router;
