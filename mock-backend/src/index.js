const express = require('express');
const cors = require('cors');
const path = require('path');

const { seedData } = require('./data');
const { errorHandler } = require('./middleware');
const { register, login } = require('./auth');
const documentsRoutes = require('./routes/documents');
const sessionsRoutes = require('./routes/sessions');
const profileMastersRoutes = require('./routes/profileMasters');
const collectionsRoutes = require('./routes/collections');
const usersRoutes = require('./routes/users');
const reportsRoutes = require('./routes/reports');

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors({ origin: 'http://localhost:5173', credentials: true }));
app.use(express.json());
app.use('/uploads', express.static(path.join(__dirname, '..', 'uploads')));

app.get('/api/health', (req, res) => res.json({ status: 'ok', mock: true }));

app.post('/api/auth/register', (req, res, next) => {
  try {
    const { email, password, name } = req.body;
    if (!email || !password) return res.status(400).json({ error: 'Email and password required' });
    const result = register(email, password, name);
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
});

app.post('/api/auth/login', (req, res, next) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ error: 'Email and password required' });
    const result = login(email, password);
    res.json(result);
  } catch (err) {
    err.status = 401;
    next(err);
  }
});

app.use('/api/documents', documentsRoutes);
app.use('/api/sessions', sessionsRoutes);
app.use('/api/profile-masters', profileMastersRoutes);
app.use('/api/collections', collectionsRoutes);
app.use('/api/users', usersRoutes);
app.use('/api/reports', reportsRoutes);

app.use(errorHandler);

(async () => {
  await seedData();
  app.listen(PORT, () => {
    console.log(`BooKI mock backend running on http://localhost:${PORT}`);
    console.log('Preloaded demo user: demo@booki.app / password');
  });
})();
