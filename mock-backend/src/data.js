const { PDFDocument, rgb, StandardFonts } = require('pdf-lib');
const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');
const { buildFactoryAiProfiles, seedUserAiProfiles } = require('./aiProfiles');
const { FACTORY_READER, LANGUAGE_SUPPORT_READER } = require('./readerProfiles');

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

// Hidden originals (userId null) + one editable set of copies per user.
const factoryTemplates = buildFactoryAiProfiles();
let aiProfiles = [...factoryTemplates];

// Reader profiles: who is reading, per study context. Every list starts with the
// read-only shipped readers; users duplicate one into their own.
let readerProfiles = [{ ...FACTORY_READER }, { ...LANGUAGE_SUPPORT_READER }];

function nextReaderProfileId() {
  return readerProfiles.length ? Math.max(...readerProfiles.map((r) => r.id)) + 1 : 1;
}

/** Give a user their own copy of every AI template. */
function seedProfilesForUser(userId) {
  const nextId = aiProfiles.length ? Math.max(...aiProfiles.map((p) => p.id)) + 1 : 1;
  const copies = seedUserAiProfiles(factoryTemplates, userId, nextId);
  aiProfiles.push(...copies);
  return { copies };
}

// Demo user: the AI template copies + one custom reader profile. Its seed sessions
// (below) pick a reader profile each.
let demoExamPrepReaderId = null;
(() => {
  seedProfilesForUser(1);
  const examPrep = {
    id: nextReaderProfileId(),
    userId: 1,
    name: 'Exam prep',
    isDefault: false,
    readOnly: false,
    readerLevel: 'intermediate',
    context:
      'Studying for a certification exam, fairly new to the topic. Prefers short answers with one concrete example and little jargon.',
    updatedAt: new Date().toISOString()
  };
  readerProfiles.push(examPrep);
  demoExamPrepReaderId = examPrep.id;
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

    page.drawText(`Page ${idx + 1}`, {
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
      title: 'Introduction to Physics',
      pages: [
        'Physics is the natural science that studies matter, energy, space and time, and the interactions between them. In this book we explore the fundamental principles that govern the behaviour of the universe. We start with kinematics, which describes motion without worrying about its causes.',
        'Kinematics uses concepts such as position, velocity and acceleration. Velocity is speed with a direction, while acceleration measures the change in velocity over time. These concepts let us predict the trajectory of an object.',
        "Newton's laws are the foundation of classical mechanics. The first law states that an object stays at rest or in uniform straight-line motion unless a net force acts on it. The second law relates force, mass and acceleration.",
        'Work and energy are central concepts. Work is the force applied times the displacement in the direction of the force. Kinetic energy depends on mass and the square of velocity. Conservation of energy is one of the most powerful principles in physics.'
      ]
    },
    {
      title: 'History of Rome',
      pages: [
        'Rome was founded, according to tradition, in 753 BC by Romulus and Remus. The city began as a small settlement on the bank of the Tiber and grew into one of the largest empires in history.',
        'The Roman Republic was characterised by a system of government with senators, consuls and tribunes. Though not a modern democracy, it included checks and balances and forms of representation that influenced later political systems.',
        'The Punic Wars against Carthage decided the fate of the western Mediterranean. Hannibal crossed the Alps with elephants and inflicted severe defeats on Rome, but Scipio Africanus finally won at Zama.',
        'The Roman Empire reached its greatest extent under Trajan. The road network, Roman law and the spread of citizenship were legacies that outlasted the fall of the West in the 5th century AD.'
      ]
    },
    {
      title: 'Programming for Beginners',
      pages: [
        'Programming is about giving a computer precise instructions. Those instructions are written in programming languages that are then translated to machine code. Learning to program develops logical thinking and problem-solving.',
        'Variables are containers for data. They can hold numbers, text, lists or more complex structures. Choosing clear names for variables makes code easier to read and maintain.',
        'Functions let you group reusable instructions. A function takes parameters, performs operations and can return a result. Splitting code into functions reduces errors and makes testing easier.',
        'Conditionals and loops control the flow of a program. An if runs code only when a condition holds, while a loop repeats instructions until a condition stops holding.'
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
    { id: 1, userId, documentId: 1, title: 'Introduction to Physics.pdf (pp. 1-2)', startPage: 1, endPage: 2, currentPage: 1, difficulty: 'easy', aiProfileId: 5, readerProfileId: demoExamPrepReaderId, language: 'en', createdAt: nowIso() },
    { id: 2, userId, documentId: 2, title: 'History of Rome.pdf (pp. 1-4)', startPage: 1, endPage: 4, currentPage: 1, difficulty: 'medium', aiProfileId: 6, readerProfileId: null, language: 'en', createdAt: nowIso() }
  );

  messages.push(
    { id: 1, sessionId: 1, speaker: 'USER', inputType: 'TEXT', message: 'What is kinematics?', createdAt: nowIso() },
    { id: 2, sessionId: 1, speaker: 'BOOKI', inputType: 'TEXT', message: 'Kinematics is the branch of physics that describes the motion of objects using concepts like position, velocity and acceleration, without analysing the causes of that motion.', createdAt: nowIso() },
    { id: 3, sessionId: 2, speaker: 'USER', inputType: 'TEXT', message: 'Who founded Rome?', createdAt: nowIso() },
    { id: 4, sessionId: 2, speaker: 'BOOKI', inputType: 'TEXT', message: 'According to tradition, Rome was founded by Romulus and Remus in 753 BC.', createdAt: nowIso() }
  );
}

module.exports = {
  UPLOADS_DIR,
  users,
  aiProfiles,
  readerProfiles,
  factoryTemplates,
  seedProfilesForUser,
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
