// AI Profile shape used by the mock backend. Stage 1 preview of the real contract:
// one profile = the full set of editable prompts a session runs on (everything
// except the core). Factory profiles are read-only seeds; a user duplicates one
// to get an editable copy.

// The core is never part of a profile and never editable. Served read-only via
// the session context endpoint.
const CORE_PROMPT =
  "You are BooKI, a reading companion, not an authority. Always respond in the session's language, " +
  'whatever language these instructions are written in. Ground every answer in the session page range; ' +
  "if something is not there, say so instead of guessing. Keep an encouraging tone and never scold a wrong answer. " +
  'When guidance conflicts, follow this order: these core rules, then the difficulty rubric, then the function ' +
  'being performed, then the persona, then the reader context. A stated accessibility need in the reader context ' +
  'outranks persona style.';

// Static metadata for every slot: label, group, and the locked frame (the part a
// user cannot edit because the program depends on its shape). `null` frame means
// the whole slot is free text.
const SLOT_DEFS = [
  { key: 'persona', label: 'Persona', group: 'persona', lockedPreamble: null, lockedPostamble: null },
  { key: 'reader_context', label: 'Reader context', group: 'reader', lockedPreamble: null, lockedPostamble: null },
  { key: 'rubric_easy', label: 'Difficulty — Easy', group: 'difficulty', lockedPreamble: null, lockedPostamble: null },
  { key: 'rubric_medium', label: 'Difficulty — Medium', group: 'difficulty', lockedPreamble: null, lockedPostamble: null },
  { key: 'rubric_hard', label: 'Difficulty — Advanced', group: 'difficulty', lockedPreamble: null, lockedPostamble: null },
  {
    key: 'fn_quiz_question',
    label: 'Function — Quiz question',
    group: 'functions',
    lockedPreamble: 'Output only the question. No preamble, no numbering, no quotes.',
    lockedPostamble: null
  },
  {
    key: 'fn_answer_grading',
    label: 'Function — Answer grading',
    group: 'functions',
    lockedPreamble:
      'Reply in exactly three lines and nothing else:\nCORRECT: yes or no\nSCORE: a number from 0.0 to 1.0\nFEEDBACK: one short sentence',
    lockedPostamble: null
  },
  {
    key: 'fn_summary',
    label: 'Function — Summary',
    group: 'functions',
    lockedPreamble: 'Write prose only. No headings unless the reader asks for them.',
    lockedPostamble: null
  },
  { key: 'fn_explain', label: 'Function — Explain', group: 'functions', lockedPreamble: null, lockedPostamble: null },
  { key: 'fn_mnemonic', label: 'Function — Mnemonic', group: 'functions', lockedPreamble: null, lockedPostamble: null },
  {
    key: 'capability_routing',
    label: 'Capability routing',
    group: 'routing',
    lockedPreamble:
      'If a specialized capability fits the reader\'s last message better than a prose reply, respond with only {"capability":"<name>"}. Otherwise answer normally.',
    lockedPostamble: null
  }
];

// Dev-grade defaults. Kept short on purpose — the strong prompts come later.
const SHARED_DEFAULTS = {
  reader_context: '',
  rubric_easy:
    'Easy: assume little prior knowledge. Short sentences, common words. Ask the reader to recall or restate one idea at a time. Accept partial answers and build on them.',
  rubric_medium:
    'Medium: assume the reader has read the pages once. Mix recall with "why" and "how" questions. Expect two or three sentences. Name what is missing without giving the full answer.',
  rubric_hard:
    'Advanced: assume a close reading. Ask the reader to compare, evaluate, or apply the ideas to a new case. Expect a precise, well-structured answer and hold it to that standard.',
  fn_quiz_question: 'Ask one open reading-comprehension question about the current page, at the session difficulty.',
  fn_answer_grading:
    "Judge the reader's answer against the page. Lenient on Easy, strict on Advanced. Keep feedback encouraging and specific.",
  fn_summary:
    'Summarize the session pages and the discussion so far, at the requested length. Lead with the main idea.',
  fn_explain:
    'Re-explain the idea the reader is stuck on in plainer terms, with one concrete everyday analogy. Keep it to a short paragraph.',
  fn_mnemonic:
    'Give one memory aid (acronym, vivid image, or short rhyme) for the key points of these pages, then a one-line note on how to use it.',
  capability_routing: 'Available capabilities: quiz, summary, explain, mnemonic. Prefer a normal answer when unsure.'
};

const READER_LEVELS = ['beginner', 'intermediate', 'advanced'];

// The conversational capabilities a profile can allow. A capability that is not
// in a profile's `enabledCapabilities` is off for that session: BooKI never
// triggers it on its own AND its quick-action button is hidden in the chat.
const CAPABILITIES = ['quiz', 'summary', 'explain', 'mnemonic'];

const FACTORY_PROFILES = [
  {
    name: 'Patient Tutor',
    isDefault: true,
    persona:
      'You are a patient tutor. You explain one step at a time, check understanding before moving on, and never make the reader feel behind.'
  },
  {
    name: 'Study Buddy',
    persona:
      'You are a study buddy the same age as the reader. You think out loud, ask questions back, and are happy to debate an idea.'
  },
  {
    name: 'Subject Expert',
    persona:
      'You are a subject-matter expert. You use precise terms, define each one once, and connect the passage to the wider field.'
  },
  {
    name: 'Accessible Pace',
    persona:
      'You support readers who need a slower pace. You break ideas into small pieces, repeat key terms in different words, and give very concrete hints.'
  }
];

function defaultContentFor(key, persona) {
  if (key === 'persona') return persona;
  return SHARED_DEFAULTS[key] != null ? SHARED_DEFAULTS[key] : '';
}

// The hidden originals (userId null). Never listed to users or run by a session
// — they exist only as the source for a new user's starter copies and for
// "restore to original".
function buildFactoryAiProfiles() {
  return FACTORY_PROFILES.map((tpl, idx) => ({
    id: idx + 1,
    userId: null,
    name: tpl.name,
    source: 'factory',
    basedOnId: null,
    isDefault: !!tpl.isDefault,
    readerLevel: null,
    enabledCapabilities: [...CAPABILITIES],
    updatedAt: new Date().toISOString(),
    slots: SLOT_DEFS.map((def) => {
      const text = defaultContentFor(def.key, tpl.persona);
      return { key: def.key, text, originalText: text };
    })
  }));
}

// One editable copy per factory template, owned by `userId`. Called at
// registration so a user always picks from — and runs sessions on — their own
// profiles, never a read-only original.
function seedUserAiProfiles(templates, userId, startId) {
  return templates.map((tpl, i) => ({
    id: startId + i,
    userId,
    name: tpl.name,
    source: 'custom',
    basedOnId: tpl.id,
    isDefault: !!tpl.isDefault,
    readerLevel: tpl.readerLevel ?? null,
    enabledCapabilities: [...(tpl.enabledCapabilities ?? CAPABILITIES)],
    updatedAt: new Date().toISOString(),
    slots: tpl.slots.map((s) => ({ ...s }))
  }));
}

// Reset a copy's editable fields to the template it was based on.
function restoreFromTemplate(profile, template) {
  profile.readerLevel = template.readerLevel ?? null;
  profile.enabledCapabilities = [...(template.enabledCapabilities ?? CAPABILITIES)];
  profile.slots = template.slots.map((s) => ({ key: s.key, text: s.text, originalText: s.originalText }));
  profile.updatedAt = new Date().toISOString();
}

function slotResponse(storedSlot) {
  const def = SLOT_DEFS.find((d) => d.key === storedSlot.key);
  return {
    key: storedSlot.key,
    label: def ? def.label : storedSlot.key,
    group: def ? def.group : 'functions',
    lockedPreamble: def ? def.lockedPreamble : null,
    lockedPostamble: def ? def.lockedPostamble : null,
    text: storedSlot.text,
    originalText: storedSlot.originalText,
    modified: storedSlot.text !== storedSlot.originalText
  };
}

function profileResponse(profile, { withSlots } = {}) {
  const slots = profile.slots.map(slotResponse);
  const base = {
    id: profile.id,
    name: profile.name,
    isDefault: !!profile.isDefault,
    readerLevel: profile.readerLevel ?? null,
    enabledCapabilities: profile.enabledCapabilities ?? [...CAPABILITIES],
    updatedAt: profile.updatedAt
  };
  return withSlots ? { ...base, slots } : base;
}

// Assembled text of one slot: its locked frame plus the editable body, as the
// model would actually receive it. Used by the session context endpoint.
function assembledSlotText(storedSlot) {
  const def = SLOT_DEFS.find((d) => d.key === storedSlot.key);
  return [def && def.lockedPreamble, storedSlot.text, def && def.lockedPostamble]
    .filter(Boolean)
    .join('\n\n');
}

module.exports = {
  CORE_PROMPT,
  SLOT_DEFS,
  READER_LEVELS,
  CAPABILITIES,
  buildFactoryAiProfiles,
  seedUserAiProfiles,
  restoreFromTemplate,
  profileResponse,
  slotResponse,
  assembledSlotText
};
