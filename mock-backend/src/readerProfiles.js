// Reader Profile: a named, reusable description of the reader in one study
// context ("Languages", "Sciences", "Philosophy" …) — their goal, how much they
// already know, how they like to learn, any accessibility need. It describes the
// READER, not the assistant. A session picks one (alongside its AI Profile);
// it has no association with any AI Profile. Editing a reader profile changes it
// for every session that uses it.
//
// Every user's list always includes the built-in `FACTORY_READER` (id 1,
// `readOnly`, the default until the user makes their own default). You don't
// edit that one — you duplicate it.

const READER_LEVELS = ['beginner', 'intermediate', 'advanced'];

const GENERIC_CONTEXT =
  'I use this general profile when I have not specified a subject-specific goal or learning preference.\n' +
  'Calibrate your support from evidence in the conversation: what I ask, understand, or find difficult. ' +
  'Do not infer my age, education, intelligence, or reading ability.\n' +
  'Help me build a clear mental model of the text. Start with the central idea and only the background ' +
  'needed to understand it. Define unfamiliar terms in context, make connections between ideas explicit, ' +
  'and use a concrete example or analogy when it adds real clarity.';

const LANGUAGE_SUPPORT_CONTEXT =
  'This reader may need support understanding or expressing spoken or written language. This is a support ' +
  'need, not a diagnosis or a measure of intelligence.\n' +
  'Preserve the conceptual challenge while reducing avoidable language load. State the purpose and main ' +
  'idea first, preview essential words, use short direct sentences, and make relationships explicit.\n' +
  'Give one instruction or ask one question at a time. Offer speech, writing, keywords, choices, sentence ' +
  'starters, or a fuller explanation as ways to respond. Assess the idea before language form.\n' +
  'If understanding is unclear, rephrase, add a concrete example or cue, and increase support gradually. ' +
  'Never diagnose, label, infantilize, or lower intellectual expectations.';

const FACTORY_READER = {
  id: 1,
  userId: null,
  name: 'General reader',
  isDefault: true,
  readOnly: true,
  readerLevel: null,
  context: GENERIC_CONTEXT,
  updatedAt: new Date().toISOString()
};

const LANGUAGE_SUPPORT_READER = {
  id: 2,
  userId: null,
  name: 'Language-support reader',
  isDefault: false,
  readOnly: true,
  readerLevel: null,
  context: LANGUAGE_SUPPORT_CONTEXT,
  updatedAt: new Date().toISOString()
};

function readerProfileResponse(p) {
  return {
    id: p.id,
    name: p.name,
    isDefault: !!p.isDefault,
    readOnly: !!p.readOnly,
    readerLevel: p.readerLevel ?? null,
    context: p.context ?? '',
    updatedAt: p.updatedAt
  };
}

module.exports = {
  READER_LEVELS,
  GENERIC_CONTEXT,
  FACTORY_READER,
  LANGUAGE_SUPPORT_READER,
  readerProfileResponse
};
