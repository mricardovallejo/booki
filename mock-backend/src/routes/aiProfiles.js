const express = require('express');
const { aiProfiles, factoryTemplates } = require('../data');
const { authMiddleware } = require('../middleware');
const { profileResponse, restoreFromTemplate, READER_LEVELS, CAPABILITIES } = require('../aiProfiles');

const router = express.Router();

// Users only ever see and touch their own profiles. The factory templates
// (userId null) are hidden originals, used only for "restore to original".
function ownedBy(userId) {
  return aiProfiles.filter((p) => p.userId === userId);
}

function findOwned(userId, id) {
  return ownedBy(userId).find((p) => p.id === Number(id)) || null;
}

function nextId() {
  return aiProfiles.length ? Math.max(...aiProfiles.map((p) => p.id)) + 1 : 1;
}

router.get('/', authMiddleware, (req, res) => {
  res.json(ownedBy(req.userId).map((p) => profileResponse(p)));
});

router.get('/:id', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) return res.status(404).json({ error: 'AI profile not found' });
  res.json(profileResponse(profile, { withSlots: true }));
});

router.post('/:id/duplicate', authMiddleware, (req, res) => {
  const source = findOwned(req.userId, req.params.id);
  if (!source) return res.status(404).json({ error: 'AI profile not found' });

  const name = (req.body && req.body.name && req.body.name.trim()) || `${source.name} (copy)`;
  const copy = {
    id: nextId(),
    userId: req.userId,
    name,
    source: 'custom',
    basedOnId: source.basedOnId,
    isDefault: false,
    readerLevel: source.readerLevel ?? null,
    enabledCapabilities: [...(source.enabledCapabilities ?? CAPABILITIES)],
    updatedAt: new Date().toISOString(),
    slots: source.slots.map((s) => ({ ...s }))
  };
  aiProfiles.push(copy);
  res.status(201).json(profileResponse(copy, { withSlots: true }));
});

router.patch('/:id', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) return res.status(404).json({ error: 'AI profile not found' });

  const { name, readerLevel, enabledCapabilities, slots } = req.body || {};
  if (typeof name === 'string' && name.trim()) profile.name = name.trim();
  if (readerLevel === null || READER_LEVELS.includes(readerLevel)) {
    if (readerLevel !== undefined) profile.readerLevel = readerLevel;
  }
  if (Array.isArray(enabledCapabilities)) {
    profile.enabledCapabilities = CAPABILITIES.filter((c) => enabledCapabilities.includes(c));
  }
  if (Array.isArray(slots)) {
    for (const patch of slots) {
      const slot = profile.slots.find((s) => s.key === patch.key);
      if (slot && typeof patch.content === 'string') slot.content = patch.content;
    }
  }
  profile.updatedAt = new Date().toISOString();
  res.json(profileResponse(profile, { withSlots: true }));
});

// Reset one slot to the text it was born with.
router.post('/:id/revert', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) return res.status(404).json({ error: 'AI profile not found' });
  const key = req.body && req.body.key;
  const slot = profile.slots.find((s) => s.key === key);
  if (!slot) return res.status(400).json({ error: 'Unknown slot key' });
  slot.content = slot.factoryContent;
  profile.updatedAt = new Date().toISOString();
  res.json(profileResponse(profile, { withSlots: true }));
});

// Reset the whole profile (all slots, reader level, capabilities) to the
// original template it was based on.
router.post('/:id/restore', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) return res.status(404).json({ error: 'AI profile not found' });
  const template = factoryTemplates.find((t) => t.id === profile.basedOnId);
  if (!template) return res.status(400).json({ error: 'This profile has no original to restore from.' });
  restoreFromTemplate(profile, template);
  res.json(profileResponse(profile, { withSlots: true }));
});

router.delete('/:id', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) return res.status(404).json({ error: 'AI profile not found' });
  if (ownedBy(req.userId).length <= 1) {
    return res.status(400).json({ error: 'You need at least one AI Profile.' });
  }
  aiProfiles.splice(aiProfiles.findIndex((p) => p.id === profile.id), 1);
  res.status(204).end();
});

module.exports = router;
