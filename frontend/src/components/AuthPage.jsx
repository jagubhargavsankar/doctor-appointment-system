import { useState } from 'react';
import { api, jsonBody } from '../api.js';

export default function AuthPage({ onLogin, onBack }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const update = (event) => setForm((old) => ({ ...old, [event.target.name]: event.target.value }));

  async function submit(event) {
    event.preventDefault();
    setBusy(true); setError(''); setNotice('');
    try {
      if (mode === 'register') {
        const result = await api('/api/auth/register', {
          method: 'POST', body: jsonBody({ name: form.name, email: form.email, phone: form.phone, password: form.password })
        });
        setNotice(result?.message || 'Account created. Please sign in.');
        setMode('login');
      } else {
        const result = await api('/api/auth/login', {
          method: 'POST', body: jsonBody({ email: form.email, password: form.password })
        });
        onLogin(result);
      }
    } catch (err) {
      setError(err.message || 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-story">
        <button className="back-link light-link" onClick={onBack}>← Back to doctors</button>
        <div className="story-copy">
          <span className="eyebrow eyebrow-light">CARE THAT FITS YOUR LIFE</span>
          <h1>Your health journey, made a little easier.</h1>
          <p>Find a specialist, choose a time that works for you, and keep every visit in one place.</p>
          <div className="story-points">
            <span><i>✓</i> Simple appointment booking</span>
            <span><i>✓</i> Secure, role-based access</span>
            <span><i>✓</i> Your visits in one dashboard</span>
          </div>
        </div>
        <div className="story-footnote">CarePoint · Doctor Appointment System</div>
      </section>
      <section className="auth-form-wrap">
        <div className="auth-card">
          <div className="auth-brand"><span className="brand-mark">✚</span><span>CarePoint</span></div>
          <span className="eyebrow">WELCOME {mode === 'login' ? 'BACK' : 'ABOARD'}</span>
          <h2>{mode === 'login' ? 'Sign in to your account' : 'Create your patient account'}</h2>
          <p className="muted">{mode === 'login' ? 'Enter your details to continue.' : 'Register once to book and manage your appointments.'}</p>
          <div className="auth-switch" role="tablist" aria-label="Authentication options">
            <button className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError(''); setNotice(''); }}>Sign in</button>
            <button className={mode === 'register' ? 'active' : ''} onClick={() => { setMode('register'); setError(''); setNotice(''); }}>Create account</button>
          </div>
          {error && <div className="alert alert-error" role="alert">{error}</div>}
          {notice && <div className="alert alert-success" role="status">{notice}</div>}
          <form className="form-stack" onSubmit={submit}>
            {mode === 'register' && <label>Full name<input name="name" value={form.name} onChange={update} placeholder="Your full name" autoComplete="name" required maxLength={120} /></label>}
            <label>Email address<input name="email" type="email" value={form.email} onChange={update} placeholder="you@example.com" autoComplete="email" required /></label>
            {mode === 'register' && <label>Phone number<input name="phone" value={form.phone} onChange={update} placeholder="+91 98765 43210" autoComplete="tel" required minLength={7} maxLength={25} /></label>}
            <label>Password<input name="password" type="password" value={form.password} onChange={update} placeholder={mode === 'register' ? 'At least 8 characters' : 'Enter your password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 8 : 1} /></label>
            <button className="btn btn-primary btn-wide" type="submit" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create patient account'} <span>→</span></button>
          </form>
          <p className="auth-note">Patient registration is open. Doctor and admin accounts are created by an administrator.</p>
        </div>
      </section>
    </main>
  );
}
