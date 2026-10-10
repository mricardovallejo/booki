const express = require('express');
const { appProfileSlots } = require('../data');
const { authMiddleware } = require('../middleware');
const { slotResponse } = require('../appProfile');

const router = express.Router();

function ownedBy(userId) {
  return appProfileSlots.filter((r) => r.userId === userId);
}

router.get('/', authMiddleware, (req, res) => {
  res.json(ownedBy(req.userId).map(slotResponse));
});

router.put('/', authMiddleware, (req, res) => {
  const rows = ownedBy(req.userId);
  const slots = (req.body && req.body.slots) || [];
  for (const patch of slots) {
    const row = rows.find((r) => r.key === patch.key);
    if (row && typeof patch.text === 'string') row.text = patch.text;
  }
  res.json(rows.map(slotResponse));
});

router.post('/:key/revert', authMiddleware, (req, res) => {
  const rows = ownedBy(req.userId);
  const row = rows.find((r) => r.key === req.params.key);
  if (!row) return res.status(400).json({ error: 'Unknown App Profile slot' });
  row.text = row.originalText;
  res.json(rows.map(slotResponse));
});

router.post('/restore', authMiddleware, (req, res) => {
  const rows = ownedBy(req.userId);
  rows.forEach((r) => {
    r.text = r.originalText;
  });
  res.json(rows.map(slotResponse));
});

module.exports = router;
