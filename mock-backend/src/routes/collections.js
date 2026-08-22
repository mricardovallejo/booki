const express = require('express');
const { collections, documents, nowIso } = require('../data');
const { authMiddleware } = require('../middleware');

const router = express.Router();

function toCollectionResponse(collection) {
  return {
    id: collection.id,
    name: collection.name,
    documentIds: collection.documentIds,
    createdAt: collection.createdAt
  };
}

function findOwned(req, id) {
  return collections.find((c) => c.id === id && c.userId === req.userId);
}

router.get('/', authMiddleware, (req, res) => {
  const list = collections.filter((c) => c.userId === req.userId).map(toCollectionResponse);
  res.json(list);
});

router.post('/', authMiddleware, (req, res) => {
  const { name, documentIds = [] } = req.body;
  if (!name) return res.status(400).json({ error: 'Name is required' });

  const validIds = documentIds.filter((id) => documents.some((d) => d.id === id && d.userId === req.userId));
  const collection = {
    id: collections.length ? Math.max(...collections.map((c) => c.id)) + 1 : 1,
    userId: req.userId,
    name,
    documentIds: validIds,
    createdAt: nowIso()
  };
  collections.push(collection);
  res.status(201).json(toCollectionResponse(collection));
});

router.patch('/:id', authMiddleware, (req, res) => {
  const collection = findOwned(req, Number(req.params.id));
  if (!collection) return res.status(404).json({ error: 'Tag not found' });

  const { name } = req.body;
  if (typeof name === 'string' && name.trim()) {
    collection.name = name.trim();
  }
  res.json(toCollectionResponse(collection));
});

router.delete('/:id', authMiddleware, (req, res) => {
  const index = collections.findIndex((c) => c.id === Number(req.params.id) && c.userId === req.userId);
  if (index === -1) return res.status(404).json({ error: 'Tag not found' });
  collections.splice(index, 1);
  res.status(204).end();
});

router.put('/:id/documents/:documentId', authMiddleware, (req, res) => {
  const collection = findOwned(req, Number(req.params.id));
  if (!collection) return res.status(404).json({ error: 'Tag not found' });
  const documentId = Number(req.params.documentId);
  const doc = documents.find((d) => d.id === documentId && d.userId === req.userId);
  if (!doc) return res.status(404).json({ error: 'Document not found' });

  if (!collection.documentIds.includes(documentId)) {
    collection.documentIds.push(documentId);
  }
  res.json(toCollectionResponse(collection));
});

router.delete('/:id/documents/:documentId', authMiddleware, (req, res) => {
  const collection = findOwned(req, Number(req.params.id));
  if (!collection) return res.status(404).json({ error: 'Tag not found' });
  const documentId = Number(req.params.documentId);
  collection.documentIds = collection.documentIds.filter((id) => id !== documentId);
  res.json(toCollectionResponse(collection));
});

module.exports = router;
