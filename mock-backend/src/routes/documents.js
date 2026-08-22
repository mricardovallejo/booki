const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const {
  documents,
  documentPages,
  collections,
  sessions,
  messages,
  quizAttempts,
  UPLOADS_DIR,
  nowIso,
  createSamplePdf
} = require('../data');
const { authMiddleware } = require('../middleware');

const router = express.Router();
const upload = multer({ dest: UPLOADS_DIR });

function toDocumentResponse(doc) {
  return {
    id: doc.id,
    title: doc.title,
    pageCount: doc.pageCount,
    createdAt: doc.createdAt
  };
}

router.get('/', authMiddleware, (req, res) => {
  const list = documents
    .filter((d) => d.userId === req.userId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map(toDocumentResponse);
  res.json(list);
});

router.get('/:id', authMiddleware, (req, res) => {
  const doc = documents.find((d) => d.id === Number(req.params.id) && d.userId === req.userId);
  if (!doc) return res.status(404).json({ error: 'Document not found' });
  res.json(toDocumentResponse(doc));
});

router.get('/:id/file', authMiddleware, (req, res) => {
  const doc = documents.find((d) => d.id === Number(req.params.id) && d.userId === req.userId);
  if (!doc) return res.status(404).json({ error: 'Document not found' });
  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `inline; filename="${doc.title}"`);
  res.sendFile(path.resolve(doc.filePath));
});

router.post('/', authMiddleware, upload.single('file'), async (req, res) => {
  try {
    const tempPath = req.file?.path;
    if (!tempPath) return res.status(400).json({ error: 'No file uploaded' });

    // Mock: generate a readable PDF with the uploaded filename as title and a few pages.
    const originalName = req.file.originalname || 'document.pdf';
    const titleBase = originalName.replace(/\.pdf$/i, '');
    const pageTexts = [
      `Este es el documento "${titleBase}". Aquí comienza tu lectura con BooKI. Puedes crear sesiones por rango de páginas y conversar con el asistente sobre el contenido.`,
      `Página 2 de "${titleBase}". En una versión real, este texto sería el contenido extraído automáticamente del PDF que subiste.`,
      `Página 3 de "${titleBase}". Sigue leyendo, avanza páginas y haz preguntas a BooKI cuando lo necesites.`
    ];

    const { fileName, filePath, pageCount } = await createSamplePdf(titleBase, pageTexts);
    fs.unlinkSync(tempPath);

    const doc = {
      id: documents.length ? Math.max(...documents.map((d) => d.id)) + 1 : 1,
      userId: req.userId,
      title: originalName,
      fileName,
      filePath,
      pageCount,
      createdAt: nowIso()
    };
    documents.push(doc);

    pageTexts.forEach((text, idx) => {
      documentPages.push({
        id: documentPages.length ? Math.max(...documentPages.map((p) => p.id)) + 1 : 1,
        documentId: doc.id,
        pageNumber: idx + 1,
        extractedText: text
      });
    });

    res.status(201).json(toDocumentResponse(doc));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

router.delete('/:id', authMiddleware, (req, res) => {
  const id = Number(req.params.id);
  const index = documents.findIndex((d) => d.id === id && d.userId === req.userId);
  if (index === -1) return res.status(404).json({ error: 'Document not found' });

  const [doc] = documents.splice(index, 1);
  try {
    fs.unlinkSync(doc.filePath);
  } catch {
    // File already gone; nothing else to do.
  }

  for (let i = documentPages.length - 1; i >= 0; i--) {
    if (documentPages[i].documentId === id) documentPages.splice(i, 1);
  }

  collections.forEach((c) => {
    c.documentIds = c.documentIds.filter((docId) => docId !== id);
  });

  const affectedSessionIds = sessions.filter((s) => s.documentId === id).map((s) => s.id);
  for (let i = sessions.length - 1; i >= 0; i--) {
    if (sessions[i].documentId === id) sessions.splice(i, 1);
  }
  for (let i = messages.length - 1; i >= 0; i--) {
    if (affectedSessionIds.includes(messages[i].sessionId)) messages.splice(i, 1);
  }
  for (let i = quizAttempts.length - 1; i >= 0; i--) {
    if (affectedSessionIds.includes(quizAttempts[i].sessionId)) quizAttempts.splice(i, 1);
  }

  res.status(204).end();
});

module.exports = router;
