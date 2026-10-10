const express = require('express');
const { users } = require('../data');
const { authMiddleware } = require('../middleware');
const { CAPABILITIES } = require('../aiProfiles');

const router = express.Router();

function toUserResponse(user) {
  const defaultReportEmail = user.defaultReportEmail ?? null;
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    createdAt: user.createdAt,
    enabledCapabilities: user.enabledCapabilities ?? [...CAPABILITIES],
    defaultReportEmail,
    // No app-wide fallback in the mock — nothing to configure like the real
    // booki.email.default-report-recipient.
    effectiveReportEmail: defaultReportEmail
  };
}

router.get('/me', authMiddleware, (req, res) => {
  const user = users.find((u) => u.id === req.userId);
  if (!user) return res.status(404).json({ error: 'User not found' });
  res.json(toUserResponse(user));
});

router.patch('/me', authMiddleware, (req, res) => {
  const user = users.find((u) => u.id === req.userId);
  if (!user) return res.status(404).json({ error: 'User not found' });

  const { name, enabledCapabilities, defaultReportEmail } = req.body;
  if (typeof name === 'string' && name.trim()) user.name = name.trim();
  if (Array.isArray(enabledCapabilities)) {
    user.enabledCapabilities = CAPABILITIES.filter((c) => enabledCapabilities.includes(c));
  }
  if (typeof defaultReportEmail === 'string') {
    user.defaultReportEmail = defaultReportEmail.trim() || null;
  }

  res.json(toUserResponse(user));
});

module.exports = router;
