const API_BASE = 'http://localhost:8081/api';

const api = {
  async getQuizzes() {
    const res = await fetch(`${API_BASE}/quizzes`);
    return res.json();
  },

  async getQuizByCode(code) {
    const res = await fetch(`${API_BASE}/quizzes/join/${code}`);
    if (!res.ok) throw new Error('Quiz not found');
    return res.json();
  },

  async createSession(quizId) {
    const res = await fetch(`${API_BASE}/quizzes/${quizId}/session`, { method: 'POST' });
    return res.json();
  },

  async getSessionInfo(sessionId) {
    const res = await fetch(`${API_BASE}/sessions/${sessionId}`);
    return res.json();
  },

  async getResults(quizId) {
    const res = await fetch(`${API_BASE}/results/${quizId}`);
    return res.json();
  },

  async healthCheck() {
    const res = await fetch(`${API_BASE}/health`);
    return res.json();
  }
};

export default api;
