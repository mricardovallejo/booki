const { users, nowIso } = require('./data');

const JWT_SECRET = 'mock-secret-do-not-use-in-production';

function generateToken(email, userId) {
  // Simple unsigned token for mock purposes: base64(JSON).base64(JSON).signature
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ sub: email, userId, iat: Date.now(), exp: Date.now() + 86400000 })).toString('base64url');
  return `${header}.${payload}.`;
}

function parseToken(token) {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    if (payload.exp && payload.exp < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

function register(email, password, name) {
  if (users.find((u) => u.email === email)) {
    throw new Error('Email already registered');
  }
  const user = {
    id: users.length + 1,
    email,
    passwordHash: password,
    name: name || email.split('@')[0],
    createdAt: nowIso()
  };
  users.push(user);
  return { token: generateToken(user.email, user.id), user: toUserResponse(user) };
}

function login(email, password) {
  const user = users.find((u) => u.email === email && u.passwordHash === password);
  if (!user) {
    throw new Error('Invalid credentials');
  }
  return { token: generateToken(user.email, user.id), user: toUserResponse(user) };
}

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

module.exports = {
  generateToken,
  parseToken,
  register,
  login,
  toUserResponse
};
