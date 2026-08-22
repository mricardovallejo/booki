const express = require('express');
const { users } = require('../data');
const { authMiddleware } = require('../middleware');

const router = express.Router();

function toUserResponse(user) {
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    bio: user.bio || '',
    systemPrompt: user.systemPrompt || '',
    createdAt: user.createdAt
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

  const { name, bio, systemPrompt } = req.body;
  if (typeof name === 'string' && name.trim()) user.name = name.trim();
  if (typeof bio === 'string') user.bio = bio.trim();
  if (typeof systemPrompt === 'string') user.systemPrompt = systemPrompt.trim();

  res.json(toUserResponse(user));
});

module.exports = router;
