// The account-wide App Profile used by the mock backend: what each
// difficulty level means (quiz question + grading only — never chat, voice,
// or summaries), how BooKI's functions behave (quiz question, grading,
// summary, explain, mnemonic), and when it routes to one on its own in chat.
// One editable set per account — not tied to any one tutor profile (see
// aiProfiles.js, which now holds only persona).

// Static metadata for every slot: label, group, and the locked frame (the part
// a user cannot edit because the program depends on its shape).
const SLOT_DEFS = [
  { key: 'easy', label: 'Difficulty — Easy', group: 'difficulty', lockedPreamble: null, lockedPostamble: null },
  { key: 'medium', label: 'Difficulty — Medium', group: 'difficulty', lockedPreamble: null, lockedPostamble: null },
  { key: 'hard', label: 'Difficulty — Advanced', group: 'difficulty', lockedPreamble: null, lockedPostamble: null },
  {
    key: 'fn_quiz_question',
    label: 'Function — Quiz question',
    group: 'functions',
    lockedPreamble:
      'Output only the question — one open question the reader answers in their own words. No preamble, ' +
      'no surrounding quotes, no number or label before it, and no multiple-choice options.',
    lockedPostamble: null
  },
  {
    key: 'fn_answer_grading',
    label: 'Function — Answer grading',
    group: 'functions',
    lockedPreamble:
      'Reply in exactly this format and nothing else:\nSCORE: a number from 0.0 to 1.0 for how complete and accurate the answer is\nFEEDBACK: two to four warm sentences — first what the reader got right, then the part they missed or misread together with the correct information from the page. Never tell them to try again.',
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

// Shipped defaults — mirror of SlotPromptCatalog's `difficulty` + `shared` in the Spring backend.
const SHARED_DEFAULTS = {
  easy:
    'Easy. Assume the reader is new to this material and may not have finished the pages. Explain in short ' +
    'sentences and plain words, and define every term you use. Check understanding one idea at a time: open ' +
    'with a multiple-choice or true/false question so the reader gains confidence, then follow with an open ' +
    'question that makes them put the idea in their own words, in writing or aloud. A short phrase or one ' +
    'sentence is a full answer. Accept partial answers, say what part is right, and build the next small step ' +
    'from there.',
  medium:
    'Medium. Assume the reader has been through the pages once. Explain at a normal pace and use the text\'s ' +
    'own terms once you have defined them. Mix recall with "why" and "how" questions that link two points ' +
    'together. Expect two or three sentences. When an answer falls short, name what is missing and let the ' +
    'reader try again rather than completing it for them.',
  hard:
    'Advanced. Assume a close reading and genuine interest in the subject. Explain concisely and engage with ' +
    'nuance, exceptions, and counter-arguments. Ask the reader to compare, evaluate, or apply ideas to a new ' +
    'case. Expect a precise, well-structured answer, and push back on vague or unsupported claims instead of ' +
    'letting them pass.',
  fn_quiz_question:
    'Ask one open question that tests whether the reader grasped a key idea on this page, not a trivia ' +
    'detail — one they answer in their own words. Keep it to a single focus and answerable from the page ' +
    'alone. Let the active difficulty rubric decide whether it asks for recall, "why/how", or analysis. No ' +
    'multiple-choice options.',
  fn_answer_grading:
    'This is a moment to teach, not to test. Judge the meaning against the page, not wording or spelling. ' +
    'SCORE reflects how complete and accurate the answer is. In FEEDBACK, start with what the reader got ' +
    'right, then give them the piece they missed or misread with the correct information from the page, so ' +
    'they finish knowing the answer. Do not ask them to try again and do not scold.',
  fn_summary:
    'Recap what these pages say: lead with the main point or argument, then the supporting ideas in the order ' +
    'the text develops them. Where the discussion so far clarified something or showed the reader was stuck, ' +
    'let that shape the emphasis. Stay within what the pages actually cover, and keep it a recap, not a ' +
    'critique. Match the requested length.',
  fn_explain:
    "Work out from the reader's words which point lost them, and explain that point from the ground up in " +
    'plain language. Give one concrete everyday analogy, then connect it back to what the text says; if the ' +
    'analogy breaks down in a way that matters, note where. Close by inviting them to say if it is still ' +
    'unclear.',
  fn_mnemonic:
    'Pick the handful of points on these pages actually worth memorising — a list, a sequence, a set of terms ' +
    '— not every detail. Build one memory aid whose form fits that structure: an acronym for a list, a vivid ' +
    'image for how things relate, a short rhyme for an order. Keep it compact, then add one line on how to ' +
    'use it to recall the material.',
  capability_routing:
    'Route to a capability only when the reader\'s last message clearly calls for it — an explicit request ' +
    '("quiz me", "summarise this", "I don\'t get this part", "help me remember this") or an unmistakable ' +
    'equivalent. When the reader is asking something you can answer in prose, discussing the text, or ' +
    'thinking aloud, answer normally. On a borderline call, answer in prose.'
};

/** One editable row per key, owned by `userId`, seeded from the shipped defaults. */
function seedAppProfileForUser(userId, startId) {
  return SLOT_DEFS.map((def, i) => ({
    id: startId + i,
    userId,
    key: def.key,
    text: SHARED_DEFAULTS[def.key],
    originalText: SHARED_DEFAULTS[def.key]
  }));
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

/** Assembled text of one slot: its locked frame plus the editable body, as the model would actually receive it. */
function assembledSlotText(storedSlot) {
  const def = SLOT_DEFS.find((d) => d.key === storedSlot.key);
  return [def && def.lockedPreamble, storedSlot.text, def && def.lockedPostamble].filter(Boolean).join('\n\n');
}

module.exports = {
  SLOT_DEFS,
  seedAppProfileForUser,
  slotResponse,
  assembledSlotText
};
