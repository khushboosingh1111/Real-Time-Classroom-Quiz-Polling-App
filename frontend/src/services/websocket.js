import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';

const SOCKET_URL = 'http://localhost:8081/ws-quiz';

class WebSocketService {
  constructor() {
    this.client = null;
    this.subscriptions = {};
    this.connected = false;
    this.sessionId = null;
    this.playerName = null;
    this.onConnectCallbacks = [];
  }

  connect(sessionId, playerName, onConnect) {
    this.sessionId = sessionId;
    this.playerName = playerName;

    this.client = new Client({
      webSocketFactory: () => new SockJS(SOCKET_URL),
      reconnectDelay: 2000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.connected = true;
        console.log('✅ WebSocket connected');
        if (onConnect) onConnect();
        this.onConnectCallbacks.forEach(cb => cb());
      },
      onDisconnect: () => {
        this.connected = false;
        console.log('❌ WebSocket disconnected');
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      }
    });

    this.client.activate();
  }

  subscribe(topic, callback) {
    if (!this.client || !this.connected) {
      this.onConnectCallbacks.push(() => this._doSubscribe(topic, callback));
      return;
    }
    this._doSubscribe(topic, callback);
  }

  _doSubscribe(topic, callback) {
    if (this.subscriptions[topic]) {
      this.subscriptions[topic].unsubscribe();
    }
    console.log(`📡 Subscribing to: ${topic}`);
    this.subscriptions[topic] = this.client.subscribe(topic, (message) => {
      console.log(`📥 Received from ${topic}:`, message.body);
      const data = JSON.parse(message.body);
      callback(data);
    });
  }

  send(destination, body) {
    if (this.client && this.connected) {
      console.log(`📤 Sending to ${destination}:`, body);
      this.client.publish({
        destination,
        body: JSON.stringify(body)
      });
    } else {
      console.warn(`⚠️ Cannot send to ${destination}, socket not connected!`);
    }
  }

  joinQuiz(sessionId, playerName, avatarUrl) {
    this.send(`/app/quiz/${sessionId}/join`, {
      playerName,
      joinCode: sessionId,
      avatarUrl: avatarUrl || ''
    });
  }

  submitAnswer(sessionId, playerName, questionIndex, selectedOption) {
    this.send(`/app/quiz/${sessionId}/answer`, {
      playerName,
      sessionId,
      questionIndex,
      selectedOption,
      clientTimestamp: Date.now()
    });
  }

  startGame(sessionId) {
    this.send(`/app/quiz/${sessionId}/start`, {});
  }

  nextQuestion(sessionId) {
    this.send(`/app/quiz/${sessionId}/next`, {});
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
    }
    this.subscriptions = {};
    this.connected = false;
  }
}

const wsService = new WebSocketService();
export default wsService;
