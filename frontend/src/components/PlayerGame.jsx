import { useState, useEffect, useRef } from 'react';
import Confetti from 'react-confetti';
import wsService from '../services/websocket';

export default function PlayerGame({ sessionId, playerName, quizInfo, onBack }) {
  const [phase, setPhase] = useState('WAITING');
  const [question, setQuestion] = useState(null);
  const [selected, setSelected] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [leaderboard, setLeaderboard] = useState([]);
  const [timer, setTimer] = useState(0);
  const [totalTime, setTotalTime] = useState(15);
  const [stats, setStats] = useState(null);
  const [players, setPlayers] = useState([]);
  const [playerCount, setPlayerCount] = useState(0);
  const [myScore, setMyScore] = useState(0);
  const [myRank, setMyRank] = useState(0);
  const [streak, setStreak] = useState(0);
  const [totalQ, setTotalQ] = useState(0);
  const [currentQ, setCurrentQ] = useState(-1);
  const [showConfetti, setShowConfetti] = useState(false);
  const connected = useRef(false);

  useEffect(() => {
    let isMounted = true;

    wsService.connect(sessionId, playerName, () => {
      if (!isMounted) return;

      // Subscribe to all topics FIRST
      wsService.subscribe(`/topic/quiz/${sessionId}/question`, (data) => {
        setQuestion(data);
        setSelected(null);
        setFeedback(null);
        setStats(null);
        setPhase('QUESTION');
      });

      wsService.subscribe(`/topic/quiz/${sessionId}/state`, (data) => {
        setPhase(data.phase);
        setCurrentQ(data.currentQuestion);
        setTotalQ(data.totalQuestions);
        setPlayerCount(data.playerCount);
      });

      wsService.subscribe(`/topic/quiz/${sessionId}/timer`, (data) => {
        setTimer(data.remaining);
        setTotalTime(data.total);
      });

      wsService.subscribe(`/topic/quiz/${sessionId}/leaderboard`, (data) => {
        setLeaderboard(data.entries || []);
        const me = (data.entries || []).find(e => e.playerName === playerName);
        if (me) {
          setMyScore(me.score);
          setMyRank(me.rank);
        }
      });

      wsService.subscribe(`/topic/quiz/${sessionId}/stats`, (data) => {
        setStats(data);
      });

      wsService.subscribe(`/topic/quiz/${sessionId}/answer/${playerName}`, (data) => {
        setFeedback(data);
        if (data.correct) {
          setMyScore(data.totalScore);
          setStreak(data.streak);
          setShowConfetti(true);
          setTimeout(() => setShowConfetti(false), 2000);
        } else {
          setStreak(0);
        }
        setMyRank(data.currentRank);
      });

      wsService.subscribe(`/topic/quiz/${sessionId}/players`, (data) => {
        setPlayers(data.allPlayers || []);
        setPlayerCount(data.playerCount);
      });

      // Join the quiz AFTER subscribing
      wsService.joinQuiz(sessionId, playerName, '');
    });

    return () => {
      isMounted = false;
      wsService.disconnect();
    };
  }, [sessionId, playerName]);

  const handleAnswer = (option) => {
    if (selected || !question) return;
    setSelected(option);
    wsService.submitAnswer(sessionId, playerName, question.questionIndex, option);
  };

  const timerPct = totalTime > 0 ? (timer / totalTime) * 100 : 0;
  const circumference = 2 * Math.PI * 42;
  const strokeDashoffset = circumference - (timerPct / 100) * circumference;

  const getOptionClass = (opt) => {
    if (!stats && !feedback) {
      if (selected === opt) return 'option-btn selected';
      if (selected) return 'option-btn disabled';
      return 'option-btn';
    }
    if (stats) {
      if (opt === stats.correctOption) return 'option-btn correct';
      if (selected === opt && opt !== stats.correctOption) return 'option-btn wrong';
      return 'option-btn disabled';
    }
    return 'option-btn disabled';
  };

  return (
    <div style={{ minHeight: '100vh' }}>
      {showConfetti && (
        <div className="confetti-wrapper">
          <Confetti width={window.innerWidth} height={window.innerHeight} recycle={false} numberOfPieces={150} />
        </div>
      )}

      {/* Header */}
      <div className="quiz-header">
        <div className="quiz-info">
          <h2 style={{ fontFamily: 'Outfit', fontWeight: 700, fontSize: '1rem' }}>
            {quizInfo?.title || 'Quiz'}
          </h2>
        </div>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <div style={{
            padding: '6px 14px', borderRadius: 50,
            background: 'var(--gradient-primary)',
            fontWeight: 700, fontSize: '0.9rem'
          }}>
            ⭐ {myScore.toLocaleString()}
          </div>
          {streak > 1 && <span className="streak-badge">🔥 {streak}</span>}
          <span className="player-count">#{myRank}</span>
        </div>
      </div>

      <div className="container" style={{ paddingTop: 20, maxWidth: 700 }}>

        {/* WAITING */}
        {phase === 'WAITING' && (
          <div className="text-center" style={{ paddingTop: 60 }}>
            <div style={{ fontSize: '3rem', marginBottom: 16 }}>🎮</div>
            <h1 className="title-lg mb-2">You're In!</h1>
            <p className="subtitle mb-1">Welcome, <strong style={{ color: 'var(--accent-purple)' }}>{playerName}</strong></p>
            <p className="subtitle mb-4">Waiting for the host to start the game...</p>

            <div className="glass-card" style={{ padding: 24, marginBottom: 24 }}>
              <p className="label mb-2">Players Joined</p>
              <div className="player-avatars">
                {players.map((p, i) => (
                  <div key={i} className="player-chip" style={{
                    borderColor: p === playerName ? 'var(--accent-purple)' : 'var(--border-glass)',
                    background: p === playerName ? 'rgba(139,92,246,0.12)' : 'var(--bg-glass)'
                  }}>
                    {p === playerName ? '⭐' : '👤'} {p}
                  </div>
                ))}
              </div>
            </div>

            <div className="pulse" style={{ color: 'var(--text-secondary)' }}>
              ⏳ Waiting for host...
            </div>
          </div>
        )}

        {/* QUESTION */}
        {phase === 'QUESTION' && question && (
          <div>
            {/* Progress + Timer */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
              <span className="label">Q{question.questionIndex + 1}/{question.totalQuestions}</span>
              <div className="progress-bar" style={{ flex: 1 }}>
                <div className="progress-fill" style={{ width: `${((question.questionIndex + 1) / question.totalQuestions) * 100}%` }}></div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {question.category && <span className="category-badge">{question.category}</span>}
                {question.difficulty && <span className={`difficulty-badge difficulty-${question.difficulty}`}>{question.difficulty}</span>}
              </div>
            </div>

            {/* Timer Ring */}
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
              <div className="timer-ring">
                <svg width="100" height="100" viewBox="0 0 100 100">
                  <circle cx="50" cy="50" r="42" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="6" />
                  <circle
                    cx="50" cy="50" r="42" fill="none"
                    stroke={timer <= 5 ? '#ef4444' : timer <= 10 ? '#f59e0b' : '#8b5cf6'}
                    strokeWidth="6" strokeLinecap="round"
                    strokeDasharray={circumference}
                    strokeDashoffset={strokeDashoffset}
                    style={{ transition: 'stroke-dashoffset 1s linear, stroke 0.3s' }}
                  />
                </svg>
                <div className={`timer-text ${timer <= 5 ? 'timer-urgent' : ''}`}>{timer}</div>
              </div>
            </div>

            {/* Question Text */}
            <div className="glass-card" style={{ padding: 28, marginBottom: 24, textAlign: 'center' }}>
              <h2 style={{ fontFamily: 'Outfit', fontWeight: 700, fontSize: '1.4rem', lineHeight: 1.4 }}>
                {question.questionText}
              </h2>
            </div>

            {/* Options */}
            <div className="option-grid">
              {['A', 'B', 'C', 'D'].map(opt => (
                <button
                  key={opt}
                  className={getOptionClass(opt)}
                  onClick={() => handleAnswer(opt)}
                >
                  <span className="option-label">{opt}</span>
                  <span>{question[`option${opt}`]}</span>
                </button>
              ))}
            </div>

            {/* Feedback */}
            {feedback && (
              <div className={feedback.correct ? 'feedback-correct' : 'feedback-wrong'} style={{ marginTop: 20 }}>
                <div style={{ fontSize: '2rem', marginBottom: 8 }}>{feedback.correct ? '✅' : '❌'}</div>
                <div style={{ fontWeight: 700, fontSize: '1.2rem' }}>
                  {feedback.correct ? `+${feedback.pointsEarned} points!` : 'Wrong answer!'}
                </div>
                <div style={{ fontSize: '0.9rem', opacity: 0.9, marginTop: 4 }}>
                  {feedback.responseTimeMs}ms • Rank #{feedback.currentRank}
                  {feedback.streak > 1 && ` • 🔥 ${feedback.streak} streak!`}
                </div>
              </div>
            )}
          </div>
        )}

        {/* ANSWER REVEAL */}
        {phase === 'ANSWER_REVEAL' && question && (
          <div>
            <div className="text-center mb-3">
              <h2 className="title-md">Answer Revealed!</h2>
            </div>

            <div className="glass-card" style={{ padding: 24, marginBottom: 20, textAlign: 'center' }}>
              <p style={{ fontSize: '1.1rem' }}>{question.questionText}</p>
            </div>

            <div className="option-grid mb-3">
              {['A', 'B', 'C', 'D'].map(opt => (
                <div key={opt} className={getOptionClass(opt)}>
                  <span className="option-label">{opt}</span>
                  <span>{question[`option${opt}`]}</span>
                </div>
              ))}
            </div>

            {stats && (
              <div className="glass-card" style={{ padding: 20 }}>
                <p className="label mb-2">Answer Distribution</p>
                <div className="stats-bar">
                  {['A', 'B', 'C', 'D'].map(opt => {
                    const count = stats.optionCounts?.[opt] || 0;
                    const pct = stats.totalAnswered > 0 ? (count / stats.totalAnswered) * 100 : 0;
                    return (
                      <div key={opt} className="stats-bar-segment"
                        style={{ width: `${pct}%`, opacity: opt === stats.correctOption ? 1 : 0.5 }}>
                        {pct > 10 ? `${opt}: ${count}` : ''}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}

        {/* LEADERBOARD */}
        {phase === 'LEADERBOARD' && (
          <div>
            <h2 className="title-md text-center mb-3">🏆 Leaderboard</h2>
            <div className="leaderboard-list">
              {leaderboard.slice(0, 10).map((entry, i) => (
                <div key={entry.playerName}
                  className={`leaderboard-item ${i < 3 ? `top-${i + 1}` : ''}`}
                  style={entry.playerName === playerName ? { border: '1px solid var(--accent-purple)' } : {}}
                >
                  <div className={`rank-badge ${i < 3 ? `rank-${i + 1}` : 'rank-other'}`}>
                    {i === 0 ? '👑' : i + 1}
                  </div>
                  <div className="player-info">
                    <div className="player-name">
                      {entry.playerName} {entry.playerName === playerName && '(You)'}
                    </div>
                    <div className="player-stats">
                      {entry.correctAnswers} correct
                      {entry.streak > 1 && <span className="streak-badge" style={{ marginLeft: 8 }}>🔥 {entry.streak}</span>}
                    </div>
                  </div>
                  <div className="player-score">{entry.score.toLocaleString()}</div>
                </div>
              ))}
            </div>
            <div className="text-center mt-3 pulse" style={{ color: 'var(--text-secondary)' }}>
              ⏳ Next question coming...
            </div>
          </div>
        )}

        {/* FINISHED */}
        {phase === 'FINISHED' && (
          <div className="text-center">
            {myRank <= 3 && (
              <div className="confetti-wrapper">
                <Confetti width={window.innerWidth} height={window.innerHeight} recycle={false} numberOfPieces={300} />
              </div>
            )}
            <div style={{ fontSize: '4rem', marginBottom: 16 }}>
              {myRank === 1 ? '🏆' : myRank === 2 ? '🥈' : myRank === 3 ? '🥉' : '🎮'}
            </div>
            <h1 className="title-xl mb-2">
              {myRank === 1 ? 'You Won!' : myRank <= 3 ? `#${myRank} - Amazing!` : 'Game Over!'}
            </h1>
            <p className="subtitle mb-1">
              Final Score: <strong style={{ color: 'var(--accent-purple)' }}>{myScore.toLocaleString()}</strong>
            </p>
            <p className="subtitle mb-4">Rank #{myRank} of {playerCount}</p>

            <div className="leaderboard-list" style={{ marginBottom: 24 }}>
              {leaderboard.map((entry, i) => (
                <div key={entry.playerName}
                  className={`leaderboard-item ${i < 3 ? `top-${i + 1}` : ''}`}
                  style={entry.playerName === playerName ? { border: '1px solid var(--accent-purple)' } : {}}
                >
                  <div className={`rank-badge ${i < 3 ? `rank-${i + 1}` : 'rank-other'}`}>
                    {i === 0 ? '👑' : i === 1 ? '🥈' : i === 2 ? '🥉' : i + 1}
                  </div>
                  <div className="player-info">
                    <div className="player-name">
                      {entry.playerName} {entry.playerName === playerName && '⭐'}
                    </div>
                    <div className="player-stats">{entry.correctAnswers} correct</div>
                  </div>
                  <div className="player-score">{entry.score.toLocaleString()}</div>
                </div>
              ))}
            </div>

            <button className="btn btn-primary btn-lg" onClick={onBack}>🏠 Back to Home</button>
          </div>
        )}
      </div>
    </div>
  );
}
