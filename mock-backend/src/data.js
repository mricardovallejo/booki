const { PDFDocument, rgb, StandardFonts } = require('pdf-lib');
const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');
const { buildFactoryAiProfiles, seedUserAiProfiles } = require('./aiProfiles');

const UPLOADS_DIR = path.join(__dirname, '..', 'uploads');
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

let users = [
  {
    id: 1,
    email: 'demo@booki.app',
    passwordHash: 'password', // plain text for demo only
    name: 'Demo User',
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 30).toISOString()
  }
];

// Hidden originals (ids 1-4, userId null) + one editable set of copies per user.
const factoryTemplates = buildFactoryAiProfiles();
let aiProfiles = [...factoryTemplates];

/** Give a user their own copy of every factory template. Returns the new copies. */
function seedAiProfilesForUser(userId) {
  const nextId = aiProfiles.length ? Math.max(...aiProfiles.map((p) => p.id)) + 1 : 1;
  const copies = seedUserAiProfiles(factoryTemplates, userId, nextId);
  aiProfiles.push(...copies);
  return copies;
}

// Demo user starts with the 4 copies; their "Patient Tutor" one carries a
// filled-in reader context so the UI has something to show.
(() => {
  const copies = seedAiProfilesForUser(1);
  const tutor = copies.find((p) => p.name === 'Patient Tutor');
  tutor.readerLevel = 'intermediate';
  tutor.slots.find((s) => s.key === 'reader_context').text =
    'Studying for a certification exam, fairly new to the topic. Prefers short answers with one concrete example and little jargon.';
})();

let documents = [];
let documentPages = [];
let sessions = [];
let messages = [];
let collections = [];
let quizAttempts = [];
let sentReports = [];

function nowIso() {
  return new Date().toISOString();
}

async function createSamplePdf(title, pageTexts) {
  const pdfDoc = await PDFDocument.create();
  const font = await pdfDoc.embedFont(StandardFonts.Helvetica);
  const boldFont = await pdfDoc.embedFont(StandardFonts.HelveticaBold);

  pageTexts.forEach((text, idx) => {
    const page = pdfDoc.addPage([612, 792]);
    const { width, height } = page.getSize();

    page.drawText(title, {
      x: 72,
      y: height - 72,
      size: 22,
      font: boldFont,
      color: rgb(0.1, 0.1, 0.2)
    });

    page.drawText(`Página ${idx + 1}`, {
      x: 72,
      y: height - 104,
      size: 12,
      font,
      color: rgb(0.4, 0.4, 0.4)
    });

    const words = text.split(' ');
    let line = '';
    let y = height - 140;
    const maxWidth = width - 144;
    const lineHeight = 18;

    for (const word of words) {
      const test = line ? `${line} ${word}` : word;
      const w = font.widthOfTextAtSize(test, 12);
      if (w > maxWidth && line) {
        page.drawText(line, { x: 72, y, size: 12, font, color: rgb(0.1, 0.1, 0.1) });
        line = word;
        y -= lineHeight;
      } else {
        line = test;
      }
    }
    if (line) {
      page.drawText(line, { x: 72, y, size: 12, font, color: rgb(0.1, 0.1, 0.1) });
    }
  });

  const fileName = `${uuidv4()}_${title.replace(/[^a-zA-Z0-9]/g, '_')}.pdf`;
  const filePath = path.join(UPLOADS_DIR, fileName);
  const bytes = await pdfDoc.save();
  fs.writeFileSync(filePath, bytes);
  return { fileName, filePath, pageCount: pageTexts.length };
}

async function seedData() {
  const sampleDocs = [
    {
      title: 'Introducción a la Física',
      pages: [
        'La física es la ciencia natural que estudia la materia, la energía, el espacio y el tiempo, y las interacciones entre ellos. En este libro exploraremos los principios fundamentales que rigen el comportamiento del universo. Comenzaremos por la cinemática, que describe el movimiento sin preocuparse por sus causas.',
        'La cinemática utiliza conceptos como posición, velocidad y aceleración. La velocidad es la rapidez con dirección, mientras que la aceleración mide el cambio de velocidad en el tiempo. Estos conceptos nos permiten predecir la trayectoria de un objeto.',
        'Las leyes de Newton son el pilar de la mecánica clásica. La primera ley establece que un objeto permanecerá en reposo o en movimiento rectilíneo uniforme a menos que actúe sobre él una fuerza neta. La segunda ley relaciona fuerza, masa y aceleración.',
        'El trabajo y la energía son conceptos centrales. El trabajo se define como la fuerza aplicada por el desplazamiento en la dirección de la fuerza. La energía cinética depende de la masa y del cuadrado de la velocidad. La conservación de la energía es uno de los principios más poderosos de la física.'
      ]
    },
    {
      title: 'Historia de Roma',
      pages: [
        'Roma fue fundada, según la tradición, en el año 753 a.C. por Rómulo y Remo. La ciudad comenzó como un pequeño asentamiento en la orilla del río Tíber y creció hasta convertirse en uno de los imperios más grandes de la historia.',
        'La República Romana se caracterizó por un sistema de gobierno con senadores, cónsules y tribunos. Aunque no era una democracia moderna, incluía mecanismos de equilibrio y representación que influyeron en posteriores sistemas políticos.',
        'Las guerras púnicas contra Cartago definieron el destino del Mediterráneo occidental. Aníbal cruzó los Alpes con elefantes y causó severas derrotas a Roma, pero finalmente Escipión el Africano venció en Zama.',
        'El Imperio Romano alcanzó su mayor extensión bajo Trajano. La red de calzadas, el derecho romano y la extensión de la ciudadanía fueron legados que perduraron mucho después de la caída de Occidente en el siglo V d.C.'
      ]
    },
    {
      title: 'Programación para Principiantes',
      pages: [
        'Programar consiste en dar instrucciones precisas a una computadora. Estas instrucciones se escriben en lenguajes de programación que luego se traducen a código máquina. Aprender a programar desarrolla el pensamiento lógico y la capacidad de resolver problemas.',
        'Las variables son contenedores de datos. Pueden almacenar números, texto, listas o estructuras más complejas. Elegir nombres claros para las variables hace que el código sea más fácil de leer y mantener.',
        'Las funciones permiten agrupar instrucciones reutilizables. Una función recibe parámetros, realiza operaciones y puede devolver un resultado. Modularizar el código con funciones reduce errores y facilita las pruebas.',
        'Los condicionales y los bucles controlan el flujo del programa. Un if ejecuta código solo cuando se cumple una condición, mientras que un bucle repite instrucciones hasta que deja de cumplirse una condición.'
      ]
    }
  ];

  const userId = 1;

  for (const sample of sampleDocs) {
    const { fileName, filePath, pageCount } = await createSamplePdf(sample.title, sample.pages);
    const doc = {
      id: documents.length + 1,
      userId,
      title: `${sample.title}.pdf`,
      fileName,
      filePath,
      pageCount,
      createdAt: nowIso()
    };
    documents.push(doc);

    sample.pages.forEach((text, idx) => {
      documentPages.push({
        id: documentPages.length + 1,
        documentId: doc.id,
        pageNumber: idx + 1,
        extractedText: text
      });
    });
  }

  collections.push(
    { id: 1, userId, name: 'Science', documentIds: [1], createdAt: nowIso() },
    { id: 2, userId, name: 'History', documentIds: [2], createdAt: nowIso() },
    { id: 3, userId, name: 'Technology', documentIds: [3], createdAt: nowIso() }
  );

  sessions.push(
    { id: 1, userId, documentId: 1, title: 'Introducción a la Física.pdf (págs. 1-2)', startPage: 1, endPage: 2, currentPage: 1, difficulty: 'easy', aiProfileId: 5, language: 'es', createdAt: nowIso() },
    { id: 2, userId, documentId: 2, title: 'Historia de Roma.pdf (págs. 1-4)', startPage: 1, endPage: 4, currentPage: 1, difficulty: 'medium', aiProfileId: 6, language: 'es', createdAt: nowIso() }
  );

  messages.push(
    { id: 1, sessionId: 1, speaker: 'USER', inputType: 'TEXT', message: '¿Qué es la cinemática?', createdAt: nowIso() },
    { id: 2, sessionId: 1, speaker: 'BOOKI', inputType: 'TEXT', message: 'La cinemática es la rama de la física que describe el movimiento de los objetos usando conceptos como posición, velocidad y aceleración, sin analizar las causas de ese movimiento.', createdAt: nowIso() },
    { id: 3, sessionId: 2, speaker: 'USER', inputType: 'TEXT', message: '¿Quién fundó Roma?', createdAt: nowIso() },
    { id: 4, sessionId: 2, speaker: 'BOOKI', inputType: 'TEXT', message: 'Según la tradición, Roma fue fundada por Rómulo y Remo en el año 753 a.C.', createdAt: nowIso() }
  );
}

module.exports = {
  UPLOADS_DIR,
  users,
  aiProfiles,
  factoryTemplates,
  seedAiProfilesForUser,
  documents,
  documentPages,
  sessions,
  messages,
  collections,
  quizAttempts,
  sentReports,
  nowIso,
  createSamplePdf,
  seedData
};
