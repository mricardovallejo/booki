const express = require('express');
const { profileMasters } = require('../data');
const { authMiddleware } = require('../middleware');

const router = express.Router();

router.get('/', authMiddleware, (req, res) => {
  res.json(profileMasters);
});

router.post('/', authMiddleware, (req, res) => {
  const { name, description, systemPrompt } = req.body;
  if (!name || !systemPrompt) {
    return res.status(400).json({ error: 'name and systemPrompt are required' });
  }
  const master = {
    id: profileMasters.length ? Math.max(...profileMasters.map((m) => m.id)) + 1 : 1,
    name,
    description: description || '',
    systemPrompt
  };
  profileMasters.push(master);
  res.status(201).json(master);
});

module.exports = router;
