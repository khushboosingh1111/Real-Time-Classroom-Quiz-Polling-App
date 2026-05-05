import { useState, useEffect, useRef } from 'react';
import wsService from '../services/websocket';

export default function HostDashboard({ sessionId, quizInfo, onBack }) {
  const [phase, setPhase] = useState('WAITING');
  const [players, setPlayers] = useState([]);
  const [leaderboard, setLeaderboard] = useState([]);
  const [currentQ, setCurrentQ] = useState(-1);
  const [totalQ, setTotalQ] = useState(quizInfo?.totalQuestions || 0);
  const [timer, setTimer] = useState(0);
  const [stats, setStats] = useState(null);
  const [playerCount, setPlayerCount] = useState(0);
  const connected = useRef(false);

  useEffect(() => {
    let isMounted = true;

    wsService.connect(sessionId, '__HOST__', () => {
      if (!isMounted) return;
      wsService.subscribe(`/topic/quiz/${sessionId}/players`, (data) => {
        setPlayers(data.allPlayers || []);
        setPlayerCount(data.playerCount);
      });
      wsService.subscribe(`/topic/quiz/${sessionId}/state`, (data) => {
        setPhase(data.phase);
        setCurrentQ(data.currentQuestion);
        setTotalQ(data.totalQuestions);
        setPlayerCount(data.playerCount);
      });
      wsService.subscribe(`/topic/quiz/${sessionId}/leaderboard`, (data) => {
        setLeaderboard(data.entries || []);
      });
      wsService.subscribe(`/topic/quiz/${sessionId}/timer`, (data) => {
        setTimer(data.remaining);
      });
      wsService.subscribe(`/topic/quiz/${sessionId}/stats`, (data) => {
        setStats(data);
      });
    });

    return () => {
      isMounted = false;
      wsService.disconnect();
    };
  }, [sessionId]);

  const startGame = () => wsService.startGame(sessionId);
  const nextQuestion = () => wsService.nextQuestion(sessionId);

  const renderStatsBar = () => {
    if (!stats || !stats.optionCounts) return null;
    const total = stats.totalAnswered || 1;
    const opts = ['A', 'B', 'C', 'D'];
    return (
      <div style={{ marginTop: 16 }}>
        <div className="stats-bar">
          {opts.map(opt => {
            const count = stats.optionCounts[opt] || 0;
            const pct = (count / total) * 100;
            return (
              <div
                key={opt}
                className="stats-bar-segment"
                style={{
                  width: `${pct}%`,
                  opacity: opt === stats.correctOption ? 1 : 0.5
                }}
              >
                {pct > 10 ? `${opt}: ${count}` : ''}
              </div>
            );
          })}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
          <span>{stats.totalAnswered}/{stats.totalPlayers} answered</span>
          <span>Correct: {stats.correctOption}</span>
          <span>Avg: {Math.round(stats.avgResponseTimeMs)}ms</span>
        </div>
      </div>
    );
  };

  return (
    <div style={{ minHeight: '100vh' }}>
      {/* Header */}
      <div className="quiz-header">
        <div className="quiz-info">
          <button className="btn btn-secondary" onClick={onBack} style={{ padding: '8px 16px', fontSize: '0.85rem' }}>← Exit</button>
          <h2 style={{ fontFamily: 'Outfit', fontWeight: 700, fontSize: '1.2rem' }}>
            {quizInfo?.quizTitle || 'Quiz'}
          </h2>
        </div>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <span className="player-count">👥 {playerCount} players</span>
          <span className="category-badge">{sessionId}</span>
        </div>
      </div>

      <div className="container" style={{ paddingTop: 30 }}>
        {/* WAITING PHASE */}
        {phase === 'WAITING' && (
          <div className="text-center">
            <h1 className="title-lg mb-2">Waiting for Players...</h1>
            <p className="subtitle mb-3">Share code: <strong style={{ color: 'var(--accent-purple)', fontFamily: 'Outfit', fontSize: '2rem', letterSpacing: 6 }}>{sessionId}</strong></p>

            <div className="player-avatars mb-4">
              {players.map((p, i) => (
                <div key={i} className="player-chip">👤 {p}</div>
              ))}
              {players.length === 0 && <p className="subtitle pulse">Waiting for players to join...</p>}
            </div>

            {players.length > 0 && (
              <button className="btn btn-primary btn-lg" onClick={startGame}>
                🚀 Start Game ({players.length} players)
              </button>
            )}
          </div>
        )}

        {/* QUESTION / ANSWER_REVEAL / LEADERBOARD */}
        {(phase === 'QUESTION' || phase === 'ANSWER_REVEAL' || phase === 'LEADERBOARD') && (
          <div>
            {/* Progress */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
              <span className="label">Q {currentQ + 1}/{totalQ}</span>
              <div className="progress-bar" style={{ flex: 1 }}>
                <div className="progress-fill" style={{ width: `${((currentQ + 1) / totalQ) * 100}%` }}></div>
              </div>
              {phase === 'QUESTION' && (
                <div style={{
                  padding: '8px 20px', borderRadius: 50,
                  background: timer <= 5 ? 'rgba(239,68,68,0.2)' : 'rgba(139,92,246,0.12)',
                  border: `1px solid ${timer <= 5 ? 'rgba(239,68,68,0.4)' : 'rgba(139,92,246,0.25)'}`,
                  fontFamily: 'Outfit', fontWeight: 700, fontSize: '1.3rem',
                  color: timer <= 5 ? 'var(--accent-red)' : 'var(--accent-purple)',
                  minWidth: 60, textAlign: 'center'
                }}>
                  {timer}s
                </div>
              )}
            </div>

            {/* Stats after reveal */}
            {(phase === 'ANSWER_REVEAL') && stats && renderStatsBar()}

            {/* Leaderboard */}
            {(phase === 'LEADERBOARD' || phase === 'ANSWER_REVEAL') && (
              <div style={{ marginTop: 24 }}>
                <h2 className="title-md mb-3 text-center">🏆 Leaderboard</h2>
                <div className="leaderboard-list">
                  {leaderboard.slice(0, 10).map((entry, i) => (
                    <div key={entry.playerName} className={`leaderboard-item ${i < 3 ? `top-${i + 1}` : ''}`}>
                      <div className={`rank-badge ${i < 3 ? `rank-${i + 1}` : 'rank-other'}`}>
                        {i === 0 ? '👑' : i + 1}
                      </div>
                      <div className="player-info">
                        <div className="player-name">{entry.playerName}</div>
                        <div className="player-stats">
                          {entry.correctAnswers} correct
                          {entry.streak > 1 && <span className="streak-badge" style={{ marginLeft: 8 }}>🔥 {entry.streak}</span>}
                        </div>
                      </div>
                      <div className="player-score">{entry.score.toLocaleString()}</div>
                    </div>
                  ))}
                </div>

                {phase === 'LEADERBOARD' && currentQ + 1 < totalQ && (
                  <div className="text-center mt-3">
                    <button className="btn btn-primary btn-lg" onClick={nextQuestion}>
                      Next Question →
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* FINISHED */}
        {phase === 'FINISHED' && (
          <div className="text-center">
            <div style={{ fontSize: '4rem', marginBottom: 16 }}>🏆</div>
            <h1 className="title-xl mb-2">Game Over!</h1>
            <p className="subtitle mb-4">Final Results</p>
            <div className="leaderboard-list">
              {leaderboard.map((entry, i) => (
                <div key={entry.playerName} className={`leaderboard-item ${i < 3 ? `top-${i + 1}` : ''}`}>
                  <div className={`rank-badge ${i < 3 ? `rank-${i + 1}` : 'rank-other'}`}>
                    {i === 0 ? '👑' : i === 1 ? '🥈' : i === 2 ? '🥉' : i + 1}
                  </div>
                  <div className="player-info">
                    <div className="player-name">{entry.playerName}</div>
                    <div className="player-stats">{entry.correctAnswers}/{totalQ} correct</div>
                  </div>
                  <div className="player-score">{entry.score.toLocaleString()}</div>
                </div>
              ))}
            </div>
            <button className="btn btn-secondary btn-lg mt-3" onClick={onBack}>Back to Home</button>
          </div>
        )}
      </div>
    </div>
  );
}
