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
  'My goal for this reading: \n' +
  'What I already know about the topic: \n' +
  'How I like to learn (examples, definitions, pace): \n' +
  'Anything that helps me (short paragraphs, dyslexia-friendly formatting, …): ';

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
  readerProfileResponse
};
