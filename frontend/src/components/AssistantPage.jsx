import { useEffect, useRef, useState } from 'react';
import { api, jsonBody } from '../api.js';

const introByRole = {
  PATIENT: 'I can help you find doctors, check real appointment slots, book a visit, review your appointments, and request cancellation or rescheduling. For emergencies, contact local emergency services.',
  DOCTOR: 'I’m your CarePoint Doctor Assistant. Ask about your assigned appointments, today’s schedule, or your availability. I can also guide you through adding or removing eligible slots and updating appointment statuses.',
  ADMIN: 'I’m your CarePoint Admin Assistant. I can summarize appointments, identify bookings needing attention, show status trends, list doctors or patients, and check real slot availability. Patient information should be handled according to your organization’s privacy rules.'
};

const titleByRole = {
  PATIENT: 'Patient appointment assistant',
  DOCTOR: 'Doctor appointment assistant',
  ADMIN: 'Admin appointment assistant'
};

const subtitleByRole = {
  PATIENT: 'Book and manage your appointments using the clinic’s configured assistant.',
  DOCTOR: 'Review your assigned visits and manage your availability using the secured backend.',
  ADMIN: 'Review appointment operations and clinic scheduling data from the backend.'
};

const endpointByRole = {
  PATIENT: '/api/ai/appointment-assistant',
  DOCTOR: '/api/ai/doctor-appointment-assistant',
  ADMIN: '/api/ai/admin-appointment-assistant'
};

const suggestionsByRole = {
  ADMIN: [
    'Summarize today’s appointments',
    'Which appointments need attention?',
    'Show appointment status trends',
    'Show all appointments'
  ],
  DOCTOR: [
    'Show my appointments',
    "Show today's appointments",
    'Show my availability',
    'How do I manage my availability?'
  ],
  PATIENT: [
    'Help me book an appointment',
    'How can I view my appointments?',
    'Cancel an appointment',
    'Reschedule an appointment'
  ]
};

// Some model/tool responses arrive as a quoted Java/JSON-style string, with
// literal "\\n" text. Normalize those so the UI displays a readable response.
function normalizeAssistantText(value) {
  let text = String(value ?? '').trim();

  if (text.startsWith('"') && text.endsWith('"')) {
    try {
      const unwrapped = JSON.parse(text);
      if (typeof unwrapped === 'string') text = unwrapped;
    } catch {
      // Keep the original response if it is not a valid JSON string.
    }
  }

  return text
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, ' ')
    .replace(/\r\n/g, '\n');
}

function renderMessageLines(value) {
  const text = normalizeAssistantText(value);
  return text.split('\n').map((originalLine, lineIndex) => {
    const isBullet = /^\s*(?:[-*•])\s+/.test(originalLine);
    const line = isBullet ? originalLine.replace(/^\s*(?:[-*•])\s+/, '') : originalLine;
    const segments = line.split(/(\*\*.+?\*\*)/g);

    return (
      <p className={isBullet ? 'message-list-line' : undefined} key={lineIndex}>
        {isBullet ? <span aria-hidden="true">• </span> : null}
        {segments.map((segment, segmentIndex) => {
          if (segment.startsWith('**') && segment.endsWith('**') && segment.length > 4) {
            return <strong key={segmentIndex}>{segment.slice(2, -2)}</strong>;
          }
          return <span key={segmentIndex}>{segment}</span>;
        })}
      </p>
    );
  });
}

export default function AssistantPage({ role }) {
  const activeRole = String(role || 'PATIENT').toUpperCase();
  const intro = introByRole[activeRole] || introByRole.PATIENT;
  const [messages, setMessages] = useState([{ from: 'assistant', text: intro }]);
  const [question, setQuestion] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function send(event) {
    event.preventDefault();
    const text = question.trim();
    if (!text || busy) return;

    setMessages((old) => [...old, { from: 'user', text }]);
    setQuestion('');
    setBusy(true);
    setError('');

    const endpoint = endpointByRole[activeRole] || '/api/ai/chat';
    try {
      const result = await api(endpoint, {
        method: 'POST',
        body: jsonBody({ question: text })
      });
      setMessages((old) => [...old, {
        from: 'assistant',
        text: normalizeAssistantText(result?.answer) || 'The assistant returned an empty response.'
      }]);
    } catch (err) {
      setError(err.message || 'The assistant request failed. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  function clearChat() {
    setMessages([{ from: 'assistant', text: intro }]);
    setQuestion('');
    setError('');
  }

  const suggestions = suggestionsByRole[activeRole] || suggestionsByRole.PATIENT;

  return (
    <section className="content-section assistant-section">
      <div className="section-heading-row">
        <div>
          <span className="eyebrow">CAREPOINT AI</span>
          <h2>{titleByRole[activeRole] || titleByRole.PATIENT}</h2>
          <p className="muted">{subtitleByRole[activeRole] || subtitleByRole.PATIENT}</p>
        </div>
        <div className="ai-badge"><span className="pulse-dot" /> AI assistant</div>
      </div>

      <div className="assistant-layout">
        <aside className="assistant-side panel-card">
          <div className="assistant-orb">✦</div>
          <h3>How can I help?</h3>
          <p className="muted">Choose a suggested request or ask in your own words.</p>
          <div className="suggestion-list">
            {suggestions.map((item) => (
              <button key={item} type="button" onClick={() => setQuestion(item)} disabled={busy}>
                {item}<span>↗</span>
              </button>
            ))}
          </div>
          <div className="privacy-note">
            <span>ⓘ</span>
            <p>AI responses may be incomplete or inaccurate. Do not use this assistant as a substitute for emergency or professional medical care.</p>
          </div>
        </aside>

        <div className="chat-panel panel-card">
          <div className="chat-header">
            <div className="chat-avatar">✦</div>
            <div>
              <strong>CarePoint Assistant</strong>
              <span><i /> {busy ? 'Thinking…' : 'Ready to help'}</span>
            </div>
            <button className="btn btn-text" type="button" onClick={clearChat} disabled={busy}>Clear chat</button>
          </div>

          <div className="chat-messages" aria-live="polite" aria-busy={busy}>
            {messages.map((message, index) => (
              <div className={`chat-message ${message.from}`} key={`${index}-${message.from}`}>
                <div className="message-avatar">{message.from === 'assistant' ? '✦' : 'You'}</div>
                <div className="message-bubble">{renderMessageLines(message.text)}</div>
              </div>
            ))}
            {busy && (
              <div className="chat-message assistant" aria-label="Assistant is thinking">
                <div className="message-avatar">✦</div>
                <div className="message-bubble typing-dots"><span /><span /><span /></div>
              </div>
            )}
            <div ref={endRef} />
          </div>

          {error && <div className="alert alert-error chat-error" role="alert">{error}</div>}

          <form className="chat-composer" onSubmit={send}>
            <textarea
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Write your question…"
              rows={2}
              maxLength={2000}
              disabled={busy}
            />
            <button className="send-button" type="submit" aria-label="Send message" disabled={busy || !question.trim()}>↑</button>
            <span className="composer-hint">Send your question · Avoid sharing unnecessary sensitive personal data</span>
          </form>
        </div>
      </div>
    </section>
  );
}
