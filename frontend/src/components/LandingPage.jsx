import { useState, useEffect } from 'react';
import api from '../services/api';

export default function LandingPage({ onJoin, onHost }) {
  const [mode, setMode] = useState(null); // null | 'join' | 'host'
  const [joinCode, setJoinCode] = useState('');
  const [playerName, setPlayerName] = useState('');
  const [quizzes, setQuizzes] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.getQuizzes().then(setQuizzes).catch(() => {});
  }, []);

  const handleJoin = async (e) => {
    e.preventDefault();
    if (!joinCode.trim() || !playerName.trim()) { setError('Fill in all fields'); return; }
    setLoading(true);
    setError('');
    try {
      const quiz = await api.getQuizByCode(joinCode.trim().toUpperCase());
      onJoin(joinCode.trim().toUpperCase(), playerName.trim(), quiz);
    } catch {
      setError('Invalid join code. Check and try again.');
    }
    setLoading(false);
  };

  const handleHost = async (quizId, quiz) => {
    setLoading(true);
    try {
      const session = await api.createSession(quizId);
      onHost(session.sessionId, { ...quiz, ...session });
    } catch (err) {
      setError('Failed to create session');
    }
    setLoading(false);
  };

  return (
    <div className="page">
      <div style={{ maxWidth: 520, width: '100%' }}>
        {/* Logo */}
        <div className="text-center mb-4">
          <div style={{ fontSize: '3rem', marginBottom: 8 }}>⚡</div>
          <h1 className="title-xl mb-2">QuizMaster</h1>
          <p className="subtitle">Real-time multiplayer quiz battles</p>
        </div>

        {!mode && (
          <div className="glass-card" style={{ padding: 32 }}>
            <button className="btn btn-primary btn-lg btn-block mb-2" onClick={() => setMode('join')}>
              🎮 Join a Quiz
            </button>
            <button className="btn btn-secondary btn-lg btn-block" onClick={() => setMode('host')}>
              🎯 Host a Quiz
            </button>
          </div>
        )}

        {mode === 'join' && (
          <div className="glass-card" style={{ padding: 32 }}>
            <h2 className="title-md mb-3">Join Quiz</h2>
            <form onSubmit={handleJoin}>
              <div className="mb-2">
                <label className="label mb-1" style={{ display: 'block' }}>Your Name</label>
                <input
                  className="input-field"
                  placeholder="Enter your name..."
                  value={playerName}
                  onChange={e => setPlayerName(e.target.value)}
                  maxLength={20}
                  autoFocus
                />
              </div>
              <div className="mb-3">
                <label className="label mb-1" style={{ display: 'block' }}>Join Code</label>
                <input
                  className="input-field"
                  placeholder="e.g. TECH42"
                  value={joinCode}
                  onChange={e => setJoinCode(e.target.value.toUpperCase())}
                  maxLength={10}
                  style={{ fontFamily: 'Outfit', fontSize: '1.3rem', fontWeight: 700, letterSpacing: 4, textAlign: 'center' }}
                />
              </div>
              {error && <p style={{ color: 'var(--accent-red)', marginBottom: 16, fontSize: '0.9rem' }}>{error}</p>}
              <button className="btn btn-primary btn-lg btn-block mb-2" type="submit" disabled={loading}>
                {loading ? '⏳ Joining...' : '🚀 Join Game'}
              </button>
              <button className="btn btn-secondary btn-block" type="button" onClick={() => { setMode(null); setError(''); }}>
                ← Back
              </button>
            </form>
          </div>
        )}

        {mode === 'host' && (
          <div className="glass-card" style={{ padding: 32 }}>
            <h2 className="title-md mb-3">Select Quiz to Host</h2>
            {quizzes.length === 0 && <p className="subtitle">No quizzes available. Start the backend server first.</p>}
            {quizzes.map(quiz => (
              <div
                key={quiz.id}
                className="glass-card"
                style={{
                  padding: 20, marginBottom: 12, cursor: 'pointer',
                  border: '1px solid var(--border-glass)',
                  transition: 'all 0.2s'
                }}
                onClick={() => handleHost(quiz.id, quiz)}
                onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--accent-purple)'}
                onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border-glass)'}
              >
                <h3 style={{ fontFamily: 'Outfit', fontWeight: 600, marginBottom: 4 }}>{quiz.title}</h3>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>{quiz.description}</p>
                <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span className="category-badge">Code: {quiz.joinCode}</span>
                  <span style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>
                    {quiz.status === 'WAITING' ? '🟢 Ready' : quiz.status === 'ACTIVE' ? '🟡 Active' : '⚫ Finished'}
                  </span>
                </div>
              </div>
            ))}
            {error && <p style={{ color: 'var(--accent-red)', marginBottom: 16 }}>{error}</p>}
            <button className="btn btn-secondary btn-block mt-2" onClick={() => { setMode(null); setError(''); }}>
              ← Back
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
