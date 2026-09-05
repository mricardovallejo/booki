const express = require('express');
const { readerProfiles } = require('../data');
const { authMiddleware } = require('../middleware');
const { READER_LEVELS, GENERIC_CONTEXT, readerProfileResponse } = require('../readerProfiles');

const router = express.Router();

// A user sees the built-in read-only reader (userId null) + their own.
function visibleTo(userId) {
  return readerProfiles.filter((r) => r.userId === null || r.userId === userId);
}
function findOwned(userId, id) {
  return readerProfiles.find((r) => r.id === Number(id) && r.userId === userId) || null;
}
function findVisible(userId, id) {
  return visibleTo(userId).find((r) => r.id === Number(id)) || null;
}
function nextId() {
  return readerProfiles.length ? Math.max(...readerProfiles.map((r) => r.id)) + 1 : 1;
}

router.get('/', authMiddleware, (req, res) => {
  res.json(visibleTo(req.userId).map(readerProfileResponse));
});

// Create a reader profile — a copy of `fromId` (defaults to the built-in
// default), which the caller then renames/edits.
router.post('/', authMiddleware, (req, res) => {
  const { name, context, readerLevel, fromId } = req.body || {};
  const from = fromId ? findVisible(req.userId, fromId) : null;
  const profile = {
    id: nextId(),
    userId: req.userId,
    name: typeof name === 'string' && name.trim() ? name.trim() : 'My reader profile',
    isDefault: false,
    readOnly: false,
    readerLevel: READER_LEVELS.includes(readerLevel) ? readerLevel : from ? from.readerLevel : null,
    context: typeof context === 'string' ? context : from ? from.context : GENERIC_CONTEXT,
    updatedAt: new Date().toISOString()
  };
  readerProfiles.push(profile);
  res.status(201).json(readerProfileResponse(profile));
});

router.patch('/:id', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) {
    return res.status(404).json({ error: 'Reader profile not found (the built-in default is not editable)' });
  }
  const { name, context, readerLevel, isDefault } = req.body || {};
  if (typeof name === 'string' && name.trim()) profile.name = name.trim();
  if (typeof context === 'string') profile.context = context;
  if (typeof readerLevel === 'string') {
    profile.readerLevel = READER_LEVELS.includes(readerLevel) ? readerLevel : null;
  }
  if (isDefault === true) {
    readerProfiles.forEach((r) => {
      if (r.userId === req.userId || r.userId === null) r.isDefault = r.id === profile.id;
    });
  }
  profile.updatedAt = new Date().toISOString();
  res.json(readerProfileResponse(profile));
});

router.delete('/:id', authMiddleware, (req, res) => {
  const profile = findOwned(req.userId, req.params.id);
  if (!profile) return res.status(404).json({ error: 'Reader profile not found' });
  // Sessions that used it fall back to the default at read time.
  readerProfiles.splice(readerProfiles.findIndex((r) => r.id === profile.id), 1);
  if (profile.isDefault) {
    const fallback = readerProfiles.find((r) => r.userId === null);
    if (fallback) fallback.isDefault = true;
  }
  res.status(204).end();
});

module.exports = router;
