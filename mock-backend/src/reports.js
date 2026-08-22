const { PDFDocument, rgb, StandardFonts } = require('pdf-lib');
const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const REPORTS_DIR = path.join(__dirname, '..', 'reports');
if (!fs.existsSync(REPORTS_DIR)) {
  fs.mkdirSync(REPORTS_DIR, { recursive: true });
}

const PAGE_SIZE = [612, 792];
const MARGIN_X = 56;
const MAX_WIDTH = PAGE_SIZE[0] - MARGIN_X * 2;
const LINE_HEIGHT = 15;

// Same palette family as the frontend's DocumentCard gradients, so a
// generated PDF's cover block visually matches the app's own book covers.
const COVER_PALETTE = [
  [0.5, 0.1, 0.1],
  [0.45, 0.28, 0.05],
  [0.05, 0.3, 0.24],
  [0.05, 0.28, 0.35],
  [0.28, 0.1, 0.4],
  [0.4, 0.08, 0.35],
  [0.45, 0.2, 0.05],
  [0.15, 0.12, 0.4]
];

function coverColorFor(title, id) {
  const hash = String(title)
    .split('')
    .reduce((acc, ch) => acc + ch.charCodeAt(0), 0) + Number(id || 0);
  const [r, g, b] = COVER_PALETTE[hash % COVER_PALETTE.length];
  return rgb(r, g, b);
}

function initialsFor(title) {
  const words = String(title).split(/[\s_.-]+/).filter(Boolean);
  if (words.length === 0) return 'BK';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

async function buildPdf(title, subtitle, sections, options = {}) {
  const pdfDoc = await PDFDocument.create();
  const font = await pdfDoc.embedFont(StandardFonts.Helvetica);
  const bold = await pdfDoc.embedFont(StandardFonts.HelveticaBold);

  let page = pdfDoc.addPage(PAGE_SIZE);
  let y = PAGE_SIZE[1] - 72;

  function ensureSpace(height) {
    if (y - height < 60) {
      page = pdfDoc.addPage(PAGE_SIZE);
      y = PAGE_SIZE[1] - 72;
    }
  }

  function drawWrapped(text, size, useFont, color) {
    const words = String(text).split(' ');
    let line = '';
    for (const word of words) {
      const test = line ? `${line} ${word}` : word;
      const w = useFont.widthOfTextAtSize(test, size);
      if (w > MAX_WIDTH && line) {
        ensureSpace(LINE_HEIGHT);
        page.drawText(line, { x: MARGIN_X, y, size, font: useFont, color });
        y -= LINE_HEIGHT;
        line = word;
      } else {
        line = test;
      }
    }
    if (line) {
      ensureSpace(LINE_HEIGHT);
      page.drawText(line, { x: MARGIN_X, y, size, font: useFont, color });
      y -= LINE_HEIGHT;
    }
  }

  ensureSpace(30);
  page.drawText('BooKI', { x: MARGIN_X, y, size: 22, font: bold, color: rgb(0.9, 0.22, 0.27) });

  if (options.cover) {
    const size = 54;
    const coverX = PAGE_SIZE[0] - MARGIN_X - size;
    const coverY = y - size + 20;
    page.drawRectangle({ x: coverX, y: coverY, width: size, height: size, color: options.cover.color, borderRadius: 6 });
    const initialsSize = 18;
    const textWidth = bold.widthOfTextAtSize(options.cover.initials, initialsSize);
    page.drawText(options.cover.initials, {
      x: coverX + (size - textWidth) / 2,
      y: coverY + size / 2 - initialsSize / 2 + 4,
      size: initialsSize,
      font: bold,
      color: rgb(1, 1, 1)
    });
  }

  y -= 26;
  drawWrapped(title, 14, bold, rgb(0.1, 0.1, 0.15));
  if (subtitle) {
    y -= 2;
    drawWrapped(subtitle, 10, font, rgb(0.4, 0.4, 0.45));
  }
  y -= 10;

  for (const section of sections) {
    ensureSpace(24);
    page.drawText(section.heading, { x: MARGIN_X, y, size: 12, font: bold, color: rgb(0.1, 0.1, 0.15) });
    y -= 18;
    for (const line of section.lines) {
      drawWrapped(line, 10, font, rgb(0.25, 0.25, 0.32));
    }
    y -= 10;
  }

  const bytes = await pdfDoc.save();
  const fileName = `${uuidv4()}.pdf`;
  const filePath = path.join(REPORTS_DIR, fileName);
  fs.writeFileSync(filePath, bytes);
  return { fileName, filePath };
}

module.exports = { buildPdf, REPORTS_DIR, coverColorFor, initialsFor };
