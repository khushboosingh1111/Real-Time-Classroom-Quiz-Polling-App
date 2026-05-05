import { useState } from 'react';
import './index.css';
import LandingPage from './components/LandingPage';
import HostDashboard from './components/HostDashboard';
import PlayerGame from './components/PlayerGame';

function App() {
  const [view, setView] = useState('landing'); // landing | host | player
  const [sessionId, setSessionId] = useState('');
  const [playerName, setPlayerName] = useState('');
  const [quizInfo, setQuizInfo] = useState(null);

  const handleJoinAsPlayer = (session, name, info) => {
    setSessionId(session);
    setPlayerName(name);
    setQuizInfo(info);
    setView('player');
  };

  const handleHostQuiz = (session, info) => {
    setSessionId(session);
    setQuizInfo(info);
    setView('host');
  };

  const handleBack = () => {
    setView('landing');
    setSessionId('');
    setPlayerName('');
    setQuizInfo(null);
  };

  return (
    <>
      {view === 'landing' && (
        <LandingPage onJoin={handleJoinAsPlayer} onHost={handleHostQuiz} />
      )}
      {view === 'host' && (
        <HostDashboard sessionId={sessionId} quizInfo={quizInfo} onBack={handleBack} />
      )}
      {view === 'player' && (
        <PlayerGame sessionId={sessionId} playerName={playerName} quizInfo={quizInfo} onBack={handleBack} />
      )}
    </>
  );
}

export default App;
