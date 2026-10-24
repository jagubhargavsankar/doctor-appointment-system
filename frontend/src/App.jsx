import { useState } from 'react';
import AuthPage from './components/AuthPage.jsx';
import DoctorExplorer from './components/DoctorExplorer.jsx';
import AppointmentsPage from './components/AppointmentsPage.jsx';
import DoctorSchedulePage from './components/DoctorSchedulePage.jsx';
import AssistantPage from './components/AssistantPage.jsx';
import ProfilePage from './components/ProfilePage.jsx';
import { AdminDoctorsPage, AdminPatientsPage } from './components/AdminPages.jsx';

const NAV = {
  PATIENT: [
    { id: 'doctors', label: 'Find doctors', icon: '⌕' },
    { id: 'appointments', label: 'My appointments', icon: '▦' },
    { id: 'assistant', label: 'AI assistant', icon: '✦' },
    { id: 'profile', label: 'My profile', icon: '◉' },
  ],
  DOCTOR: [
    { id: 'appointments', label: 'Appointments', icon: '▦' },
    { id: 'schedule', label: 'My schedule', icon: '◷' },
    { id: 'assistant', label: 'AI assistant', icon: '✦' },
  ],
  ADMIN: [
    { id: 'doctors', label: 'Manage doctors', icon: '✚' },
    { id: 'patients', label: 'Patients', icon: '◉' },
    { id: 'appointments', label: 'Appointments', icon: '▦' },
    { id: 'assistant', label: 'AI assistant', icon: '✦' },
  ],
};

const DEFAULT_PAGE = { PATIENT: 'doctors', DOCTOR: 'appointments', ADMIN: 'doctors' };

function readSession() {
  try { return JSON.parse(sessionStorage.getItem('carepoint.session') || 'null'); }
  catch { sessionStorage.removeItem('carepoint.session'); return null; }
}

function SiteLogo() {
  return <div className="site-logo"><span className="brand-mark">✚</span><span>Care<span>Point</span></span></div>;
}

function Header({ session, page, setPage, onLogin, onLogout, onAuth }) {
  const nav = session ? (NAV[session.role] || []) : [];
  return <header className="topbar"><div className="topbar-inner"><button className="logo-button" onClick={() => { setPage(session ? DEFAULT_PAGE[session.role] : 'doctors'); }} aria-label="CarePoint home"><SiteLogo /></button>
    <nav className="main-nav" aria-label="Main navigation">{session ? nav.map((item) => <button key={item.id} className={`nav-link ${page === item.id ? 'active' : ''}`} onClick={() => setPage(item.id)}><span className="nav-icon">{item.icon}</span>{item.label}</button>) : <button className="nav-link active" onClick={() => setPage('doctors')}>Find a doctor</button>}</nav>
    <div className="topbar-actions">{session ? <><div className="user-chip"><span className="user-chip-avatar">{(session.email || 'U').slice(0, 1).toUpperCase()}</span><span className="user-chip-copy"><strong>{session.email}</strong><small>{session.role}</small></span></div><button className="btn btn-outline logout-button" onClick={onLogout}>Sign out</button></> : <><button className="btn btn-text top-login" onClick={onAuth}>Sign in</button><button className="btn btn-primary" onClick={onAuth}>Get started <span>→</span></button></>}</div>
  </div></header>;
}

function PublicHero({ onAuth }) {
  return <section className="hero-section"><div className="hero-copy"><span className="eyebrow"><span className="eyebrow-dot" /> A BETTER WAY TO MANAGE YOUR HEALTH</span><h1>Good care starts with <em>the right connection.</em></h1><p>Find trusted specialists, discover available appointment times, and keep your healthcare visits organized—all in one simple place.</p><div className="hero-actions"><button className="btn btn-primary btn-large" onClick={onAuth}>Book an appointment <span>→</span></button><a href="#doctors" className="hero-secondary">Explore specialists <span>↓</span></a></div><div className="hero-trust"><div className="trust-avatars"><span>DR</span><span>CP</span><span>+</span></div><div><strong>Care that works around you</strong><small>Convenient · Clear · Connected</small></div></div></div>
    <div className="hero-visual" aria-hidden="true"><div className="hero-blob" /><div className="hero-cross">✚</div><div className="hero-orbit orbit-one" /><div className="hero-orbit orbit-two" /><div className="hero-doctor-illustration"><div className="illustration-hair" /><div className="illustration-head"><div className="illustration-ear ear-left" /><div className="illustration-ear ear-right" /><div className="illustration-face"><span className="eye eye-left" /><span className="eye eye-right" /><span className="nose" /><span className="smile" /></div></div><div className="illustration-neck" /><div className="illustration-coat"><div className="coat-shirt" /><div className="coat-lapel lapel-left" /><div className="coat-lapel lapel-right" /><div className="coat-stethoscope" /></div></div><div className="floating-card floating-appointment"><span className="floating-icon">✓</span><div><strong>Appointment booked</strong><small>Your visit is on the calendar</small></div></div><div className="floating-card floating-care"><span className="care-icon">✚</span><div><strong>Care, made simple</strong><small>Your health, your schedule</small></div></div><div className="hero-spark spark-one">✦</div><div className="hero-spark spark-two">✧</div></div>
  </section>;
}

function Toast({ message, onClose }) {
  if (!message) return null;
  return <div className="toast-message" role="status"><span>✓</span>{message}<button aria-label="Dismiss message" onClick={onClose}>×</button></div>;
}

function Footer() {
  return <footer className="site-footer"><div className="footer-inner"><SiteLogo /><p>Thoughtful healthcare, better organized.</p><span>Doctor Appointment System · React + Spring Boot</span></div></footer>;
}

export default function App() {
  const [session, setSession] = useState(readSession);
  const [page, setPage] = useState(() => {
    const initial = readSession();
    return initial ? (DEFAULT_PAGE[initial.role] || 'doctors') : 'doctors';
  });
  const [authOpen, setAuthOpen] = useState(false);
  const [toast, setToast] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  function handleLogin(result) {
    const normalized = { ...result, role: String(result.role || '').toUpperCase() };
    sessionStorage.setItem('carepoint.session', JSON.stringify(normalized));
    setSession(normalized); setPage(DEFAULT_PAGE[normalized.role] || 'doctors'); setAuthOpen(false);
    showNotice(`Signed in as ${normalized.role.toLowerCase()}.`);
  }
  function handleLogout() {
    sessionStorage.removeItem('carepoint.session');
    setSession(null); setPage('doctors'); setAuthOpen(false); setToast('You have signed out.');
  }
  function showNotice(message) {
    setToast(message); setRefreshKey((old) => old + 1);
    window.setTimeout(() => setToast(''), 4500);
  }

  if (authOpen && !session) return <AuthPage onLogin={handleLogin} onBack={() => setAuthOpen(false)} />;

  return <div className="app-shell">
    <Header session={session} page={page} setPage={setPage} onLogout={handleLogout} onAuth={() => setAuthOpen(true)} />
    {!session ? <><PublicHero onAuth={() => setAuthOpen(true)} /><div id="doctors" className="public-doctors"><DoctorExplorer session={null} onRequireLogin={() => setAuthOpen(true)} onNotice={showNotice} refreshKey={refreshKey} /></div></> : <><section className="dashboard-welcome"><div><span className="eyebrow">{session.role === 'PATIENT' ? 'YOUR PERSONAL CARE SPACE' : session.role === 'DOCTOR' ? 'YOUR CLINIC WORKSPACE' : 'SYSTEM OVERVIEW'}</span><h1>{session.role === 'PATIENT' ? 'Hello, welcome back.' : session.role === 'DOCTOR' ? 'Good to see you.' : 'Welcome to your workspace.'}</h1><p>{session.role === 'PATIENT' ? 'Make time for your health. Your next step starts here.' : session.role === 'DOCTOR' ? 'Keep your availability and patient visits up to date.' : 'Manage care teams, patient records, and appointments.'}</p></div><div className="welcome-decoration"><span>✚</span></div></section>
      <main className="dashboard-main" key={`${session.role}-${page}`}>{session.role === 'PATIENT' && page === 'doctors' && <DoctorExplorer session={session} onRequireLogin={() => setAuthOpen(true)} onNotice={showNotice} refreshKey={refreshKey} />}{page === 'appointments' && <AppointmentsPage role={session.role} onNotice={showNotice} />}{session.role === 'DOCTOR' && page === 'schedule' && <DoctorSchedulePage />}{page === 'assistant' && <AssistantPage role={session.role} />}{session.role === 'PATIENT' && page === 'profile' && <ProfilePage session={session} />}{session.role === 'ADMIN' && page === 'doctors' && <AdminDoctorsPage />}{session.role === 'ADMIN' && page === 'patients' && <AdminPatientsPage />}</main>
    </>}
    <Footer />
    <Toast message={toast} onClose={() => setToast('')} />
  </div>;
}
