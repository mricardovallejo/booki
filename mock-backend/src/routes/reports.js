const express = require('express');
const path = require('path');
const { sentReports } = require('../data');
const { authMiddleware } = require('../middleware');
const { REPORTS_DIR } = require('../reports');

const router = express.Router();

router.get('/:id/file', authMiddleware, (req, res) => {
  const report = sentReports.find((r) => r.id === Number(req.params.id));
  if (!report) return res.status(404).json({ error: 'Report not found' });

  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `inline; filename="booki-report-${report.id}.pdf"`);
  res.sendFile(path.resolve(path.join(REPORTS_DIR, report.fileName)));
});

module.exports = router;
